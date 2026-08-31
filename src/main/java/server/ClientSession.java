package server;

import common.NetworkConstants;
import common.SerializationUtils;
import common.dto.TextMessage;
import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * ClientSession Represents a single connected client on the server. Each client gets one of these.
 *
 * <p>It handles their specific SocketChannel, manages read/write buffers for non- blocking I/O with
 * length-prefix framing, and holds basic player info like ID and display name.
 */
public class ClientSession {
  private final SocketChannel channel; // The actual network connection to the
  // client.
  private final String playerId; // Unique ID for this connection, server-generated.
  private String displayId; // Name shown to other players, can be changed.
  private String avatarId; // Chosen preset avatar shown to other players; null until announced.
  private GameSession associatedGameSession; // Which game are they in? Null if
  // none.

  // NIO Buffers - these are key for non-blocking reads.
  // Allocated per frame once the length prefix is known (capped by WireLimits), because a fixed
  // buffer capped the real frame size at its capacity: limit(n) with n > capacity throws.
  private ByteBuffer readBuffer; // Buffer for the current incoming object's bytes.
  private final ByteBuffer lengthBuffer; // Small buffer just for the 4-byte int length
  // prefix.
  private boolean readingLength; // My state machine for reading: am I getting
  // length or data?
  private int expectedObjectLength; // Once length is read, this stores how many object
  // bytes to expect.

  // Outgoing messages are queued. OP_WRITE will drain this.
  private final Queue<Serializable> writeQueue;

  // Per-connection flood protection (security-pass issue 03); consulted for every decoded
  // inbound message before routing.
  private final CommandRateLimiter rateLimiter;

  // Idle / slow-loris timeouts (SECURITY_PLAN B/P1-1). lastActivityMillis advances on every
  // successful read; messageStartedMillis marks when the current (still-incomplete) inbound message
  // began, and resets to NO_MESSAGE_IN_PROGRESS when a full message is decoded. The clock is a seam
  // so tests can drive the timeouts deterministically.
  private final LongSupplier clock;
  private volatile long lastActivityMillis;
  private volatile long messageStartedMillis = ConnectionTimeouts.NO_MESSAGE_IN_PROGRESS;

  // Per-connection garbage-frame throttle (SECURITY_PLAN B/P2-3): decode failures aren't covered by
  // the command rate limiter (which only sees decoded messages), so a flood of undecodable frames
  // is bounded here and drops only this connection.
  private final DecodeFailureLimiter decodeFailureLimiter;

  private final GameServer server; // Need this to call back to server (e.g., registerForWrite,

  // processMessage).

  /**
   * Constructor for a new client session.
   *
   * @param channel The connected SocketChannel for this client.
   * @param server A reference to the main GameServer.
   */
  public ClientSession(SocketChannel channel, GameServer server) {
    this(channel, server, new CommandRateLimiter(), System::currentTimeMillis);
  }

  /** Test seam: drive the rate limiter directly (production clock). */
  ClientSession(SocketChannel channel, GameServer server, CommandRateLimiter rateLimiter) {
    this(channel, server, rateLimiter, System::currentTimeMillis);
  }

  /** Test seam: inject both the rate limiter and a fake clock to drive the idle/frame timeouts. */
  ClientSession(
      SocketChannel channel,
      GameServer server,
      CommandRateLimiter rateLimiter,
      LongSupplier clock) {
    this.channel = channel;
    this.server = server;
    this.rateLimiter = rateLimiter;
    this.clock = clock;
    this.decodeFailureLimiter = new DecodeFailureLimiter(clock);
    this.lastActivityMillis = clock.getAsLong();
    this.playerId = UUID.randomUUID().toString(); // Every connection gets a unique
    // internal ID.

    // Default display name, client can change it later with /setname.
    this.displayId = "Player-" + playerId.substring(0, 4);

    this.readBuffer = null; // Allocated per frame once the length prefix arrives.
    this.lengthBuffer = ByteBuffer.allocate(4); // Just for the int.
    this.readingLength = true; // Start by expecting a length prefix.
    this.expectedObjectLength = -1; // No object expected yet.
    this.writeQueue = new LinkedList<>(); // For DTOs to send.
  }

  // --- Getters and Setters ---

  public String getPlayerId() {
    return playerId;
  }

  public String getDisplayId() {
    return displayId;
  }

  /**
   * Updates the display name for this client session. Called by server logic when processing an
   * UpdateDisplayNameCommand.
   */
  public void setDisplayId(String newDisplayId) {
    if (newDisplayId != null && !newDisplayId.trim().isEmpty()) {
      this.displayId = newDisplayId.trim();
      // server.log() could be called here if I want to see name changes server-wide.
    }
  }

  /** The chosen avatar preset id shown to other players, or {@code null} until announced. */
  public String getAvatarId() {
    return avatarId;
  }

  /**
   * Records this client's chosen avatar preset id. Called when the server processes an {@code
   * UpdateAvatarCommand}; the caller validates the id against the {@link common.PlayerAvatars}
   * allowlist first.
   */
  public void setAvatarId(String newAvatarId) {
    this.avatarId = newAvatarId;
  }

  public SocketChannel getChannel() {
    return channel;
  }

  public GameSession getAssociatedGameSession() {
    return associatedGameSession;
  }

  public void setAssociatedGameSession(GameSession gameSession) {
    this.associatedGameSession = gameSession;
  }

  /**
   * Adds a DTO to the outgoing queue for this client. Also signals the GameServer that this channel
   * now has data to write.
   *
   * @param dto The Serializable object (usually a DTO) to send.
   */
  public void send(Serializable dto) {
    // Must synchronize writeQueue as network listener thread might also check it
    // (for OP_WRITE).
    synchronized (writeQueue) {
      if (writeQueue.size() >= NetworkConstants.MAX_WRITE_QUEUE_DTOS) {
        // The peer is not reading (slow-loris reader); buffering further would grow server
        // memory without bound (security-pass issue 09). Drop the connection — the selector
        // loop runs the normal cleanup when the closed channel's key fires.
        try {
          if (channel.isOpen()) {
            channel.close();
          }
        } catch (IOException ignored) {
          // Closing a dead channel; nothing further to do.
        }
        writeQueue.clear();
        return;
      }
      writeQueue.offer(dto);
      // Tell the server's selector we're interested in writing now.
      // This is crucial for OP_WRITE to get triggered.
      server.registerForWrite(this);
    }
  }

  /**
   * Handles reading data from this client's SocketChannel. Implements the state machine for
   * length-prefix framing: 1. Read 4 bytes for length. 2. Read 'length' bytes for the object data.
   * 3. Deserialize and process. This method is called by GameServer when its selector indicates
   * OP_READ is ready.
   *
   * @throws IOException if the client disconnects or a network error occurs.
   */
  public void handleRead() throws IOException {
    int bytesRead;
    // No try-catch here for IOException; GameServer's main loop handles it and
    // calls cleanupClient.
    // This method just throws it up.

    if (readingLength) {
      // Trying to read the 4-byte integer length.
      bytesRead = channel.read(lengthBuffer);
      if (bytesRead == -1) throw new IOException("Client disconnected (EOF on length read).");
      if (bytesRead == 0) return; // Channel not ready, try again later.
      markProgress(); // Length-prefix bytes count as activity (and start the message timer).

      if (!lengthBuffer.hasRemaining()) { // Got all 4 bytes for length.
        lengthBuffer.flip(); // Prepare for reading from buffer.
        expectedObjectLength = lengthBuffer.getInt();
        lengthBuffer.clear(); // Reset for next length.

        // Inbound (client -> server) frames are commands and small DTOs only; anything past the
        // wire ceiling is hostile or corrupt (security-pass issue 02).
        if (expectedObjectLength <= 0
            || expectedObjectLength > common.WireLimits.MAX_INBOUND_FRAME_BYTES) {
          throw new IOException(
              "Invalid object length received: " + expectedObjectLength + ". Closing connection.");
        }
        readingLength = false; // Next, we'll read the object data.
        // Per-frame buffer, bounded by the wire ceiling validated above.
        readBuffer = ByteBuffer.allocate(expectedObjectLength);
      }
    }

    // If we're not reading length, we must be reading the object data.
    if (!readingLength) {
      // Need to check if readBuffer is null, though it's final.
      // But more importantly, expectedObjectLength should be set.
      if (expectedObjectLength <= 0) { // Should not happen if logic above is correct
        throw new IOException(
            "Internal read state error: trying to read object data but expectedObjectLength is invalid: "
                + expectedObjectLength);
      }

      bytesRead = channel.read(readBuffer);
      if (bytesRead == -1) throw new IOException("Client disconnected (EOF on object data read).");
      if (bytesRead == 0) return; // Channel not ready, try again later.
      markProgress(); // Body bytes count as activity.

      if (!readBuffer.hasRemaining()) { // Got all 'expectedObjectLength' bytes.
        readBuffer.flip(); // Prepare for reading from buffer.
        byte[] objectData = new byte[expectedObjectLength];
        readBuffer.get(objectData); // Copy bytes from buffer to array.

        Object receivedObject = null;
        boolean tooManyGarbageFrames = false;
        try {
          receivedObject = SerializationUtils.deserialize(objectData);
        } catch (IOException e) {
          // Catches JsonProcessingException and other IO issues from deserialize
          // This is bad. Client sent something we can't parse, or it was corrupted.
          server.logError(
              "DESERIALIZATION_ERROR from client " + playerId + ": " + e.getMessage(), e);

          // A single bad frame keeps the connection alive (transient corruption); but a sustained
          // flood of undecodable frames is throttled per connection (SECURITY_PLAN B/P2-3), since
          // the command rate limiter below never sees them.
          tooManyGarbageFrames = decodeFailureLimiter.recordFailureAndCheckExceeded();
          if (!tooManyGarbageFrames) {
            this.send(
                new TextMessage(
                    "Server Error: Could not process command (Deserialization Failed).", true));
          }
        }

        // Reset state for the next message BEFORE routing, so a rate-limit disconnect below
        // leaves the session consistent.
        readingLength = true;
        expectedObjectLength = -1;
        readBuffer = null; // Released; the next frame allocates its own.
        // A full message arrived: no partial frame is in progress anymore.
        messageStartedMillis = ConnectionTimeouts.NO_MESSAGE_IN_PROGRESS;

        if (tooManyGarbageFrames) {
          // Outside the deserialize try-catch on purpose: this IOException must reach the selector
          // loop's cleanup path and close only this connection.
          throw new IOException("Too many undecodable frames; closing connection.");
        }

        if (receivedObject != null) {
          // Flood protection (security-pass issue 03): every decoded message spends one token.
          // Decode errors above deliberately do NOT bypass this path's escalation — garbage
          // frames are already throttled by the error reply, and sustained command floods end
          // here.
          switch (rateLimiter.onMessage()) {
            case ALLOW:
              // Message fully read and decoded. Pass it to GameServer for processing/routing.
              server.processClientMessage(this, receivedObject);
              break;
            case DROP_AND_WARN:
              this.send(
                  new TextMessage(
                      "[SERVER] You are sending messages too quickly - command dropped. Slow down.",
                      true));
              break;
            case DROP:
              break; // silently discard
            case DISCONNECT:
              // Outside the deserialize try-catch on purpose: this IOException must reach the
              // selector loop's cleanup path, not the keep-alive error handler above.
              throw new IOException("Command rate limit exceeded. Closing connection.");
          }
        }
      }
    }
  }

  /**
   * Records inbound read activity (SECURITY_PLAN B/P1-1): advances the idle clock and, if no
   * message is currently in progress, marks the start of one so the partial-frame timer runs.
   */
  private void markProgress() {
    long now = clock.getAsLong();
    lastActivityMillis = now;
    if (messageStartedMillis == ConnectionTimeouts.NO_MESSAGE_IN_PROGRESS) {
      messageStartedMillis = now;
    }
  }

  /**
   * True when this connection is idle or stuck mid-frame beyond the configured timeouts and should
   * be reaped (SECURITY_PLAN B/P1-1). Called by the server's selector loop with the current time.
   */
  public boolean isConnectionExpired(long nowMillis) {
    return ConnectionTimeouts.isExpired(
        nowMillis,
        lastActivityMillis,
        messageStartedMillis,
        NetworkConstants.IDLE_TIMEOUT_MS,
        NetworkConstants.PARTIAL_FRAME_TIMEOUT_MS);
  }

  /**
   * Handles writing DTOs from the writeQueue to the client's SocketChannel. This method is called
   * by GameServer when its selector indicates OP_WRITE is ready. It also uses length-prefix
   * framing.
   *
   * @throws IOException if a network error occurs.
   */
  public void handleWrite() throws IOException {
    // Synchronize because 'send' method also modifies this queue.
    synchronized (writeQueue) {
      while (!writeQueue.isEmpty()) {
        // Peek first. Only remove (poll) if fully sent.
        Serializable dtoToSend = writeQueue.peek();
        if (dtoToSend == null) { // Should not happen if !isEmpty() is true, but defensive.
          writeQueue.poll(); // Remove the null.
          continue;
        }

        // Current simple model: serialize and try to write whole DTO (length + data)
        // each time.
        // This is okay if channel usually accepts all bytes or if DTOs are small.
        byte[] objectBytes = SerializationUtils.serialize(dtoToSend); // Serialize DTO to
        // bytes.
        int length = objectBytes.length;

        // Prepare a new ByteBuffer for each DTO. Inefficient for many small DTOs.
        // Could use a single, larger, reusable session write buffer.
        ByteBuffer buffer = ByteBuffer.allocate(4 + length); // 4 bytes for int length.
        buffer.putInt(length);
        buffer.put(objectBytes);
        buffer.flip(); // Ready for writing to channel.

        while (buffer.hasRemaining()) {
          int written = channel.write(buffer);
          if (written == 0) {
            // Socket send buffer is full. Can't write more now.
            // The DTO is still at the head of the queue.
            // OP_WRITE will be triggered again by selector when channel is ready.
            // GameServer MUST ensure OP_WRITE is still registered.
            return; // Exit handleWrite, will try again later.
          }
        }

        // If we reach here, the entire current DTO (length + data) was written
        // successfully.
        writeQueue.poll(); // Remove it from the queue.
      }

      // If queue becomes empty, we don't need OP_WRITE interest anymore for now.
      server.unregisterForWrite(this);
    }
  }

  @Override
  public String toString() {
    // Simple toString for logging.
    return "ClientSession{"
        + "playerId='"
        + playerId.substring(0, Math.min(8, playerId.length()))
        + "..'"
        + // Show partial ID
        ", displayId='"
        + displayId
        + '\''
        + ", session="
        + (associatedGameSession != null
            ? associatedGameSession
                    .getSessionId()
                    .substring(0, Math.min(8, associatedGameSession.getSessionId().length()))
                + ".."
            : "None")
        + ", chanOpen="
        + (channel != null && channel.isOpen())
        + '}';
  }
}

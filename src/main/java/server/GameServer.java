package server;

import common.NetworkConstants;
import common.commands.Command;
import common.dto.ChatMessage;
import common.dto.ClientIdAssignmentDTO;
import common.dto.TextMessage;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GameServer This is my main NIO server class. It listens for client connections, manages reads and
 * writes for connected clients, and routes messages. It runs in its own thread.
 */
public class GameServer implements Runnable {

  private static final Logger logger = LoggerFactory.getLogger(GameServer.class);

  // --- Fields ---
  private final int port;
  private ServerSocketChannel serverSocketChannel;
  private Selector selector;
  private volatile boolean running = true; // Flag to control the main server loop.

  // Manages all active client connections. SocketChannel -> ClientSession.
  private final Map<SocketChannel, ClientSession> clientSessionsMap;
  // Manages game rooms, lobbies, etc. Protected so ServerMain can access for admin commands.
  protected final GameSessionManager sessionManager;

  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

  // --- Constructor ---
  public GameServer(int port) {
    this.port = port;
    this.clientSessionsMap = new ConcurrentHashMap<>(); // Thread-safe map for client sessions.
    this.sessionManager = new GameSessionManager(this);
  }

  // --- Server Setup & Control ---

  /**
   * Initializes the server: opens selector, server socket, binds, and registers for accept. This
   * needs to be called before starting the server thread.
   */
  public void startServer() throws IOException {
    this.selector = Selector.open();
    this.serverSocketChannel = ServerSocketChannel.open();
    this.serverSocketChannel.configureBlocking(false); // Non-blocking for selector.
    this.serverSocketChannel.socket().bind(new InetSocketAddress(port));
    this.serverSocketChannel.register(
        selector, SelectionKey.OP_ACCEPT); // Listen for new connections.

    log("Server started on port " + port + ". Waiting for connections...");
  }

  /**
   * Signals the server's main loop to stop running, wakes up the selector, and closes the server
   * socket to release the port immediately.
   */
  public void stopServer() {
    log("StopServer called. Signaling server loop to terminate and closing socket.");
    this.running = false;

    // Close the server socket channel immediately to release the port.
    // This will cause an exception in the select() loop, which is handled.
    if (serverSocketChannel != null && serverSocketChannel.isOpen()) {
      try {
        serverSocketChannel.close();
        log("Server socket channel closed.");
      } catch (IOException e) {
        logError("Error closing server socket channel during stop: " + e.getMessage(), e);
      }
    }

    // Wake up the selector to ensure the loop terminates promptly.
    if (this.selector != null && this.selector.isOpen()) {
      this.selector.wakeup(); // Interrupt selector.select() if it's blocking.
    }
  }

  /**
   * Cleans up server resources when the server is shutting down. Closes channels, selector, and
   * saves games. Called from finally block of run().
   */
  protected void shutdownServerInternals() {
    log("Server is shutting down internals...");
    // Close selector first to stop processing new events.
    if (selector != null && selector.isOpen()) {
      // Close all client channels registered with the selector.
      for (SelectionKey key : selector.keys()) {
        try {
          if (key.channel() != null && key.channel().isOpen()) {
            key.channel().close();
          }
          key.cancel(); // Cancel the key.
        } catch (IOException e) {
          logError("Error closing channel during shutdown: " + key.channel(), e);
        }
      }
      try {
        selector.close();
        log("Selector closed.");
      } catch (IOException e) {
        logError("Error closing selector: " + e.getMessage(), e);
      }
    }
    // Close the server socket channel.
    if (serverSocketChannel != null && serverSocketChannel.isOpen()) {
      try {
        serverSocketChannel.close();
        log("Server socket channel closed.");
      } catch (IOException e) {
        logError("Error closing server socket channel: " + e.getMessage(), e);
      }
    }
    log("Server has shut down internals.");
    // clientSessionsMap will be cleared as clients get cleaned up.
  }

  // --- Main Server Loop (Runnable Implementation) ---
  @Override
  public void run() {
    try {
      while (running) {
        // Wait for an event or timeout.
        int readyChannels = selector.select(NetworkConstants.SELECTOR_TIMEOUT);

        if (!running) { // Double-check running flag after select().
          break;
        }
        // Reap idle / stuck (slow-loris) connections every cycle. select() returns at least once
        // per SELECTOR_TIMEOUT, so this runs ~once a second even under no I/O (SECURITY_PLAN
        // B/P1-1).
        reapExpiredConnections();
        if (readyChannels == 0) {
          // Timeout, no I/O events. Good place for periodic tasks if needed.
          continue;
        }

        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

        while (keyIterator.hasNext()) {
          SelectionKey key = keyIterator.next();
          keyIterator.remove(); // Must remove the key from the selected set.

          if (!key.isValid()) {
            // Key might have been cancelled (e.g., channel closed).
            // cleanupClient would have been called if so.
            // log("Key is invalid, skipping.");
            continue;
          }

          // Handle I/O events for this key.
          try {
            if (key.isAcceptable()) {
              handleAccept(key);
            }
            if (key.isReadable()) { // Key might become invalid after accept/read/write if channel
              // closes
              handleRead(key);
            }
            if (key.isWritable()) { // Check validity again
              handleWrite(key);
            }
          } catch (CancelledKeyException cke) {
            // This happens if channel closed while processing.
            // ClientSession object might still be in map if cleanupClient hasn't run for this key
            // yet.
            ClientSession client =
                (ClientSession) key.attachment(); // More reliable way to get session
            String clientId =
                (client != null)
                    ? client.getPlayerId()
                    : "Unknown (channel: " + key.channel().hashCode() + ")";
            log("Key cancelled for client " + clientId + " during event processing. Cleaning up.");
            cleanupClient(key, "Key was cancelled during op");
          } catch (IOException e) {
            // Network errors (e.g., client disconnected abruptly).
            ClientSession client = (ClientSession) key.attachment();
            String clientId =
                (client != null)
                    ? client.getPlayerId()
                    : "Unknown (channel: " + key.channel().hashCode() + ")";
            log(
                "I/O Error for client "
                    + clientId
                    + ": "
                    + e.getMessage()
                    + ". Closing connection.");
            cleanupClient(key, "I/O Error: " + e.getMessage());
          } catch (Exception e) {
            // Catch-all for unexpected errors during key processing to keep server running.
            ClientSession client = (ClientSession) key.attachment();
            String clientId = (client != null) ? client.getPlayerId() : "Unknown";
            logError("Unexpected error processing key for client " + clientId, e);
            cleanupClient(key, "Unexpected error: " + e.getMessage());
          }
        }
      }
    } catch (ClosedSelectorException cse) {
      // This is expected if stopServer() closes the selector.
      log("Selector closed, server thread shutting down as expected.");
    } catch (IOException e) {
      // Major I/O error in the selector.select() loop itself.
      logError("Server run loop I/O error (selector.select() issue?): " + e.getMessage(), e);
    } finally {
      log("Server loop is terminating. Initiating internal shutdown.");
      shutdownServerInternals();
    }
  }

  // --- Event Handlers (Called by run() loop) ---

  private void handleAccept(SelectionKey key) throws IOException {
    ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
    SocketChannel clientChannel = ssc.accept(); // Accept the new connection.

    if (clientChannel != null) {
      // Connection cap (security-pass issue 09): each session costs buffers and lobby slots;
      // beyond the cap new connections are refused outright.
      if (clientSessionsMap.size() >= NetworkConstants.MAX_CONNECTIONS) {
        log(
            "Connection limit ("
                + NetworkConstants.MAX_CONNECTIONS
                + ") reached. Refusing new connection from "
                + clientChannel.getRemoteAddress());
        clientChannel.close();
        return;
      }
      clientChannel.configureBlocking(false); // Must be non-blocking for selector.
      SelectionKey clientKey =
          clientChannel.register(
              selector, SelectionKey.OP_READ); // Initially interested in reading.

      ClientSession clientSession = new ClientSession(clientChannel, this);
      clientKey.attach(clientSession); // Attach session object to key for easy retrieval.
      clientSessionsMap.put(clientChannel, clientSession); // Also keep in map for now.

      SocketAddress remoteAddr = clientChannel.getRemoteAddress();
      log(
          "Accepted new connection from: "
              + (remoteAddr != null ? remoteAddr.toString() : "Unknown")
              + " | PlayerID: "
              + clientSession.getPlayerId()
              + " (Display: "
              + clientSession.getDisplayId()
              + ")");

      // Send initial DTOs for client setup.
      clientSession.send(
          new ClientIdAssignmentDTO(clientSession.getPlayerId(), clientSession.getDisplayId()));
      clientSession.send(
          new TextMessage(
              "Welcome, " + clientSession.getDisplayId() + "! Connected to Detective Game Server.",
              false));
    }
  }

  private void handleRead(SelectionKey key) throws IOException {
    ClientSession clientSession = (ClientSession) key.attachment(); // Get session from key.
    if (clientSession != null) {
      clientSession.handleRead(); // Delegate to ClientSession to read framed data.
    } else {
      // This shouldn't happen if attach() was successful and key is valid.
      log(
          "Warning: Read event for key with no ClientSession attachment. Channel: "
              + key.channel().hashCode());
      key.channel().close();
      key.cancel();
    }
  }

  private void handleWrite(SelectionKey key) throws IOException {
    ClientSession clientSession = (ClientSession) key.attachment();
    if (clientSession != null) {
      clientSession.handleWrite(); // Delegate to ClientSession to write queued data.
    } else {
      log(
          "Warning: Write event for key with no ClientSession attachment. Channel: "
              + key.channel().hashCode());
      key.channel().close();
      key.cancel();
    }
  }

  // --- Client Management & Message Routing ---

  /**
   * Processes a fully deserialized message from a client. Routes it to GameSessionManager (for
   * lobby commands) or GameSession (for in-game commands).
   */
  public void processClientMessage(ClientSession sender, Object message) {
    if (sender == null || message == null) return; // Basic sanity check.
    log("Received from " + sender.getDisplayId() + ": " + message.getClass().getSimpleName());

    if (message instanceof Command command) {
      GameSession session = sender.getAssociatedGameSession();
      if (session != null) {
        // Client is in a game session (could be WAITING, ACTIVE, etc.)
        session.processCommand(command, sender.getPlayerId());
      } else {
        // Client is not in a game session yet (e.g., just connected, using lobby commands).
        sessionManager.processLobbyCommand(sender, command);
      }
    } else if (message instanceof ChatMessage chatMsg) {
      GameSession session = sender.getAssociatedGameSession();
      if (session != null
          && (session.getState() == GameSessionState.ACTIVE
              || session.getState() == GameSessionState.IN_LOBBY_AWAITING_START
              || (session.getState() == GameSessionState.WAITING_FOR_PLAYERS
                  && session.isFull()))) {
        // Stamp sender identity and time server-side — the wire values are client-controlled
        // and spoofable (security-pass issue 05).
        session.processChatMessage(
            new ChatMessage(sender.getDisplayId(), chatMsg.getText(), System.currentTimeMillis()));
      } else {
        sender.send(new TextMessage("Chat only available in game lobbies or active games.", true));
      }
    } else {
      log(
          "Warning: Received unknown object type from "
              + sender.getPlayerId()
              + ": "
              + message.getClass().getName());
      sender.send(new TextMessage("Server received an unknown message type.", true));
    }
  }

  /**
   * Cleans up a client connection: cancels key, closes channel, notifies session manager. Called
   * when an error occurs or client disconnects.
   */

  // PASTE THIS, REPLACING the current cleanupClient method in server/GameServer.java

  private void cleanupClient(SelectionKey key, String reason) {
    SocketChannel clientChannel = null;
    ClientSession clientSession = null;

    if (key != null) {
      clientChannel = (SocketChannel) key.channel();
      clientSession = (ClientSession) key.attachment();
      key.cancel();
    }

    if (clientChannel != null) {
      clientSessionsMap.remove(clientChannel);
    }

    if (clientSession != null) {
      logger.info("Cleaning up client {}. Reason: {}", clientSession.getDisplayId(), reason);
      sessionManager.handleClientDisconnect(clientSession);
    } else {
      logger.info("Cleaning up a client that had no session. Reason: {}", reason);
    }

    try {
      if (clientChannel != null && clientChannel.isOpen()) {
        clientChannel.close();
      }
    } catch (IOException ex) {
      logger.error("Error closing channel during cleanup.", ex);
    }
  }

  /**
   * Closes connections that have gone idle or are stuck mid-frame beyond the configured timeouts
   * (SECURITY_PLAN B/P1-1), freeing their slots. Runs on the selector thread, so it shares the same
   * cleanup path as normal disconnects and never races the I/O handlers. A reaped connection is
   * closed cleanly; healthy, actively-playing clients (which read regularly) are never touched.
   */
  private void reapExpiredConnections() {
    if (clientSessionsMap.isEmpty()) {
      return;
    }
    long now = System.currentTimeMillis();
    List<SelectionKey> expiredKeys = new ArrayList<>();
    for (ClientSession session : clientSessionsMap.values()) {
      if (!session.isConnectionExpired(now)) {
        continue;
      }
      SelectionKey key = session.getChannel().keyFor(selector);
      if (key != null) {
        expiredKeys.add(key);
      } else {
        // No selector key (unusual): close and drop the session directly.
        try {
          if (session.getChannel().isOpen()) {
            session.getChannel().close();
          }
        } catch (IOException ignored) {
          // Closing a dead channel; nothing further to do.
        }
        clientSessionsMap.remove(session.getChannel());
        sessionManager.handleClientDisconnect(session);
      }
    }
    // Cleanup after iterating, since cleanupClient mutates clientSessionsMap.
    for (SelectionKey key : expiredKeys) {
      ClientSession session = (ClientSession) key.attachment();
      log(
          "Reaping idle/stuck connection: "
              + (session != null ? session.getDisplayId() : "unknown"));
      cleanupClient(key, "Idle/stuck connection timeout");
    }
  }

  /**
   * Called by ClientSession when its writeQueue is empty. Signals the selector that this channel is
   * no longer interested in write operations (for now).
   */
  public void registerForWrite(ClientSession client) {
    if (client == null
        || client.getChannel() == null
        || !client.getChannel().isOpen()
        || selector == null
        || !selector.isOpen()) {
      return; // Invalid state, do nothing.
    }
    try {
      SelectionKey key = client.getChannel().keyFor(selector);
      if (key != null && key.isValid()) {
        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE); // Add write interest
        selector.wakeup(); // Wake up selector if it's blocking
      }
    } catch (CancelledKeyException e) {
      // Key was cancelled, client is likely disconnecting. Cleanup is handled elsewhere.
      logger.debug("Could not register for write, key cancelled for {}", client.getPlayerId());
    }
  }

  /**
   * Called by a ClientSession when its writeQueue is empty. This signals the selector that the
   * channel is no longer interested in write operations.
   */
  public void unregisterForWrite(ClientSession client) {
    if (client == null
        || client.getChannel() == null
        || !client.getChannel().isOpen()
        || selector == null
        || !selector.isOpen()) {
      return;
    }
    try {
      SelectionKey key = client.getChannel().keyFor(selector);
      if (key != null && key.isValid()) {
        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE); // Remove write interest
      }
    } catch (CancelledKeyException e) {
      // Key was cancelled, client is likely disconnecting. Cleanup is handled elsewhere.
      logger.debug("Could not unregister for write, key cancelled for {}", client.getPlayerId());
    }
  }

  // --- Logging Utilities ---
  public void log(String message) {
    logger.info(message);
  }

  public void logError(String message, Throwable throwable) {
    logger.error(message, throwable);
  }
}

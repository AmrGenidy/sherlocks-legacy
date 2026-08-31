package server;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

/**
 * A {@link SocketChannel} test double that feeds a pre-scripted byte stream to {@link
 * ClientSession#handleRead()} so the length-prefix framing state machine can be driven
 * deterministically with {@link ByteBuffer}s instead of a real socket.
 *
 * <p>{@code ClientSession} only ever calls {@link #read(ByteBuffer)} on its channel; every other
 * abstract operation throws {@link UnsupportedOperationException} so accidental use is loud.
 *
 * <p>Two delivery modes:
 *
 * <ul>
 *   <li><b>Greedy</b> (no plan): each {@code read} transfers as many of the still-available bytes
 *       as fit in the destination buffer. Models "all bytes are already in the socket receive
 *       buffer", used for the single-frame and back-to-back-frames-in-one-read cases.
 *   <li><b>Scripted</b>: each {@code read} consumes one entry from a plan of per-call byte counts,
 *       transferring exactly that many bytes (clamped to the destination's remaining space and to
 *       the bytes still available). A plan entry of {@code 0} models "channel not ready right now";
 *       {@code -1} models EOF (peer disconnect). This reproduces split length prefixes and split
 *       payloads.
 * </ul>
 */
final class ScriptedSocketChannel extends SocketChannel {

  private final ByteBuffer source;
  private final Deque<Integer> readPlan; // empty => greedy
  private boolean inputShutdown = false;

  /** Greedy channel: delivers up to the destination's capacity from {@code bytes} each read. */
  static ScriptedSocketChannel greedy(byte[] bytes) {
    return new ScriptedSocketChannel(bytes, new ArrayDeque<>());
  }

  /** Scripted channel: each read delivers the next planned count (0 = not ready, -1 = EOF). */
  static ScriptedSocketChannel scripted(byte[] bytes, int... perReadCounts) {
    Deque<Integer> plan = new ArrayDeque<>();
    for (int c : perReadCounts) {
      plan.addLast(c);
    }
    return new ScriptedSocketChannel(bytes, plan);
  }

  private ScriptedSocketChannel(byte[] bytes, Deque<Integer> readPlan) {
    super(SelectorProvider.provider());
    this.source = ByteBuffer.wrap(bytes);
    this.readPlan = readPlan;
  }

  @Override
  public int read(ByteBuffer dst) throws IOException {
    if (inputShutdown) {
      return -1;
    }
    int available = source.remaining();
    int room = dst.remaining();

    int toTransfer;
    if (readPlan.isEmpty()) {
      // Greedy: hand over whatever fits.
      toTransfer = Math.min(available, room);
    } else {
      int planned = readPlan.pollFirst();
      if (planned == -1) {
        return -1; // Scripted EOF.
      }
      toTransfer = Math.min(planned, Math.min(available, room));
    }

    for (int i = 0; i < toTransfer; i++) {
      dst.put(source.get());
    }
    return toTransfer;
  }

  // --- Everything below is unused by ClientSession.handleRead() ---

  @Override
  public long read(ByteBuffer[] dsts, int offset, int length) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int write(ByteBuffer src) {
    throw new UnsupportedOperationException();
  }

  @Override
  public long write(ByteBuffer[] srcs, int offset, int length) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SocketChannel bind(SocketAddress local) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> SocketChannel setOption(SocketOption<T> name, T value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T getOption(SocketOption<T> name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<SocketOption<?>> supportedOptions() {
    return Set.of();
  }

  @Override
  public SocketChannel shutdownInput() {
    inputShutdown = true;
    return this;
  }

  @Override
  public SocketChannel shutdownOutput() {
    return this;
  }

  @Override
  public Socket socket() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isConnected() {
    return true;
  }

  @Override
  public boolean isConnectionPending() {
    return false;
  }

  @Override
  public boolean connect(SocketAddress remote) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean finishConnect() {
    return true;
  }

  @Override
  public SocketAddress getRemoteAddress() {
    return null;
  }

  @Override
  public SocketAddress getLocalAddress() {
    return null;
  }

  @Override
  protected void implCloseSelectableChannel() {
    // no-op
  }

  @Override
  protected void implConfigureBlocking(boolean block) {
    // no-op
  }
}

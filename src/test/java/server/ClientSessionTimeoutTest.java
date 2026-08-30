package server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import common.NetworkConstants;
import common.SerializationUtils;
import common.commands.MoveCommand;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

/**
 * ClientSession idle / slow-loris timeout behaviour (SECURITY_PLAN B/P1-1), driven by a fake clock
 * and a mocked SocketChannel so the timeouts are exercised deterministically without real sockets.
 */
public class ClientSessionTimeoutTest {

  private static final long START = 1_000_000L;

  private static ClientSession session(SocketChannel channel, AtomicLong clock) {
    GameServer server = mock(GameServer.class);
    return new ClientSession(channel, server, new CommandRateLimiter(), clock::get);
  }

  @Test
  public void connectThenSilentIsReapedAfterIdleTimeout() {
    AtomicLong now = new AtomicLong(START);
    ClientSession session = session(mock(SocketChannel.class), now);

    assertFalse("fresh connection is alive", session.isConnectionExpired(now.get()));
    assertFalse(
        "at the exact idle boundary it is still alive",
        session.isConnectionExpired(START + NetworkConstants.IDLE_TIMEOUT_MS));
    assertTrue(
        "a silent connection past the idle timeout is expired",
        session.isConnectionExpired(START + NetworkConstants.IDLE_TIMEOUT_MS + 1));
  }

  @Test
  public void lengthPrefixButNoBodyIsReapedAfterPartialFrameTimeout() throws Exception {
    AtomicLong now = new AtomicLong(START);
    SocketChannel channel = mock(SocketChannel.class);
    // Deliver the 4-byte length prefix (announcing a 100-byte body), then never any body bytes.
    when(channel.read(any(ByteBuffer.class)))
        .thenAnswer(
            invocation -> {
              ByteBuffer buffer = invocation.getArgument(0);
              if (buffer.capacity() == 4 && buffer.position() == 0) {
                buffer.putInt(100);
                return 4;
              }
              return 0; // body never arrives
            });
    ClientSession session = session(channel, now);

    session.handleRead(); // reads the length prefix; a message is now in progress

    assertFalse("still within the partial-frame window", session.isConnectionExpired(now.get()));
    long past = START + NetworkConstants.PARTIAL_FRAME_TIMEOUT_MS + 1;
    assertTrue(
        "a stuck partial frame past the timeout is expired", session.isConnectionExpired(past));
    assertTrue(
        "and it is the partial-frame timer firing, not the (much longer) idle one",
        past - START < NetworkConstants.IDLE_TIMEOUT_MS);
  }

  @Test
  public void aClientThatCompletesMessagesIsNeverReapedMidPlay() throws Exception {
    AtomicLong now = new AtomicLong(START);
    SocketChannel channel = mock(SocketChannel.class);
    byte[] body = SerializationUtils.serialize(new MoveCommand("north"));
    ByteBuffer frame = ByteBuffer.allocate(4 + body.length);
    frame.putInt(body.length);
    frame.put(body);
    frame.flip();
    when(channel.read(any(ByteBuffer.class)))
        .thenAnswer(
            invocation -> {
              ByteBuffer dst = invocation.getArgument(0);
              int n = 0;
              while (dst.hasRemaining() && frame.hasRemaining()) {
                dst.put(frame.get());
                n++;
              }
              return n;
            });
    ClientSession session = session(channel, now);

    session.handleRead(); // reads a complete framed message → no partial frame in progress

    // Past the (short) partial-frame timeout but within the idle window: a client that completed a
    // message must NOT be dropped just because time passed since it last spoke.
    long later = START + NetworkConstants.PARTIAL_FRAME_TIMEOUT_MS + 5_000;
    assertTrue(
        "precondition: still within idle window", later - START < NetworkConstants.IDLE_TIMEOUT_MS);
    assertFalse(
        "a client that completed its message stays connected", session.isConnectionExpired(later));
  }
}

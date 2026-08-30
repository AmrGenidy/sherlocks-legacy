package server;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import org.junit.Test;

/**
 * A sustained flood of undecodable frames must drop only the offending connection (SECURITY_PLAN
 * B/P2-3): the command rate limiter never sees garbage frames, so the decode-failure limiter is
 * what escalates to disconnect.
 */
public class ClientSessionDecodeFloodTest {

  @Test
  public void floodOfUndecodableFramesDropsTheConnection() throws Exception {
    GameServer server = mock(GameServer.class);
    SocketChannel channel = mock(SocketChannel.class);
    // Every frame is a valid 2-byte-length envelope whose body ("zz") is not valid JSON, so each
    // decodes-fail. Distinguish the 4-byte length buffer from the 2-byte body buffer by capacity.
    when(channel.read(any(ByteBuffer.class)))
        .thenAnswer(
            invocation -> {
              ByteBuffer buffer = invocation.getArgument(0);
              if (buffer.capacity() == 4) {
                buffer.putInt(2);
                return 4;
              }
              buffer.put((byte) 'z').put((byte) 'z');
              return 2;
            });
    // Fixed clock so every failure lands inside one window (no pruning).
    ClientSession session =
        new ClientSession(channel, server, new CommandRateLimiter(), () -> 1_000L);

    // The threshold's worth of garbage is tolerated (connection stays up)...
    for (int i = 0; i < DecodeFailureLimiter.MAX_FAILURES; i++) {
      session.handleRead();
    }
    // ...but one more garbage frame closes the connection (IOException reaches the selector
    // cleanup).
    assertThrows(IOException.class, session::handleRead);
  }
}

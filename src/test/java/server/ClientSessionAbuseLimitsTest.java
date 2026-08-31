package server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import common.SerializationUtils;
import common.commands.MoveCommand;
import common.dto.TextMessage;
import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import org.junit.Test;

/**
 * Connection-boundary abuse limits (security-pass issues 03/09), driven through the real {@link
 * ClientSession} read path with {@link ScriptedSocketChannel} byte streams: a command flood is
 * throttled after the burst budget, sustained flooding disconnects, and a peer that never drains
 * its write queue is dropped instead of growing server memory.
 */
public class ClientSessionAbuseLimitsTest {

  private static byte[] frames(int count) throws IOException {
    byte[] one = frame(new MoveCommand("north"));
    ByteBuffer all = ByteBuffer.allocate(one.length * count);
    for (int i = 0; i < count; i++) {
      all.put(one);
    }
    return all.array();
  }

  private static byte[] frame(Serializable obj) throws IOException {
    byte[] payload = SerializationUtils.serialize(obj);
    return ByteBuffer.allocate(4 + payload.length).putInt(payload.length).put(payload).array();
  }

  /** A session whose limiter sees a frozen clock, so the bucket never refills. */
  private static ClientSession frozenClockSession(
      ScriptedSocketChannel channel, RecordingGameServer server) {
    return new ClientSession(channel, server, new CommandRateLimiter(() -> 0L));
  }

  @Test
  public void floodBeyondTheBurstBudgetIsDropped() throws IOException {
    RecordingGameServer server = new RecordingGameServer();
    int flood = CommandRateLimiter.BURST_CAPACITY + 10;
    ClientSession session = frozenClockSession(ScriptedSocketChannel.greedy(frames(flood)), server);

    for (int i = 0; i < flood; i++) {
      session.handleRead();
    }

    assertEquals(
        "only the burst budget may be routed",
        CommandRateLimiter.BURST_CAPACITY,
        server.received().size());
  }

  @Test
  public void sustainedFloodDisconnectsWithIOException() throws IOException {
    RecordingGameServer server = new RecordingGameServer();
    int flood =
        CommandRateLimiter.BURST_CAPACITY + CommandRateLimiter.DISCONNECT_AFTER_CONSECUTIVE_DROPS;
    ClientSession session = frozenClockSession(ScriptedSocketChannel.greedy(frames(flood)), server);

    try {
      for (int i = 0; i < flood; i++) {
        session.handleRead();
      }
      fail("Expected the sustained flood to raise IOException for the cleanup path");
    } catch (IOException expected) {
      assertTrue(expected.getMessage().contains("rate limit"));
    }
    assertEquals(CommandRateLimiter.BURST_CAPACITY, server.received().size());
  }

  @Test
  public void writeQueueOverflowClosesTheConnection() throws IOException {
    RecordingGameServer server = new RecordingGameServer();
    ScriptedSocketChannel channel = ScriptedSocketChannel.greedy(new byte[0]);
    ClientSession session = new ClientSession(channel, server);

    // The peer never reads (handleWrite never runs), so the queue only grows.
    for (int i = 0; i <= common.NetworkConstants.MAX_WRITE_QUEUE_DTOS; i++) {
      session.send(new TextMessage("m" + i, false));
    }

    assertFalse(
        "a peer that never drains its queue must be dropped, not buffered forever",
        channel.isOpen());
  }
}

package server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import common.SerializationUtils;
import common.commands.MoveCommand;
import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/**
 * Drives {@link ClientSession}'s length-prefix framing state machine directly with byte streams
 * (via {@link ScriptedSocketChannel}) rather than real sockets, per the Phase-1 server-tests plan
 * (.scratch/server-tests/issues/03). Covers the classic reassembly hazards: split length prefix,
 * split payload, multiple frames in one read, the not-ready (0-byte) read, EOF, oversize/invalid
 * length rejection, and graceful recovery from an undecodable payload.
 *
 * <p>The wire format mirrors {@link SerializationUtils#writeFramedObject}: a 4-byte big-endian
 * length followed by that many serialized bytes.
 */
public class ClientSessionFramingTest {

  /** Builds one framed message: 4-byte length prefix + serialized payload. */
  private static byte[] frame(Serializable obj) throws IOException {
    byte[] payload = SerializationUtils.serialize(obj);
    return ByteBuffer.allocate(4 + payload.length).putInt(payload.length).put(payload).array();
  }

  /**
   * A length prefix declaring {@code declaredLength} bytes, with the given raw payload appended.
   */
  private static byte[] frameWithRawPayload(int declaredLength, byte[] payload) {
    return ByteBuffer.allocate(4 + payload.length).putInt(declaredLength).put(payload).array();
  }

  private static byte[] concat(byte[] a, byte[] b) {
    return ByteBuffer.allocate(a.length + b.length).put(a).put(b).array();
  }

  private static ClientSession session(ScriptedSocketChannel channel, RecordingGameServer server) {
    return new ClientSession(channel, server);
  }

  @Test
  public void singleFrameInOneRead_decodesOnce() throws IOException {
    RecordingGameServer server = new RecordingGameServer();
    byte[] wire = frame(new MoveCommand("north"));
    ClientSession session = session(ScriptedSocketChannel.greedy(wire), server);

    session.handleRead();

    assertEquals(1, server.received().size());
    assertTrue(server.received().get(0) instanceof MoveCommand);
    assertEquals("north", ((MoveCommand) server.received().get(0)).getDirection());
  }

  @Test
  public void partialLengthPrefix_reassemblesAcrossReads() throws IOException {
    RecordingGameServer server = new RecordingGameServer();
    byte[] wire = frame(new MoveCommand("east"));
    int payloadLen = wire.length - 4;
    // 2 bytes of the length, then the other 2, then the whole payload.
    ScriptedSocketChannel channel = ScriptedSocketChannel.scripted(wire, 2, 2, payloadLen);
    ClientSession session = session(channel, server);

    session.handleRead(); // only 2 of 4 length bytes -> nothing decoded yet
    assertEquals(0, server.received().size());

    session.handleRead(); // remaining length byte pair completes, then payload is read in-call
    assertEquals(1, server.received().size());
    assertEquals("east", ((MoveCommand) server.received().get(0)).getDirection());
  }

  @Test
  public void partialPayload_reassemblesAcrossReads() throws IOException {
    RecordingGameServer server = new RecordingGameServer();
    byte[] wire = frame(new MoveCommand("south"));
    int payloadLen = wire.length - 4;
    int firstChunk = payloadLen / 2;
    // Full length in one go, then the payload split into two reads.
    ScriptedSocketChannel channel =
        ScriptedSocketChannel.scripted(wire, 4, firstChunk, payloadLen - firstChunk);
    ClientSession session = session(channel, server);

    session.handleRead(); // length + first half of payload
    assertEquals(0, server.received().size());

    session.handleRead(); // second half completes the frame
    assertEquals(1, server.received().size());
    assertEquals("south", ((MoveCommand) server.received().get(0)).getDirection());
  }

  @Test
  public void multipleFramesAvailable_decodeOnePerHandleRead() throws IOException {
    RecordingGameServer server = new RecordingGameServer();
    byte[] wire = concat(frame(new MoveCommand("up")), frame(new MoveCommand("down")));
    ClientSession session = session(ScriptedSocketChannel.greedy(wire), server);

    session.handleRead(); // first frame
    assertEquals(1, server.received().size());

    session.handleRead(); // second frame, from the bytes left in the same buffer
    assertEquals(2, server.received().size());
    assertEquals("up", ((MoveCommand) server.received().get(0)).getDirection());
    assertEquals("down", ((MoveCommand) server.received().get(1)).getDirection());
  }

  @Test
  public void notReadyRead_isANoOpThenResumes() throws IOException {
    RecordingGameServer server = new RecordingGameServer();
    byte[] wire = frame(new MoveCommand("west"));
    int payloadLen = wire.length - 4;
    // First read returns 0 bytes (channel not ready), then the frame arrives normally.
    ScriptedSocketChannel channel = ScriptedSocketChannel.scripted(wire, 0, 4, payloadLen);
    ClientSession session = session(channel, server);

    session.handleRead(); // 0 bytes -> no progress, no error
    assertEquals(0, server.received().size());

    session.handleRead(); // length...
    session.handleRead(); // ...then payload (separate plan entry)
    assertEquals(1, server.received().size());
    assertEquals("west", ((MoveCommand) server.received().get(0)).getDirection());
  }

  @Test
  public void oversizeLength_isRejectedWithoutAllocating() throws IOException {
    RecordingGameServer server = new RecordingGameServer();
    int oversize = common.WireLimits.MAX_INBOUND_FRAME_BYTES + 1; // just past the cap
    byte[] wire = frameWithRawPayload(oversize, new byte[0]);
    ClientSession session = session(ScriptedSocketChannel.greedy(wire), server);

    try {
      session.handleRead();
      fail("Expected oversize frame to be rejected with IOException");
    } catch (IOException expected) {
      assertTrue(expected.getMessage().contains("Invalid object length"));
    }
    assertEquals(0, server.received().size());
  }

  @Test
  public void nonPositiveLength_isRejected() throws IOException {
    RecordingGameServer server = new RecordingGameServer();
    byte[] wire = frameWithRawPayload(0, new byte[0]);
    ClientSession session = session(ScriptedSocketChannel.greedy(wire), server);

    try {
      session.handleRead();
      fail("Expected zero-length frame to be rejected with IOException");
    } catch (IOException expected) {
      assertTrue(expected.getMessage().contains("Invalid object length"));
    }
  }

  @Test
  public void eofOnLengthRead_throwsDisconnect() {
    RecordingGameServer server = new RecordingGameServer();
    ScriptedSocketChannel channel = ScriptedSocketChannel.scripted(new byte[0], -1);
    ClientSession session = session(channel, server);

    try {
      session.handleRead();
      fail("Expected EOF to surface as IOException");
    } catch (IOException expected) {
      assertTrue(expected.getMessage().contains("EOF"));
    }
  }

  @Test
  public void undecodablePayload_keepsSessionAliveAndDecodesNextFrame() throws IOException {
    RecordingGameServer server = new RecordingGameServer();
    byte[] garbage = "this is not a serialized object".getBytes(StandardCharsets.UTF_8);
    byte[] badFrame = frameWithRawPayload(garbage.length, garbage);
    byte[] goodFrame = frame(new MoveCommand("north"));
    ClientSession session =
        session(ScriptedSocketChannel.greedy(concat(badFrame, goodFrame)), server);

    // Deserialization failure must NOT throw (the connection stays open) and must NOT route
    // anything.
    session.handleRead();
    assertEquals(0, server.received().size());

    // State must be reset so the very next frame decodes cleanly.
    session.handleRead();
    assertEquals(1, server.received().size());
    assertEquals("north", ((MoveCommand) server.received().get(0)).getDirection());
  }
}

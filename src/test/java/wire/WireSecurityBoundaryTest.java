package wire;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import common.SerializationUtils;
import common.commands.MoveCommand;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/**
 * Security boundary of the wire protocol. Because the Phase 3 web track exposes this exact {@link
 * SerializationUtils} JSON to untrusted browsers (docs/ROADMAP.md), the deserializer must refuse
 * anything outside the polymorphic allowlist and must refuse oversized frames before allocating for
 * them. These tests pin both guarantees.
 */
public class WireSecurityBoundaryTest {

  // Client-inbound ceiling, lowered from 10 MB (SECURITY_PLAN B/P2-2).
  private static final int MAX_FRAME_BYTES = common.WireLimits.MAX_CLIENT_INBOUND_FRAME_BYTES;

  // --- Polymorphic type allowlist ---------------------------------------------------------------

  @Test
  public void deserializeRejectsTypeOutsideAllowlist() {
    // A @class tag pointing at an arbitrary JDK type must be refused by the
    // PolymorphicTypeValidator
    // (only common.commands, common.dto, JsonDTO, List and Map subtypes are permitted). Without
    // this guard, default typing would be a remote-code-execution style gadget sink.
    byte[] hostile =
        "{\"@class\":\"java.io.File\",\"path\":\"/etc/passwd\"}".getBytes(StandardCharsets.UTF_8);

    IOException ex = assertThrows(IOException.class, () -> SerializationUtils.deserialize(hostile));
    assertTrue(
        "rejection should name the offending type, was: " + ex.getMessage(),
        ex.getMessage() != null && ex.getMessage().contains("java.io.File"));
  }

  @Test
  public void deserializeRejectsAnotherDisallowedSystemType() {
    byte[] hostile =
        "{\"@class\":\"java.lang.ProcessBuilder\",\"command\":[]}".getBytes(StandardCharsets.UTF_8);

    assertThrows(IOException.class, () -> SerializationUtils.deserialize(hostile));
  }

  @Test
  public void deserializeAcceptsAllowlistedWireType() {
    // Sanity: a legitimate Command from an allowlisted package round-trips, so the allowlist is a
    // scalpel, not a blanket deny.
    byte[] wire = serialize(new MoveCommand("north"));

    Object restored = deserialize(wire);

    assertTrue(restored instanceof MoveCommand);
    assertEquals("north", ((MoveCommand) restored).getDirection());
  }

  @Test
  public void deserializeRejectsArbitraryListSubtype() {
    // Deny-by-default: the allowlist must name the exact concrete collections the protocol uses,
    // not "any java.util.List subtype". java.util.Stack stands in for an unexpected/gadget
    // collection type arriving with a type tag.
    byte[] hostile = "[\"java.util.Stack\",[]]".getBytes(StandardCharsets.UTF_8);

    assertThrows(IOException.class, () -> SerializationUtils.deserialize(hostile));
  }

  @Test
  public void deserializeRejectsArbitraryMapSubtype() {
    // Same for Map: java.util.Properties is a Hashtable (Map subtype) that must not be
    // instantiable from the wire.
    byte[] hostile =
        "{\"@class\":\"java.util.Properties\",\"x\":\"y\"}".getBytes(StandardCharsets.UTF_8);

    assertThrows(IOException.class, () -> SerializationUtils.deserialize(hostile));
  }

  @Test
  public void deserializeRejectsPackagePrefixSibling() {
    // The package prefixes must be dot-terminated: a type tag in a sibling package whose name
    // merely starts with "common.dto" must not match the allowlist. (The class does not exist, but
    // the validator must reject it BEFORE class resolution is attempted — the message names the
    // denied id, not a ClassNotFoundException.)
    byte[] hostile =
        "{\"@class\":\"common.dtoevil.Gadget\",\"x\":1}".getBytes(StandardCharsets.UTF_8);

    IOException ex = assertThrows(IOException.class, () -> SerializationUtils.deserialize(hostile));
    assertTrue(
        "should be a validator denial naming the id, was: " + ex.getMessage(),
        ex.getMessage() != null && ex.getMessage().contains("common.dtoevil.Gadget"));
  }

  @Test
  public void deserializeAcceptsProtocolCollections() {
    // The concrete collections the protocol actually emits stay deserializable.
    byte[] list = "[\"java.util.ArrayList\",[\"a\",\"b\"]]".getBytes(StandardCharsets.UTF_8);
    byte[] map = "{\"@class\":\"java.util.HashMap\",\"k\":\"v\"}".getBytes(StandardCharsets.UTF_8);

    assertTrue(deserialize(list) instanceof java.util.ArrayList);
    assertTrue(deserialize(map) instanceof java.util.HashMap);
  }

  // --- Frame size ceiling -----------------------------------------------------------------------

  @Test
  public void readFramedObjectRejectsOversizedFrame() throws IOException {
    // The 4-byte length prefix announces a payload past the client-inbound ceiling; the reader must
    // reject it on the length alone, before allocating a buffer of that size (a memory-exhaustion
    // DoS vector — here, a malicious host handing a client a huge frame).
    SocketChannel channel = channelAnnouncingLength(MAX_FRAME_BYTES + 1);

    IOException ex =
        assertThrows(IOException.class, () -> SerializationUtils.readFramedObject(channel));
    assertTrue(ex.getMessage().contains("Invalid object length"));
  }

  @Test
  public void readFramedObjectRejectsTheOldTenMegabyteFrame() throws IOException {
    // Regression pin for the P2-2 lowering: what the old 10 MB cap would have accepted is now
    // refused.
    SocketChannel channel = channelAnnouncingLength(10 * 1024 * 1024);

    IOException ex =
        assertThrows(IOException.class, () -> SerializationUtils.readFramedObject(channel));
    assertTrue(ex.getMessage().contains("Invalid object length"));
  }

  @Test
  public void readFramedObjectAcceptsACaseListSizedFrame() throws IOException {
    // The client cap is deliberately ABOVE the server-inbound 64 KB: the case-list DTO legitimately
    // runs to ~90 KB+. A 200 KB frame must pass the length check (it fails later on the truncated
    // body, not on the length guard), so normal play is unaffected.
    SocketChannel channel = channelAnnouncingLength(200 * 1024);

    IOException ex =
        assertThrows(IOException.class, () -> SerializationUtils.readFramedObject(channel));
    assertTrue(
        "a case-list-sized frame must pass the length check: " + ex.getMessage(),
        !ex.getMessage().contains("Invalid object length"));
  }

  @Test
  public void readFramedObjectRejectsNonPositiveLength() throws IOException {
    SocketChannel channel = channelAnnouncingLength(0);

    assertThrows(IOException.class, () -> SerializationUtils.readFramedObject(channel));
  }

  @Test
  public void readFramedObjectAcceptsLengthAtTheCeiling() throws IOException {
    // Exactly at the ceiling is allowed; the channel reports EOF after the length so we only assert
    // the size guard let it through (it fails later, on the truncated body, not on the length
    // check).
    SocketChannel channel = channelAnnouncingLength(MAX_FRAME_BYTES);

    IOException ex =
        assertThrows(IOException.class, () -> SerializationUtils.readFramedObject(channel));
    assertTrue(
        "the exact ceiling must pass the length check: " + ex.getMessage(),
        !ex.getMessage().contains("Invalid object length"));
  }

  /** A {@link SocketChannel} whose first read fills the 4-byte length prefix, then reports EOF. */
  private static SocketChannel channelAnnouncingLength(int announcedLength) throws IOException {
    SocketChannel channel = mock(SocketChannel.class);
    boolean[] lengthDelivered = {false};
    when(channel.read(any(ByteBuffer.class)))
        .thenAnswer(
            invocation -> {
              ByteBuffer buffer = invocation.getArgument(0);
              if (!lengthDelivered[0]) {
                lengthDelivered[0] = true;
                buffer.putInt(announcedLength);
                return 4;
              }
              return -1; // EOF: no body follows
            });
    return channel;
  }

  // --- helpers ----------------------------------------------------------------------------------

  private static byte[] serialize(java.io.Serializable object) {
    try {
      return SerializationUtils.serialize(object);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static Object deserialize(byte[] bytes) {
    try {
      Object value = SerializationUtils.deserialize(bytes);
      assertNotNull(value);
      return value;
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }
}

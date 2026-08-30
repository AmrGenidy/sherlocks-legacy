package common;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import java.io.EOFException;
import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class SerializationUtils {
  private static final ObjectMapper mapper;

  static {
    // Deny-by-default allowlist (security-pass issue 01). Package prefixes are dot-terminated so
    // sibling packages cannot match, and collections are enumerated as the exact concrete types
    // the protocol emits — never "any List/Map subtype", which would re-open the classpath to
    // collection-based deserialization gadgets.
    PolymorphicTypeValidator ptv =
        BasicPolymorphicTypeValidator.builder()
            .allowIfSubType("common.commands.")
            .allowIfSubType("common.dto.")
            .allowIfSubType("JsonDTO.")
            .allowIfSubType("java.util.ArrayList")
            .allowIfSubType("java.util.LinkedList")
            .allowIfSubType("java.util.HashMap")
            .allowIfSubType("java.util.LinkedHashMap")
            .allowIfSubType("java.util.HashSet")
            .allowIfSubType("java.util.LinkedHashSet")
            .build();

    mapper = new ObjectMapper();
    mapper.activateDefaultTyping(
        ptv, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
  }

  private SerializationUtils() {}

  public static byte[] serialize(Serializable object) throws IOException {
    return mapper.writeValueAsBytes(object);
  }

  public static Object deserialize(byte[] bytes) throws IOException {
    return mapper.readValue(bytes, Object.class);
  }

  public static void writeFramedObject(SocketChannel channel, Serializable object)
      throws IOException {
    byte[] objectBytes = serialize(object);
    int length = objectBytes.length;
    ByteBuffer buffer = ByteBuffer.allocate(4 + length);
    buffer.putInt(length);
    buffer.put(objectBytes);
    buffer.flip();

    while (buffer.hasRemaining()) {
      channel.write(buffer);
    }
  }

  public static Object readFramedObject(SocketChannel channel) throws IOException {
    ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
    int bytesRead = 0;
    while (bytesRead < 4) {
      int read = channel.read(lengthBuffer);
      if (read == -1) {
        if (bytesRead == 0) return null;
        throw new EOFException("Stream ended prematurely while reading object length.");
      }
      bytesRead += read;
    }

    lengthBuffer.flip();
    int objectLength = lengthBuffer.getInt();

    // Client-inbound cap (SECURITY_PLAN B/P2-2): this reader is the client's server->client path,
    // so
    // a malicious host cannot make a client allocate an oversized buffer. Lowered from 10 MB.
    if (objectLength <= 0 || objectLength > WireLimits.MAX_CLIENT_INBOUND_FRAME_BYTES) {
      throw new IOException("Invalid object length received: " + objectLength);
    }

    ByteBuffer objectBuffer = ByteBuffer.allocate(objectLength);
    bytesRead = 0;
    while (bytesRead < objectLength) {
      int read = channel.read(objectBuffer);
      if (read == -1) {
        throw new EOFException("Stream ended prematurely while reading object data.");
      }
      bytesRead += read;
    }

    return deserialize(objectBuffer.array());
  }
}

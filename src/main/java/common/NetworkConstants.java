package common;

public class NetworkConstants {
  public static final String DEFAULT_HOST = "localhost";
  public static final int DEFAULT_PORT = 8888;
  public static final int BUFFER_SIZE = 8192; // For network ByteBuffers (8KB)
  public static final int MAX_PLAYERS_PER_GAME = 2;

  public static final long SELECTOR_TIMEOUT = 1000; // 1 second

  // --- Abuse limits (security-pass issues 03/09) ---
  /** Hard cap on concurrent TCP connections; excess connects are closed at accept. */
  public static final int MAX_CONNECTIONS = 16;

  /** Outbound DTOs buffered per connection before a non-reading peer is dropped. */
  public static final int MAX_WRITE_QUEUE_DTOS = 1024;

  // --- Idle / slow-loris timeouts (SECURITY_PLAN B/P1-1) ---
  /**
   * A connection with no successful read for this long is reaped, freeing its slot. Generous, so a
   * player who is merely thinking mid-case is never dropped; it only sheds abandoned/silent
   * sockets.
   */
  public static final long IDLE_TIMEOUT_MS = 300_000; // 5 minutes

  /**
   * A connection that has begun a message (any length/body bytes) but not completed it within this
   * window is reaped. Short, because a legitimate client sends a whole framed message promptly;
   * this closes the "dangle a partial frame" slow-loris that the idle timeout alone would miss.
   */
  public static final long PARTIAL_FRAME_TIMEOUT_MS = 30_000; // 30 seconds

  // --- LAN Discovery ---
  public static final int DISCOVERY_PORT = 51515;
  public static final int DISCOVERY_INTERVAL_MS = 1000;

  // Private constructor to prevent instantiation
  private NetworkConstants() {}
}

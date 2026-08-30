package server;

/**
 * Pure decision for when a client connection has gone stale (SECURITY_PLAN B/P1-1). Kept free of
 * any I/O so the slow-loris / idle policy is directly unit-testable with an injected clock.
 *
 * <p>Two independent timers, either of which expires a connection:
 *
 * <ul>
 *   <li><b>Idle</b> — no successful read for {@code idleTimeoutMs} (an abandoned or silent socket).
 *   <li><b>Partial frame</b> — a message was started (length/body bytes arrived) but not completed
 *       within {@code partialFrameTimeoutMs} (a peer dribbling or withholding a frame to hold its
 *       slot). {@code messageStartedMillis == 0} means "no message in progress".
 * </ul>
 */
public final class ConnectionTimeouts {

  private ConnectionTimeouts() {}

  /** Sentinel for {@code messageStartedMillis}: no partially-received message in progress. */
  public static final long NO_MESSAGE_IN_PROGRESS = 0L;

  public static boolean isExpired(
      long now,
      long lastActivityMillis,
      long messageStartedMillis,
      long idleTimeoutMs,
      long partialFrameTimeoutMs) {
    if (now - lastActivityMillis > idleTimeoutMs) {
      return true;
    }
    return messageStartedMillis != NO_MESSAGE_IN_PROGRESS
        && now - messageStartedMillis > partialFrameTimeoutMs;
  }
}

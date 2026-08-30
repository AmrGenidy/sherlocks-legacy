package server;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongSupplier;

/**
 * Per-connection sliding-window counter for undecodable ("garbage") inbound frames (SECURITY_PLAN
 * B/P2-3). The existing {@link CommandRateLimiter} only throttles successfully-decoded messages; a
 * peer streaming frames that fail deserialization would otherwise evade it (each just draws a
 * single error reply). This bounds decode failures to {@link #MAX_FAILURES} within {@link
 * #WINDOW_MILLIS}; beyond that the connection should be dropped.
 *
 * <p>Not thread-safe by design: one instance per {@link ClientSession}, touched only from the
 * selector thread. Clock-injectable (millis) for tests.
 */
final class DecodeFailureLimiter {

  /** Garbage frames tolerated within the window before the connection is dropped. */
  static final int MAX_FAILURES = 20;

  static final long WINDOW_MILLIS = 10_000;

  private final LongSupplier clockMillis;
  private final Deque<Long> failures = new ArrayDeque<>();

  DecodeFailureLimiter(LongSupplier clockMillis) {
    this.clockMillis = clockMillis;
  }

  /**
   * Records one decode failure and returns {@code true} when the number of failures in the trailing
   * window now exceeds {@link #MAX_FAILURES} — the caller should then close the connection.
   */
  boolean recordFailureAndCheckExceeded() {
    long now = clockMillis.getAsLong();
    long cutoff = now - WINDOW_MILLIS;
    while (!failures.isEmpty() && failures.peekFirst() < cutoff) {
      failures.pollFirst();
    }
    failures.addLast(now);
    return failures.size() > MAX_FAILURES;
  }
}

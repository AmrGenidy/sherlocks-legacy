package server;

import java.util.function.LongSupplier;

/**
 * Token-bucket rate limiter for one client connection (security-pass issue 03).
 *
 * <p>The bucket starts full at {@link #BURST_CAPACITY} and refills at {@link #REFILL_PER_SECOND}.
 * The burst headroom covers legitimate rapid input (pinboard drags emit many small updates); a
 * flooding connection drains the bucket and its messages are dropped. Sustained abuse — {@link
 * #DISCONNECT_AFTER_CONSECUTIVE_DROPS} drops in a row without ever regaining a token — escalates
 * to disconnect.
 *
 * <p>Not thread-safe by design: each instance belongs to one {@link ClientSession} and is only
 * touched from the selector thread inside {@code handleRead()}.
 */
final class CommandRateLimiter {

  static final int BURST_CAPACITY = 60;
  static final double REFILL_PER_SECOND = 20.0;
  static final int DISCONNECT_AFTER_CONSECUTIVE_DROPS = 200;

  /** What to do with the message that just arrived. */
  enum Verdict {
    /** Within budget: process normally. */
    ALLOW,
    /** Over budget, first drop of this burst: discard and warn the client once. */
    DROP_AND_WARN,
    /** Over budget, already warned: discard silently. */
    DROP,
    /** Sustained flood: the connection should be closed. */
    DISCONNECT
  }

  private final LongSupplier nanoClock;
  private double tokens = BURST_CAPACITY;
  private long lastRefillNanos;
  private int consecutiveDrops;

  CommandRateLimiter() {
    this(System::nanoTime);
  }

  /** Clock-injectable for tests. */
  CommandRateLimiter(LongSupplier nanoClock) {
    this.nanoClock = nanoClock;
    this.lastRefillNanos = nanoClock.getAsLong();
  }

  Verdict onMessage() {
    refill();
    if (tokens >= 1.0) {
      tokens -= 1.0;
      consecutiveDrops = 0;
      return Verdict.ALLOW;
    }
    consecutiveDrops++;
    if (consecutiveDrops >= DISCONNECT_AFTER_CONSECUTIVE_DROPS) {
      return Verdict.DISCONNECT;
    }
    return consecutiveDrops == 1 ? Verdict.DROP_AND_WARN : Verdict.DROP;
  }

  private void refill() {
    long now = nanoClock.getAsLong();
    double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
    if (elapsedSeconds > 0) {
      tokens = Math.min(BURST_CAPACITY, tokens + elapsedSeconds * REFILL_PER_SECOND);
      lastRefillNanos = now;
    }
  }
}

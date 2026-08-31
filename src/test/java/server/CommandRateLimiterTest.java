package server;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The per-connection token bucket (security-pass issue 03), driven by a fake clock. */
public class CommandRateLimiterTest {

  /** A controllable nanosecond clock. */
  private static final class FakeClock {
    long nanos;

    void advanceMillis(long ms) {
      nanos += ms * 1_000_000L;
    }
  }

  @Test
  public void allowsTheFullBurstThenDrops() {
    FakeClock clock = new FakeClock();
    CommandRateLimiter limiter = new CommandRateLimiter(() -> clock.nanos);

    for (int i = 0; i < CommandRateLimiter.BURST_CAPACITY; i++) {
      assertEquals("message " + i, CommandRateLimiter.Verdict.ALLOW, limiter.onMessage());
    }
    assertEquals(CommandRateLimiter.Verdict.DROP_AND_WARN, limiter.onMessage());
    assertEquals(CommandRateLimiter.Verdict.DROP, limiter.onMessage());
  }

  @Test
  public void refillRestoresBudgetOverTime() {
    FakeClock clock = new FakeClock();
    CommandRateLimiter limiter = new CommandRateLimiter(() -> clock.nanos);
    for (int i = 0; i < CommandRateLimiter.BURST_CAPACITY; i++) {
      limiter.onMessage();
    }
    assertEquals(CommandRateLimiter.Verdict.DROP_AND_WARN, limiter.onMessage());

    clock.advanceMillis(1000); // refills REFILL_PER_SECOND tokens

    for (int i = 0; i < (int) CommandRateLimiter.REFILL_PER_SECOND; i++) {
      assertEquals("refilled message " + i, CommandRateLimiter.Verdict.ALLOW, limiter.onMessage());
    }
    assertEquals(CommandRateLimiter.Verdict.DROP_AND_WARN, limiter.onMessage());
  }

  @Test
  public void sustainedFloodEscalatesToDisconnect() {
    FakeClock clock = new FakeClock();
    CommandRateLimiter limiter = new CommandRateLimiter(() -> clock.nanos);
    for (int i = 0; i < CommandRateLimiter.BURST_CAPACITY; i++) {
      limiter.onMessage();
    }

    CommandRateLimiter.Verdict last = null;
    for (int i = 0; i < CommandRateLimiter.DISCONNECT_AFTER_CONSECUTIVE_DROPS; i++) {
      last = limiter.onMessage();
    }
    assertEquals(CommandRateLimiter.Verdict.DISCONNECT, last);
  }

  @Test
  public void anAllowedMessageResetsTheDropStreak() {
    FakeClock clock = new FakeClock();
    CommandRateLimiter limiter = new CommandRateLimiter(() -> clock.nanos);
    for (int i = 0; i < CommandRateLimiter.BURST_CAPACITY; i++) {
      limiter.onMessage();
    }
    for (int i = 0; i < CommandRateLimiter.DISCONNECT_AFTER_CONSECUTIVE_DROPS - 1; i++) {
      limiter.onMessage();
    }

    clock.advanceMillis(100); // 2 tokens back
    assertEquals(CommandRateLimiter.Verdict.ALLOW, limiter.onMessage());

    // The next over-budget message starts a NEW burst (warn again), not a disconnect.
    assertEquals(CommandRateLimiter.Verdict.ALLOW, limiter.onMessage());
    assertEquals(CommandRateLimiter.Verdict.DROP_AND_WARN, limiter.onMessage());
  }
}

package server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure policy tests for the idle / partial-frame timeout decision (SECURITY_PLAN B/P1-1). */
public class ConnectionTimeoutsTest {

  private static final long IDLE = 300_000;
  private static final long FRAME = 30_000;
  private static final long NONE = ConnectionTimeouts.NO_MESSAGE_IN_PROGRESS;

  @Test
  public void freshConnectionIsNotExpired() {
    assertFalse(ConnectionTimeouts.isExpired(1000, 1000, NONE, IDLE, FRAME));
  }

  @Test
  public void idleAtExactBoundaryIsNotExpired() {
    assertFalse(ConnectionTimeouts.isExpired(1000 + IDLE, 1000, NONE, IDLE, FRAME));
  }

  @Test
  public void idleBeyondTimeoutIsExpired() {
    assertTrue(ConnectionTimeouts.isExpired(1000 + IDLE + 1, 1000, NONE, IDLE, FRAME));
  }

  @Test
  public void partialFrameBeyondTimeoutIsExpiredEvenWhenIdleClockIsFresh() {
    // Last read was "just now" (idle timer fresh), but a message began FRAME+1 ago and never
    // completed — the slow-loris case the idle timer alone would miss.
    long now = 1_000_000;
    assertTrue(ConnectionTimeouts.isExpired(now, now, now - (FRAME + 1), IDLE, FRAME));
  }

  @Test
  public void partialFrameWithinTimeoutIsNotExpired() {
    long now = 1_000_000;
    assertFalse(ConnectionTimeouts.isExpired(now, now, now - (FRAME - 1), IDLE, FRAME));
  }
}

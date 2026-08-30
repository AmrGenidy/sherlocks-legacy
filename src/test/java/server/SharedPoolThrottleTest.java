package server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

/** Pure per-player shared-pool throttle policy (SECURITY_PLAN B/P2-5). */
public class SharedPoolThrottleTest {

  @Test
  public void allowsANormalBurstThenThrottlesSpam() {
    SharedPoolThrottle throttle = new SharedPoolThrottle(30, 10_000, () -> 5_000L);
    for (int i = 0; i < 30; i++) {
      assertTrue("action " + (i + 1) + " within budget", throttle.tryAcquire("p1"));
    }
    assertFalse("the 31st action in the window is refused", throttle.tryAcquire("p1"));
  }

  @Test
  public void budgetIsPerPlayer() {
    SharedPoolThrottle throttle = new SharedPoolThrottle(2, 10_000, () -> 5_000L);
    assertTrue(throttle.tryAcquire("p1"));
    assertTrue(throttle.tryAcquire("p1"));
    assertFalse("p1 is now throttled", throttle.tryAcquire("p1"));
    // p2 has its own independent budget — one peer's spam never blocks the other.
    assertTrue(throttle.tryAcquire("p2"));
    assertTrue(throttle.tryAcquire("p2"));
    assertFalse(throttle.tryAcquire("p2"));
  }

  @Test
  public void budgetRefillsAsTheWindowSlides() {
    AtomicLong now = new AtomicLong(0);
    SharedPoolThrottle throttle = new SharedPoolThrottle(2, 10_000, now::get);
    assertTrue(throttle.tryAcquire("p1"));
    assertTrue(throttle.tryAcquire("p1"));
    assertFalse(throttle.tryAcquire("p1"));
    now.addAndGet(10_001); // the earlier actions age out of the window
    assertTrue("budget is available again after the window slides", throttle.tryAcquire("p1"));
  }
}

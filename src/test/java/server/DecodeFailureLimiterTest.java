package server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

/** Pure sliding-window policy for undecodable-frame throttling (SECURITY_PLAN B/P2-3). */
public class DecodeFailureLimiterTest {

  @Test
  public void toleratesUpToThresholdInWindowThenTrips() {
    DecodeFailureLimiter limiter = new DecodeFailureLimiter(() -> 1000L);
    for (int i = 0; i < DecodeFailureLimiter.MAX_FAILURES; i++) {
      assertFalse(
          "failure " + (i + 1) + " is within budget", limiter.recordFailureAndCheckExceeded());
    }
    assertTrue("one past the threshold trips", limiter.recordFailureAndCheckExceeded());
  }

  @Test
  public void failuresSpreadBeyondTheWindowNeverTrip() {
    AtomicLong now = new AtomicLong(0);
    DecodeFailureLimiter limiter = new DecodeFailureLimiter(now::get);
    // One failure per second for a long time: old ones fall out of the 10s window, so the count in
    // any window stays well under the threshold — a slow trickle of corrupt frames is tolerated.
    for (int i = 0; i < 100; i++) {
      now.set(i * 1000L);
      assertFalse(limiter.recordFailureAndCheckExceeded());
    }
  }
}

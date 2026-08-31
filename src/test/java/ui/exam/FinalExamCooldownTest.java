package ui.exam;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Unit tests for the Final Exam anti-abuse cooldown (deterministic via an injected clock). */
public class FinalExamCooldownTest {

  /** A mutable fake clock. */
  private static final class Clock {
    long now = 0L;
  }

  @Test
  public void threeQuickSubmissionsFreezeForFiveMinutesThenRestore() {
    Clock clock = new Clock();
    FinalExamCooldown cooldown = new FinalExamCooldown(() -> clock.now);

    assertFalse("1st submission is fine", cooldown.recordSubmission());
    clock.now += 1_000;
    assertFalse("2nd submission is fine", cooldown.recordSubmission());
    clock.now += 1_000;
    assertTrue("3rd submission within 60s trips the freeze", cooldown.recordSubmission());
    assertTrue(cooldown.isFrozen());
    assertEquals(FinalExamCooldown.FREEZE_MS, cooldown.remainingMillis());

    // Still frozen a millisecond before the cooldown elapses.
    clock.now += FinalExamCooldown.FREEZE_MS - 1;
    assertTrue(cooldown.isFrozen());

    // Access is restored once it elapses.
    clock.now += 1;
    assertFalse(cooldown.isFrozen());
    assertEquals(0L, cooldown.remainingMillis());
  }

  @Test
  public void aSingleLegitimateSolveIsNeverFrozen() {
    Clock clock = new Clock();
    FinalExamCooldown cooldown = new FinalExamCooldown(() -> clock.now);

    assertFalse(cooldown.recordSubmission());
    assertFalse(cooldown.isFrozen());

    // Even much later, one submission never freezes.
    clock.now += FinalExamCooldown.WINDOW_MS * 10;
    assertFalse(cooldown.isFrozen());
  }

  @Test
  public void submissionsSpacedBeyondTheWindowDoNotFreeze() {
    Clock clock = new Clock();
    FinalExamCooldown cooldown = new FinalExamCooldown(() -> clock.now);

    for (int i = 0; i < 5; i++) {
      assertFalse("spaced-out submission #" + i + " must not freeze", cooldown.recordSubmission());
      clock.now += FinalExamCooldown.WINDOW_MS + 1; // each falls outside the previous window
    }
    assertFalse(cooldown.isFrozen());
  }

  @Test
  public void resetClearsAnActiveFreeze() {
    Clock clock = new Clock();
    FinalExamCooldown cooldown = new FinalExamCooldown(() -> clock.now);

    cooldown.recordSubmission();
    cooldown.recordSubmission();
    assertTrue(cooldown.recordSubmission());
    assertTrue(cooldown.isFrozen());

    cooldown.reset();
    assertFalse("reset (new case/session) clears the freeze", cooldown.isFrozen());
    assertEquals(0L, cooldown.remainingMillis());

    // And counting starts fresh afterwards.
    assertFalse(cooldown.recordSubmission());
  }
}

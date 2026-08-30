package ui.exam;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongSupplier;

/**
 * Anti-abuse cooldown for the Final Exam. Tracks recent exam-submission timestamps: if the player
 * submits the exam more than {@link #MAX_SUBMISSIONS} times within {@link #WINDOW_MS}, the exam is
 * frozen for {@link #FREEZE_MS} (submit + the Final exam button are locked, with a live countdown).
 *
 * <p>Pure, UI-free and clock-injectable so it is deterministically unit-testable. A single
 * legitimate solve records one submission and is never frozen. {@link #reset()} clears everything
 * between cases/sessions.
 */
public final class FinalExamCooldown {

  /** More than this many submissions within the window trips the freeze. */
  static final int MAX_SUBMISSIONS = 2;

  /** Sliding window over which submissions are counted (60s). */
  static final long WINDOW_MS = 60_000L;

  /** How long the exam stays frozen once the threshold is exceeded (5 minutes). */
  static final long FREEZE_MS = 5L * 60_000L;

  private final LongSupplier clock; // current time in millis
  private final Deque<Long> submissions = new ArrayDeque<>();
  private long frozenUntil = 0L;

  public FinalExamCooldown() {
    this(System::currentTimeMillis);
  }

  /** Package-private clock-injecting constructor for deterministic tests. */
  FinalExamCooldown(LongSupplier clock) {
    this.clock = clock;
  }

  /**
   * Records one exam submission and trips the freeze when too many land within the window.
   *
   * @return true if the exam is frozen after this submission
   */
  public boolean recordSubmission() {
    long now = clock.getAsLong();
    // Already frozen: a submission during the lockout doesn't extend it (submit is disabled anyway).
    if (isFrozen()) {
      return true;
    }
    submissions.addLast(now);
    while (!submissions.isEmpty() && now - submissions.peekFirst() > WINDOW_MS) {
      submissions.removeFirst();
    }
    if (submissions.size() > MAX_SUBMISSIONS) {
      frozenUntil = now + FREEZE_MS;
      submissions.clear();
    }
    return isFrozen();
  }

  /** True while the exam is locked out. */
  public boolean isFrozen() {
    return remainingMillis() > 0;
  }

  /** Milliseconds remaining on the freeze, or 0 when not frozen. */
  public long remainingMillis() {
    return Math.max(0L, frozenUntil - clock.getAsLong());
  }

  /** Clears submission history and any active freeze (call between cases/sessions). */
  public void reset() {
    submissions.clear();
    frozenUntil = 0L;
  }
}

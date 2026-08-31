package engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import Core.Suspect.SuspectState;
import common.commands.ContradictCommand;
import common.interfaces.GameActionContext;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Command-cooldown contract across both contexts: combine and contradict lock after three
 * consecutive failures, a success resets the streak, and a locked command is blocked.
 *
 * <p>The five-minute expiry itself is driven by {@code System.currentTimeMillis()} with no
 * injectable clock, so true expiry-after-timeout is not unit-testable here; we instead pin the
 * report/lock/reset logic and the "remaining seconds" accounting, which are the parts a refactor
 * can break. The lock threshold ({@value #FAILURES_TO_LOCK}) and ceiling are part of the pinned
 * contract.
 */
@RunWith(Parameterized.class)
public class CooldownContractTest {

  private static final int FAILURES_TO_LOCK = 3;
  // Combine and contradict both lock for 60 seconds after three consecutive failures.
  private static final long MAX_COOLDOWN_SECONDS = 60;

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> contexts() {
    return ContextHarnessFactory.both();
  }

  @Parameterized.Parameter public ContextHarnessFactory factory;

  private ContextHarness sapphire() {
    return factory.start(EngineFixtures.sapphire());
  }

  // --- combine cooldown ---------------------------------------------------

  @Test
  public void combineStartsOffCooldown() {
    GameActionContext c = sapphire().context();
    assertFalse(c.isCombineOnCooldown());
    assertEquals(0, c.getCombineCooldownRemaining());
  }

  @Test
  public void combineLocksAfterThreeFailures() {
    GameActionContext c = sapphire().context();
    for (int i = 0; i < FAILURES_TO_LOCK; i++) {
      assertFalse("should still be unlocked before the threshold", c.isCombineOnCooldown());
      c.reportCombineFailure();
    }
    assertTrue(c.isCombineOnCooldown());
    long remaining = c.getCombineCooldownRemaining();
    assertTrue("remaining must be a positive countdown", remaining > 0);
    assertTrue(
        "remaining must not exceed the configured window", remaining <= MAX_COOLDOWN_SECONDS);
  }

  @Test
  public void combineSuccessResetsTheFailureStreak() {
    GameActionContext c = sapphire().context();
    c.reportCombineFailure();
    c.reportCombineFailure();
    c.reportCombineSuccess(); // streak back to zero
    c.reportCombineFailure();
    c.reportCombineFailure();
    assertFalse("two failures after a reset must not lock", c.isCombineOnCooldown());
  }

  // --- contradict cooldown ------------------------------------------------

  @Test
  public void contradictStartsOffCooldown() {
    GameActionContext c = sapphire().context();
    assertFalse(c.isContradictOnCooldown());
    assertEquals(0, c.getContradictCooldownRemaining());
  }

  @Test
  public void contradictLocksAfterThreeFailures() {
    GameActionContext c = sapphire().context();
    for (int i = 0; i < FAILURES_TO_LOCK; i++) {
      assertFalse(c.isContradictOnCooldown());
      c.reportContradictFailure();
    }
    assertTrue(c.isContradictOnCooldown());
    long remaining = c.getContradictCooldownRemaining();
    assertTrue(remaining > 0 && remaining <= MAX_COOLDOWN_SECONDS);
  }

  @Test
  public void contradictSuccessResetsTheFailureStreak() {
    GameActionContext c = sapphire().context();
    c.reportContradictFailure();
    c.reportContradictFailure();
    c.reportContradictSuccess();
    c.reportContradictFailure();
    c.reportContradictFailure();
    assertFalse(c.isContradictOnCooldown());
  }

  // --- integration: a locked contradict blocks even a valid contradiction --

  @Test
  public void lockedContradictBlocksAnOtherwiseValidContradiction() {
    ContextHarness h = sapphire();
    h.bringSuspectToPlayer("LordAshworth");

    // Three failed contradictions (wrong evidence) drive the lock through the real command.
    for (int i = 0; i < FAILURES_TO_LOCK; i++) {
      h.execute(new ContradictCommand("LordAshworth", "shattered_glass"));
    }
    assertTrue(h.context().isContradictOnCooldown());

    // 'cigar_stub' WOULD crack LIE->TRUTH, but the lock must short-circuit it.
    h.execute(new ContradictCommand("LordAshworth", "cigar_stub"));

    assertEquals(SuspectState.LIE, h.suspect("LordAshworth").getCurrentState());
  }
}

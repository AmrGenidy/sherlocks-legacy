package engine;

import static org.junit.Assert.assertEquals;

import common.commands.DeduceCommand;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * The deduction-penalty "heal" as one behavioural contract across both contexts.
 *
 * <p>An unfunded deduction charges the session deduction count; a subsequent token award (from a
 * successful contradiction or combine) repays that penalty instead of granting the token, and the
 * repayment is observable through {@link
 * common.interfaces.GameActionContext#getSessionDeduceCount()} — so the final Rank Tier benefits
 * identically in single-player and multiplayer.
 *
 * <p>Replaces {@code DeductionPenaltyHealDivergenceTest}, which pinned the pre-unification
 * divergence where single-player read a different counter and the heal was invisible (decision
 * recorded in {@code .scratch/engine-test-suite/issues/04-deduction-heal-divergence.md} and {@code
 * docs/adr/0001-shared-game-engine-core.md}).
 */
@RunWith(Parameterized.class)
public class DeductionPenaltyHealContractTest {

  private static final String GLASS = "shattered_glass";

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> contexts() {
    return ContextHarnessFactory.both();
  }

  @Parameterized.Parameter public ContextHarnessFactory factory;

  @Test
  public void rewardedTokenRepaysAnOutstandingDeductionPenalty() {
    ContextHarness h = factory.start(EngineFixtures.sapphire());

    h.execute(new DeduceCommand(GLASS)); // sapphire starts with 0 tokens -> unfunded
    assertEquals(1, h.context().getSessionDeduceCount());

    h.context().awardInsightToken(); // heals the penalty instead of granting a token

    assertEquals(
        "the heal is visible through getSessionDeduceCount()",
        0,
        h.context().getSessionDeduceCount());
    assertEquals("no token granted while a penalty was outstanding", 0, h.tokens());
  }

  @Test
  public void rewardedTokenIsGrantedWhenNoPenaltyIsOutstanding() {
    ContextHarness h = factory.start(EngineFixtures.sapphire());

    h.context().awardInsightToken();

    assertEquals(0, h.context().getSessionDeduceCount());
    assertEquals("with no penalty to heal, the award grants a token", 1, h.tokens());
  }
}

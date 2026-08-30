package engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import common.commands.CombineCommand;
import common.commands.ContradictCommand;
import common.commands.DeduceCommand;
import common.commands.ExamineCommand;
import common.commands.MoveCommand;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * The insight-token economy as one behavioural contract across both context implementations:
 * initialisation from the case file, spending, reward, and insufficient-balance rejection.
 *
 * <p>The deduction-penalty "heal" interaction (where {@code awardInsightToken} repays a spent rank
 * budget instead of granting a token) is NOT pinned here because the two contexts genuinely diverge
 * on it — that divergence is documented in {@link DeductionPenaltyHealDivergenceTest} and filed as
 * {@code .scratch/engine-test-suite/issues/04-...}.
 */
@RunWith(Parameterized.class)
public class TokenEconomyContractTest {

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> contexts() {
    return ContextHarnessFactory.both();
  }

  @Parameterized.Parameter public ContextHarnessFactory factory;

  private ContextHarness sapphire() {
    return factory.start(EngineFixtures.sapphire());
  }

  private ContextHarness fsm() {
    return factory.start(EngineFixtures.fsm());
  }

  // --- initialisation -----------------------------------------------------

  @Test
  public void startingBalanceComesFromTheCaseFile() {
    assertEquals("sapphire declares startingInsightTokens=0", 0, sapphire().tokens());
    assertEquals("fsm fixture declares startingInsightTokens=3", 3, fsm().tokens());
  }

  // --- spend --------------------------------------------------------------

  @Test
  public void spendingDecrementsTheBalance() {
    ContextHarness h = fsm(); // starts with 3
    assertTrue(h.context().trySpendInsightToken());
    assertEquals(2, h.tokens());
    assertTrue(h.context().trySpendInsightTokens(2));
    assertEquals(0, h.tokens());
  }

  @Test
  public void deductionSpendsATokenWhenFundedAndChargesRankBudgetWhenNot() {
    ContextHarness h = fsm(); // 3 tokens
    h.execute(new DeduceCommand("knife"));
    assertEquals("a funded deduction spends a token, not rank budget", 2, h.tokens());
    assertEquals(0, h.context().getSessionDeduceCount());

    // Drain the remaining tokens, then a deduction must fall back to the rank budget.
    assertTrue(h.context().trySpendInsightTokens(2));
    assertEquals(0, h.tokens());
    h.execute(new DeduceCommand("letter"));
    assertEquals(0, h.tokens());
    assertEquals(
        "an unfunded deduction increases the deduction count",
        1,
        h.context().getSessionDeduceCount());
  }

  // --- reward -------------------------------------------------------------

  @Test
  public void contradictionRewardsAToken() {
    ContextHarness h = sapphire(); // 0 tokens
    h.execute(new MoveCommand("east"));
    h.execute(new ExamineCommand("cigar_stub"));
    h.bringSuspectToPlayer("LordAshworth");

    h.execute(new ContradictCommand("LordAshworth", "cigar_stub"));

    assertEquals(1, h.tokens());
  }

  @Test
  public void combineRewardsItsConfiguredTokens() {
    ContextHarness h = sapphire(); // 0 tokens, combine rule grants 1
    h.execute(new ExamineCommand("shattered_glass"));
    h.execute(new MoveCommand("east"));
    h.execute(new ExamineCommand("cigar_stub"));

    h.execute(new CombineCommand("shattered_glass", "cigar_stub"));

    assertEquals(1, h.tokens());
  }

  // --- insufficient balance ----------------------------------------------

  @Test
  public void spendingFailsWhenBalanceIsEmpty() {
    ContextHarness h = sapphire(); // 0 tokens
    assertFalse(h.context().trySpendInsightToken());
    assertEquals(0, h.tokens());
  }

  @Test
  public void multiTokenSpendFailsWhenBalanceTooLow() {
    ContextHarness h = fsm(); // 3 tokens
    assertTrue(h.context().trySpendInsightTokens(2));
    assertEquals(1, h.tokens());

    assertFalse("cannot spend 2 with only 1 left", h.context().trySpendInsightTokens(2));
    assertEquals("a failed spend must not change the balance", 1, h.tokens());
  }
}

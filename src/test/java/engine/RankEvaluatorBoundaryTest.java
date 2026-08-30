package engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import Core.Rank;
import Core.util.RankEvaluator;
import JsonDTO.CaseData;
import org.junit.Test;

/**
 * Boundary and fallback behaviour of {@link RankEvaluator}, asserted against the REAL ranking tiers
 * of bundled cases (loaded through {@code CaseLoader}) rather than hand-built tier objects.
 *
 * <p>Sapphire tiers: Sherlock Holmes (maxDeductions 1), Dr. Watson (maxDeductions 4), Curious
 * Observer (default). FSM fixture tiers: Ace Detective (maxDeductions 0), Rookie (default).
 */
public class RankEvaluatorBoundaryTest {

  private final CaseData sapphire = EngineFixtures.sapphire();
  private final CaseData fsm = EngineFixtures.fsm();

  @Test
  public void zeroDeductionsEarnsTheTopTier() {
    Rank rank = RankEvaluator.evaluate(0, sapphire);
    assertEquals("Sherlock Holmes", rank.getRankName());
    assertFalse(rank.isDefault());
  }

  @Test
  public void deductionCountAtTierMaxStillEarnsThatTier() {
    assertEquals("Sherlock Holmes", RankEvaluator.evaluate(1, sapphire).getRankName());
    assertEquals("Dr. Watson", RankEvaluator.evaluate(4, sapphire).getRankName());
  }

  @Test
  public void deductionCountJustOverATierFallsToTheNextTier() {
    assertEquals("Dr. Watson", RankEvaluator.evaluate(2, sapphire).getRankName());
  }

  @Test
  public void exceedingEveryTierFallsBackToTheDefaultTier() {
    Rank rank = RankEvaluator.evaluate(5, sapphire);
    assertEquals("Curious Observer", rank.getRankName());
    assertTrue("the configured default tier must be flagged as default", rank.isDefault());

    // Arbitrarily large counts also resolve to the default tier.
    assertEquals("Curious Observer", RankEvaluator.evaluate(100, sapphire).getRankName());
  }

  @Test
  public void winningMessageIsTierSpecific() {
    assertEquals(
        "A flawless investigation, Holmes. You have outdone yourself!",
        RankEvaluator.evaluate(1, sapphire).getWinningStatement());
    assertEquals(
        "A solid deduction, my friend. Well done!",
        RankEvaluator.evaluate(3, sapphire).getWinningStatement());
    assertEquals(
        "You reached the truth, but there's room to sharpen your methods.",
        RankEvaluator.evaluate(9, sapphire).getWinningStatement());
  }

  @Test
  public void aZeroMaxDeductionsTierMatchesOnlyAFlawlessRun() {
    // FSM fixture: Ace Detective requires maxDeductions == 0.
    assertEquals("Ace Detective", RankEvaluator.evaluate(0, fsm).getRankName());
    Rank rookie = RankEvaluator.evaluate(1, fsm);
    assertEquals("Rookie", rookie.getRankName());
    assertTrue(rookie.isDefault());
  }
}

package engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import common.commands.DeduceCommand;
import common.commands.MoveCommand;
import common.dto.ExamResultDTO;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * The final-exam flow as one behavioural contract across both contexts: start conditions, answer
 * submission, scoring, and rank assignment.
 *
 * <p>Driven entirely through the shared {@code GameActionContext} surface ({@code
 * canStartFinalExam}, {@code startExamProcess}, {@code processSubmitQuestionAnswer}) and the
 * harness's uniform {@link ContextHarness#lastExamResult()}. Where exam *start gating* itself
 * diverges between the contexts, that is pinned separately in {@link
 * ExamStartGatingDivergenceTest}.
 *
 * <p>Sapphire has one question; the correct combination is {@code slot1=s1_opt1, slot2=s2_opt1}.
 */
@RunWith(Parameterized.class)
public class FinalExamFlowContractTest {

  private static final Map<String, String> CORRECT = Map.of("slot1", "s1_opt1", "slot2", "s2_opt1");
  private static final Map<String, String> WRONG = Map.of("slot1", "s1_opt2", "slot2", "s2_opt3");

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> contexts() {
    return ContextHarnessFactory.both();
  }

  @Parameterized.Parameter public ContextHarnessFactory factory;

  private ContextHarness sapphire() {
    return factory.start(EngineFixtures.sapphire());
  }

  // --- start conditions ---------------------------------------------------

  @Test
  public void examCanStartOnceTheCaseHasStarted() {
    ContextHarness h = sapphire();
    assertTrue(h.context().canStartFinalExam(h.playerId()));
  }

  @Test
  public void examCannotStartBeforeTheCaseHasStarted() {
    ContextHarness h = factory.startUnstarted(EngineFixtures.sapphire());
    assertFalse(h.context().canStartFinalExam(h.playerId()));
  }

  // --- submission + scoring ----------------------------------------------

  @Test
  public void correctAnswersSolveTheCaseWithAPerfectScore() {
    ContextHarness h = sapphire();

    h.context().startExamProcess(h.playerId());
    h.context().processSubmitQuestionAnswer(h.playerId(), 0, CORRECT);

    ExamResultDTO result = h.lastExamResult();
    assertNotNull("an exam result should have been produced", result);
    assertEquals(1, result.getScore());
    assertEquals(1, result.getTotalQuestions());
    assertTrue(result.isCaseSolved());
  }

  @Test
  public void wrongAnswersDoNotSolveTheCase() {
    ContextHarness h = sapphire();

    h.context().startExamProcess(h.playerId());
    h.context().processSubmitQuestionAnswer(h.playerId(), 0, WRONG);

    ExamResultDTO result = h.lastExamResult();
    assertNotNull(result);
    assertEquals(0, result.getScore());
    assertFalse(result.isCaseSolved());
    assertNull("no winning message when the case is not solved", result.getWinningMessage());
  }

  // --- rank assignment ----------------------------------------------------

  @Test
  public void perfectRunWithNoDeductionsEarnsTheTopRank() {
    ContextHarness h = sapphire();

    h.context().startExamProcess(h.playerId());
    h.context().processSubmitQuestionAnswer(h.playerId(), 0, CORRECT);

    ExamResultDTO result = h.lastExamResult();
    assertEquals("Sherlock Holmes", result.getFinalRank());
    assertEquals(
        "A flawless investigation, Holmes. You have outdone yourself!", result.getWinningMessage());
  }

  @Test
  public void spentDeductionsLowerTheAssignedRank() {
    ContextHarness h = sapphire();
    // Two unfunded deductions push the rank budget from Sherlock (<=1) down to Dr. Watson (<=4).
    h.execute(new DeduceCommand("shattered_glass"));
    h.execute(new MoveCommand("east"));
    h.execute(new DeduceCommand("cigar_stub"));
    assertEquals(2, h.context().getSessionDeduceCount());

    h.context().startExamProcess(h.playerId());
    h.context().processSubmitQuestionAnswer(h.playerId(), 0, CORRECT);

    ExamResultDTO result = h.lastExamResult();
    assertTrue(result.isCaseSolved());
    assertEquals("Dr. Watson", result.getFinalRank());
  }
}

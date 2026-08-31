package engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Multi-question Final Exam advance (.scratch/mp-exam-question-advance): a two-question exam scores
 * end-to-end only when submits carry advancing, server-authoritative indices. Pins the engine half
 * (already correct) in BOTH contexts: Q1's answer advances to Q2 rather than scoring, a stale-index
 * resubmit is dropped by the guard, and Q2's answer scores. This is the server-side complement to
 * the client receive-path fix ({@code client.GameClientExamAdvanceTest}).
 */
@RunWith(Parameterized.class)
public class MultiQuestionExamContractTest {

  private static final Map<String, String> Q1_CORRECT = Map.of("slot1", "q1_butler");
  private static final Map<String, String> Q2_CORRECT = Map.of("slot1", "q2_knife");

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> contexts() {
    return ContextHarnessFactory.both();
  }

  @Parameterized.Parameter public ContextHarnessFactory factory;

  private ContextHarness twoQuestionExam() {
    return factory.start(EngineFixtures.twoQuestionExam());
  }

  @Test
  public void twoQuestionExamScoresOnlyWithAdvancingIndices() {
    ContextHarness h = twoQuestionExam();
    h.context().startExamProcess(h.playerId());
    assertTrue("the exam is active after start", h.context().isExamActive());
    assertNull("no result before all questions are answered", h.lastExamResult());

    // Q1 (index 0): a correct answer advances to Q2 rather than scoring the exam.
    h.context().processSubmitQuestionAnswer(h.playerId(), 0, Q1_CORRECT);
    assertTrue("a two-question exam stays active after Q1", h.context().isExamActive());
    assertNull("scoring must wait for Q2", h.lastExamResult());

    // A stale-index resubmit (index 0, now that the engine is on index 1) is dropped by the guard.
    h.context().processSubmitQuestionAnswer(h.playerId(), 0, Q1_CORRECT);
    assertTrue("a stale-index submit must not score or end the exam", h.context().isExamActive());
    assertNull(h.lastExamResult());

    // Q2 (index 1): the matching index scores the exam end-to-end.
    h.context().processSubmitQuestionAnswer(h.playerId(), 1, Q2_CORRECT);
    assertFalse("scoring ends the exam", h.context().isExamActive());
    assertNotNull("the exam scores once every question is answered", h.lastExamResult());
    assertEquals("both questions correct → score 2", 2, h.lastExamResult().getScore());
    assertTrue("a retry start is allowed once scored", h.context().canStartFinalExam(h.playerId()));
  }
}

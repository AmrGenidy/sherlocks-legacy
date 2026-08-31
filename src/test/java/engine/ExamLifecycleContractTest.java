package engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import common.dto.FinalExamQuestionDTO;
import common.dto.InitiateFinalExamDTO;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import singleplayer.GameContextSinglePlayer;

/**
 * The unified final-exam lifecycle across both contexts (issue 06 resolution, ADR-0001): an exam
 * cannot be (re)started while one is active, becomes startable again after scoring, announces
 * itself with a typed {@link InitiateFinalExamDTO} in both modes, and the scored result survives
 * until the next exam start.
 *
 * <p>Replaces {@code ExamStartGatingDivergenceTest}, which pinned the pre-unification divergence
 * (server ignored {@code examActive}; single-player nulled its result right after scoring).
 */
@RunWith(Parameterized.class)
public class ExamLifecycleContractTest {

  private static final Map<String, String> CORRECT = Map.of("slot1", "s1_opt1", "slot2", "s2_opt1");

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> contexts() {
    return ContextHarnessFactory.both();
  }

  @Parameterized.Parameter public ContextHarnessFactory factory;

  private ContextHarness sapphire() {
    return factory.start(EngineFixtures.sapphire());
  }

  @Test
  public void examCannotBeRestartedWhileOneIsActive() {
    ContextHarness h = sapphire();
    assertTrue(h.context().canStartFinalExam(h.playerId()));

    h.context().startExamProcess(h.playerId());

    assertFalse(
        "an active exam blocks a fresh start in both modes",
        h.context().canStartFinalExam(h.playerId()));
  }

  @Test
  public void examBecomesStartableAgainAfterScoring() {
    ContextHarness h = sapphire();
    h.context().startExamProcess(h.playerId());
    h.context().processSubmitQuestionAnswer(h.playerId(), 0, CORRECT);

    assertTrue(
        "after scoring, a retry start is allowed", h.context().canStartFinalExam(h.playerId()));
  }

  @Test
  public void examStartIsAnnouncedWithATypedEvent() {
    ContextHarness h = sapphire();

    h.context().startExamProcess(h.playerId());

    assertTrue(
        "both modes announce the exam with InitiateFinalExamDTO",
        h.playerResponses().stream().anyMatch(d -> d instanceof InitiateFinalExamDTO));
  }

  @Test
  public void examAnnouncementDoesNotLeakTheAnswerKey() {
    // Security-pass issue 04: the InitiateFinalExamDTO broadcast must carry prompts and slots
    // only — the correct combinations stay server-side (scoring is engine-owned).
    ContextHarness h = sapphire();

    h.context().startExamProcess(h.playerId());

    InitiateFinalExamDTO announcement =
        h.playerResponses().stream()
            .filter(d -> d instanceof InitiateFinalExamDTO)
            .map(d -> (InitiateFinalExamDTO) d)
            .findFirst()
            .orElseThrow();
    for (FinalExamQuestionDTO question : announcement.getFinalExam().getQuestions()) {
      assertNull(
          "the answer key must never reach clients", question.getCorrectCombination());
      assertNotNull("slots must survive sanitization", question.getSlots());
      assertNotNull("prompt must survive sanitization", question.getQuestionPrompt());
    }

    // Scoring still runs against the engine's own (unsanitized) case data.
    h.context().processSubmitQuestionAnswer(h.playerId(), 0, CORRECT);
    assertEquals(1, h.lastExamResult().getScore());
  }

  @Test
  public void singlePlayerLastResultSurvivesScoring() {
    ContextHarness h = sapphire();
    if (!(h.context() instanceof GameContextSinglePlayer sp)) {
      return; // the server exposes the result only as a broadcast, covered by lastExamResult()
    }

    h.context().startExamProcess(h.playerId());
    h.context().processSubmitQuestionAnswer(h.playerId(), 0, CORRECT);

    assertNotNull(
        "the scored result must be readable after the exam concludes (GUI reads it back)",
        sp.getLastResultDTO());
    assertEquals(1, sp.getLastResultDTO().getScore());
  }
}

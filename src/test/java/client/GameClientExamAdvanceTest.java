package client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import client.exam.FinalExamListener;
import common.commands.Command;
import common.commands.SubmitQuestionAnswerCommand;
import common.dto.ExamQuestionDTO;
import common.dto.FinalExamChoiceDTO;
import common.dto.FinalExamDTO;
import common.dto.FinalExamQuestionDTO;
import common.dto.FinalExamSlotDTO;
import common.dto.InitiateFinalExamDTO;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * Multiplayer Final Exam question advance (.scratch/mp-exam-question-advance): a broadcast {@link
 * ExamQuestionDTO} on the client receive path must render in the GUI (via {@link
 * FinalExamListener#showQuestion}) and sync the client {@code FinalExamController} index, so the
 * next {@link SubmitQuestionAnswerCommand} carries the server-authoritative question index and the
 * engine's stale-index guard accepts it.
 *
 * <p>Drives the real {@code GameClient.processServerMessage} dispatch with no socket and no FX
 * toolkit (the spy listener does not touch JavaFX) — the pattern from {@code
 * GameClientHostCodeTest}.
 */
public class GameClientExamAdvanceTest {

  /**
   * A listener that is both the client-state listener and the exam listener, like MainController.
   */
  private interface ExamSpyListener extends GameClientStateListener, FinalExamListener {}

  /** Captures direct commands instead of writing them to a (non-existent) socket. */
  private static final class CapturingGameClient extends GameClient {
    final List<Command> sent = new ArrayList<>();

    CapturingGameClient() {
      super("localhost", 0, line -> {}, GameClient.LaunchMode.NORMAL, null);
    }

    @Override
    public void sendDirectCommand(Command command) {
      sent.add(command);
    }
  }

  private static FinalExamDTO twoQuestionExam() {
    FinalExamSlotDTO slot =
        new FinalExamSlotDTO(
            "slot1",
            List.of(
                new FinalExamChoiceDTO("opt_a", "Answer A"),
                new FinalExamChoiceDTO("opt_b", "Answer B")));
    FinalExamQuestionDTO q1 =
        new FinalExamQuestionDTO("Question one?", Map.of("slot1", slot), Map.of("slot1", "opt_a"));
    FinalExamQuestionDTO q2 =
        new FinalExamQuestionDTO("Question two?", Map.of("slot1", slot), Map.of("slot1", "opt_b"));
    return new FinalExamDTO(List.of(q1, q2));
  }

  private static ExamQuestionDTO broadcastQuestion(int index, FinalExamDTO exam) {
    FinalExamQuestionDTO q = exam.getQuestions().get(index);
    return new ExamQuestionDTO(
        index, exam.getQuestions().size(), q.getQuestionPrompt(), q.getSlots(), Map.of());
  }

  private static void dispatch(GameClient c, Object message) throws Exception {
    Method m = GameClient.class.getDeclaredMethod("processServerMessage", Object.class);
    m.setAccessible(true);
    m.invoke(c, message);
  }

  /** Makes this client the Host so {@code submitCurrentQuestion} actually sends. */
  private static void makeHost(GameClient c) throws Exception {
    setField(c, "playerId", "host-1");
    setField(c, "hostPlayerIdInSession", "host-1");
  }

  private static void setField(GameClient c, String name, Object value) throws Exception {
    Field f = GameClient.class.getDeclaredField(name);
    f.setAccessible(true);
    f.set(c, value);
  }

  @Test
  public void secondQuestionBroadcastRendersInGuiAndSyncsSubmitIndex() throws Exception {
    CapturingGameClient client = new CapturingGameClient();
    ExamSpyListener listener = mock(ExamSpyListener.class);
    client.setListener(listener);
    makeHost(client);

    FinalExamDTO exam = twoQuestionExam();
    dispatch(client, new InitiateFinalExamDTO(exam)); // shows Q1 (index 0)
    dispatch(client, broadcastQuestion(1, exam)); // engine advances; Q2 (index 1) is broadcast

    // 1. Every broadcast question renders in the GUI — including Q2.
    ArgumentCaptor<ExamQuestionDTO> shown = ArgumentCaptor.forClass(ExamQuestionDTO.class);
    verify(listener, atLeast(2)).showQuestion(shown.capture());
    assertTrue(
        "Q2 (index 1) must be rendered in the exam view, not only the console",
        shown.getAllValues().stream().anyMatch(q -> q.getQuestionIndex() == 1));

    // 2. The next submit carries the server-authoritative index (1), not the stale 0.
    client.getFinalExamController().submitCurrentQuestion(Map.of("slot1", "opt_b"));
    SubmitQuestionAnswerCommand submit =
        client.sent.stream()
            .filter(cmd -> cmd instanceof SubmitQuestionAnswerCommand)
            .map(cmd -> (SubmitQuestionAnswerCommand) cmd)
            .reduce((first, second) -> second) // last
            .orElseThrow(() -> new AssertionError("no SubmitQuestionAnswerCommand was sent"));
    assertEquals(
        "submit must carry the advanced question index so the engine guard accepts it",
        1,
        submit.getQuestionIndex());
  }
}

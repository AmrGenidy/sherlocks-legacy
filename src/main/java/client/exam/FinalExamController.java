package client.exam;

import client.GameClient;
import client.util.FinalExamState;
import common.commands.SubmitQuestionAnswerCommand;
import common.dto.ExamQuestionDTO;
import common.dto.FinalExamDTO;
import common.dto.FinalExamQuestionDTO;
import common.dto.FinalExamSlotDTO;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class FinalExamController {

  private final FinalExamDTO finalExam;
  private final FinalExamState finalExamState;
  private final FinalExamListener listener;
  private final GameClient gameClient;
  private final boolean isHost;

  public FinalExamController(
      FinalExamDTO finalExam, FinalExamListener listener, GameClient gameClient, boolean isHost) {
    this.finalExam = Objects.requireNonNull(finalExam, "FinalExamDTO cannot be null.");
    this.listener = Objects.requireNonNull(listener, "FinalExamListener cannot be null.");
    this.finalExamState = new FinalExamState();
    this.gameClient = gameClient;
    this.isHost = isHost;
  }

  public void startExam() {
    finalExamState.setCurrentQuestionIndex(0);
    listener.showExamView();
    showCurrentQuestion();
  }

  /**
   * Syncs the client's current-question index to the server-authoritative index carried by each
   * broadcast {@link ExamQuestionDTO} (.scratch/mp-exam-question-advance). Keeps {@code
   * submitCurrentQuestion} sending the matching index so the engine's stale-index guard accepts it.
   * Ignores an out-of-range index defensively.
   */
  public void syncCurrentQuestionIndex(int questionIndex) {
    if (questionIndex >= 0 && questionIndex < finalExam.getQuestions().size()) {
      finalExamState.setCurrentQuestionIndex(questionIndex);
    }
  }

  public void submitCurrentQuestion(Map<String, String> answers) {
    if (isHost && gameClient != null) {
      int currentIndex = finalExamState.getCurrentQuestionIndex();
      gameClient.sendDirectCommand(new SubmitQuestionAnswerCommand(currentIndex, answers));
    }
  }

  public void handleTerminalAnswer(String[] choiceIndices) {
    if (!isHost) return;

    int currentIndex = finalExamState.getCurrentQuestionIndex();
    if (currentIndex >= finalExam.getQuestions().size()) return;

    FinalExamQuestionDTO q = finalExam.getQuestions().get(currentIndex);
    // We need to map 1-based indices to actual ChoiceIDs for each slot.
    // This assumes the order of slots in the question matches the order of input.
    // And the order of choices in the slot matches the 1-based index.

    if (choiceIndices.length != q.getSlots().size()) {
      // Could print error to local console if we had access, but GameClient handles parsing
      return;
    }

    Map<String, String> answers = new HashMap<>();
    int slotCounter = 0;
    // Slots are a map, so order isn't guaranteed unless we sort or have list.
    // FinalExamQuestionDTO uses `Map<String, FinalExamSlotDTO> slots`.
    // We need a consistent iteration order. Let's sort by Slot ID or Label.
    // Assuming generic "slot1", "slot2" keys or just alphabetical order.
    List<String> sortedSlotIds = new ArrayList<>(q.getSlots().keySet());
    Collections.sort(sortedSlotIds);

    try {
      for (String slotId : sortedSlotIds) {
        int choiceIdx = Integer.parseInt(choiceIndices[slotCounter]) - 1; // 0-based
        FinalExamSlotDTO slot = q.getSlots().get(slotId);
        if (choiceIdx >= 0 && choiceIdx < slot.getChoices().size()) {
          answers.put(slotId, slot.getChoices().get(choiceIdx).getChoiceId());
        } else {
          // Invalid choice index
          return;
        }
        slotCounter++;
      }
      submitCurrentQuestion(answers);
    } catch (NumberFormatException e) {
      // Ignore
    }
  }

  public void updateAnswer(int questionIndex, String slotId, String choiceId) {
    // This might be used if we want to show real-time selection from other players (host),
    // but in linear flow, we usually just submit the batch.
    // If spectating, we might receive updates.
    finalExamState.setAnswer(questionIndex, slotId, choiceId);
    if (questionIndex == finalExamState.getCurrentQuestionIndex()) {
      showCurrentQuestion();
    }
  }

  private void showCurrentQuestion() {
    int currentIndex = finalExamState.getCurrentQuestionIndex();
    FinalExamQuestionDTO currentQuestion = finalExam.getQuestions().get(currentIndex);

    ExamQuestionDTO dto =
        new ExamQuestionDTO(
            currentIndex,
            finalExam.getQuestions().size(),
            currentQuestion.getQuestionPrompt(),
            currentQuestion.getSlots(),
            finalExamState.getAnswersForQuestion(currentIndex));
    listener.showQuestion(dto);
  }

  public void exitExam() {
    // Close the exam view by switching back to the main game view.
    if (listener instanceof ui.MainController) {
      ((ui.MainController) listener).exitFinalExamMode();
    }
  }

  public void returnToMainMenu() {
    if (listener instanceof ui.MainController) {
      ((ui.MainController) listener).returnToMainMenu();
    }
  }
}

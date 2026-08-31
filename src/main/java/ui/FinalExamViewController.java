package ui;

import client.exam.FinalExamController;
import common.dto.ExamQuestionDTO;
import common.dto.ExamResultDTO;
import common.dto.FinalExamChoiceDTO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import ui.i18n.L10n;

public class FinalExamViewController {

  @FXML private Label titleLabel;
  @FXML private Label progressLabel;
  @FXML private VBox questionPane;
  @FXML private Label questionPromptLabel;
  @FXML private HBox slotsBox;
  @FXML private ComboBox<FinalExamChoiceDTO> slot1ComboBox;
  @FXML private ComboBox<FinalExamChoiceDTO> slot2ComboBox;
  @FXML private Button previousButton;
  @FXML private Button nextButton;
  @FXML private Button submitButton;
  @FXML private ScrollPane resultScrollPane;
  @FXML private VBox resultVBox;

  // Add Retry Button programmatically or via FXML.
  // Since I can't edit FXML, I'll add it to resultVBox dynamically or check if I
  // can modify FXML.
  // I'll add it dynamically in displayResults.

  private FinalExamController finalExamController;
  private boolean isHost;

  // Per-question tutorial gate (.scratch/gui-exam-tutorial-input-enforce): when a tutorial question
  // is terminal-taught, the GUI dropdowns are disabled and pressing Submit nudges the player to the
  // terminal instead of answering. Default: GUI always allowed (normal play / non-tutorial exams).
  private java.util.function.BooleanSupplier guiAnswerAllowed = () -> true;
  private Runnable onGuiAnswerBlocked = () -> {};

  // Anti-abuse: when the Final Exam is frozen for rapid re-submission, the Submit button is disabled
  // and a submit attempt is refused. Default: never frozen.
  private java.util.function.BooleanSupplier examFrozen = () -> false;

  /** Wires the exam-frozen gate (queried at display/submit time) for the rapid-submission cooldown. */
  public void setExamFrozenSupplier(java.util.function.BooleanSupplier examFrozen) {
    this.examFrozen = examFrozen != null ? examFrozen : () -> false;
  }

  /**
   * Wires the per-question GUI gate: {@code guiAnswerAllowed} is queried at display/submit time so
   * the gate follows the current tutorial step; {@code onBlocked} shows the in-world "use the
   * terminal" nudge when the player submits on a terminal-taught question.
   */
  public void setGuiAnswerGate(
      java.util.function.BooleanSupplier guiAnswerAllowed, Runnable onBlocked) {
    this.guiAnswerAllowed = guiAnswerAllowed != null ? guiAnswerAllowed : () -> true;
    this.onGuiAnswerBlocked = onBlocked != null ? onBlocked : () -> {};
  }

  private boolean guiAnswerAllowed() {
    return guiAnswerAllowed.getAsBoolean();
  }

  /** Disables the answer dropdowns when the GUI is not the taught method for this question. */
  private void applyGuiAnswerGate() {
    boolean enabled = isHost && guiAnswerAllowed();
    slot1ComboBox.setDisable(!enabled);
    slot2ComboBox.setDisable(!enabled);
  }

  public void setFinalExamController(FinalExamController finalExamController, boolean isHost) {
    this.finalExamController = finalExamController;
    this.isHost = isHost;
    if (!isHost) {
      slot1ComboBox.setDisable(true);
      slot2ComboBox.setDisable(true);
      previousButton.setDisable(true);
      nextButton.setDisable(true);
      submitButton.setVisible(false);
      submitButton.setManaged(false);
    }
  }

  @FXML
  public void initialize() {
    // Final Exam questions read as a typed exam paper (DESIGN.md §3).
    questionPromptLabel.getStyleClass().add("typewriter");

    // Static text comes from the bundle (FXML carries no literals); re-applied on every
    // displayQuestion so a language switch from the menu is picked up on re-entry.
    applyStaticTexts();
    progressLabel.setText("");
    questionPromptLabel.setText("");

    // Previous button not used in strict linear flow
    previousButton.setVisible(false);
    previousButton.setManaged(false);

    // "Next" button logic can be merged with Submit, or used if we had navigation.
    // Requirements say "Confirm/Answer button... Immediately load next question".
    // So we should use submitButton or repurpose nextButton as the primary action.
    // Let's use nextButton as the "Answer" button for consistency if it's
    // prominent.
    // Or submitButton. FXML usually has both? The file shows both.
    // I'll hide nextButton and use submitButton as the "Confirm Answer" button.
    nextButton.setVisible(false);
    nextButton.setManaged(false);

    submitButton.setOnAction(
        event -> {
          // Tutorial gate: on a terminal-taught question the GUI must not take the answer — nudge
          // the player to the terminal instead (.scratch/gui-exam-tutorial-input-enforce).
          if (!guiAnswerAllowed()) {
            onGuiAnswerBlocked.run();
            return;
          }
          // Anti-abuse: refuse to submit while the exam is frozen for rapid re-submission.
          if (examFrozen.getAsBoolean()) {
            submitButton.setDisable(true);
            return;
          }
          // Disable button immediately to prevent double submissions
          submitButton.setDisable(true);

          // Gather answers
          java.util.Map<String, String> answers = new java.util.HashMap<>();
          if (slot1ComboBox.isVisible() && slot1ComboBox.getValue() != null)
            answers.put("slot1", slot1ComboBox.getValue().getChoiceId());
          if (slot2ComboBox.isVisible() && slot2ComboBox.getValue() != null)
            answers.put("slot2", slot2ComboBox.getValue().getChoiceId());

          // Determine how many slots are visible to validate appropriately
          int visibleSlots = 0;
          if (slot1ComboBox.isVisible()) visibleSlots++;
          if (slot2ComboBox.isVisible()) visibleSlots++;

          // Only submit if all visible slots are filled
          if (answers.size() >= visibleSlots && visibleSlots > 0) {
            finalExamController.submitCurrentQuestion(answers);
          } else {
            // Re-enable if validation failed
            submitButton.setDisable(false);
          }
        });

    // We don't need listener to send updates on change anymore, only on submit.
  }

  /** Bundle-resolved chrome for the exam paper (finalExam.* keys). */
  private void applyStaticTexts() {
    titleLabel.setText(L10n.t("finalExam.title"));
    slot1ComboBox.setPromptText(L10n.t("finalExam.slot1Prompt"));
    slot2ComboBox.setPromptText(L10n.t("finalExam.slot2Prompt"));
    submitButton.setText(L10n.t("finalExam.submitAnswer"));
  }

  public void displayResults(ExamResultDTO resultDTO) {
    Platform.runLater(
        () -> {
          // Switch panes by toggling visible+managed only (both live stacked in the same region).
          // Unmanaging the hidden panes means they reserve no layout space.
          questionPane.setVisible(false);
          questionPane.setManaged(false);
          submitButton.setVisible(false);
          submitButton.setManaged(false);

          resultScrollPane.setVisible(true);
          resultScrollPane.setManaged(true);
          resultVBox.getChildren().clear();

          Label scoreLabel =
              new Label(
                  L10n.t("finalExam.score", resultDTO.getScore(), resultDTO.getTotalQuestions()));
          scoreLabel.getStyleClass().add("exam-score");
          resultVBox.getChildren().add(scoreLabel);

          // Readable per-question review (gui-exam-review): the prompt filled with the player's
          // chosen choice text, ✓/✗ + moss/oxblood per line. Never the correct answer.
          java.util.List<common.dto.ExamReviewItemDTO> reviewItems = resultDTO.getReviewItems();
          if (reviewItems != null && !reviewItems.isEmpty()) {
            Label reviewHeading = new Label(L10n.t("finalExam.reviewHeading"));
            reviewHeading.getStyleClass().add("exam-review-heading");
            resultVBox.getChildren().add(reviewHeading);
            for (common.dto.ExamReviewItemDTO item : reviewItems) {
              Label detailLabel =
                  new Label(
                      (item.isCorrect() ? "✓ " : "✗ ")
                          + "Q"
                          + item.getQuestionNumber()
                          + ": "
                          + item.getFilledPrompt());
              detailLabel.setWrapText(true);
              detailLabel.setMaxWidth(Double.MAX_VALUE);
              detailLabel
                  .getStyleClass()
                  .add(item.isCorrect() ? "exam-review-correct" : "exam-review-incorrect");
              resultVBox.getChildren().add(detailLabel);
            }
          } else {
            for (String detail : resultDTO.getReviewableAnswersInfo()) {
              Label detailLabel = new Label(detail);
              detailLabel.setWrapText(true);
              resultVBox.getChildren().add(detailLabel);
            }
          }

          if (resultDTO.isCaseSolved()) {
            if (resultDTO.getWinningMessage() != null) {
              Label winningLabel = new Label("\n" + resultDTO.getWinningMessage());
              winningLabel.getStyleClass().add("exam-winning");
              resultVBox.getChildren().add(winningLabel);
            }
          }

          // If guest, show simple waiting message instead of controls
          if (!isHost) {
            Label waitingLabel = new Label(L10n.t("finalExam.waitingForHost"));
            waitingLabel.getStyleClass().add("exam-waiting");
            resultVBox.getChildren().add(waitingLabel);
          } else {
            // Host controls
            Button continueButton = new Button(L10n.t("finalExam.continue"));
            continueButton.setOnAction(
                e -> {
                  if (finalExamController != null) {
                    finalExamController.exitExam();
                  }
                });

            Button mainMenuButton = new Button(L10n.t("finalExam.mainMenu"));
            mainMenuButton.setOnAction(
                e -> {
                  if (finalExamController != null) {
                    finalExamController.returnToMainMenu();
                  }
                });

            javafx.scene.layout.HBox buttonBox = new javafx.scene.layout.HBox(10, continueButton);
            // Only show the Main Menu button if the case is solved
            if (resultDTO.isCaseSolved()) {
              buttonBox.getChildren().add(mainMenuButton);
            }
            buttonBox.setAlignment(javafx.geometry.Pos.CENTER);
            resultVBox.getChildren().add(buttonBox);
          }
        });
  }

  public void displayQuestion(ExamQuestionDTO questionDTO) {
    Platform.runLater(
        () -> {
          // Reset View State for new question (or retry)
          resetView();
          applyStaticTexts();

          // Re-enable for the new question unless the exam is frozen for rapid re-submission.
          submitButton.setDisable(examFrozen.getAsBoolean());
          progressLabel.setText(
              L10n.t(
                  "finalExam.progress",
                  questionDTO.getQuestionIndex() + 1,
                  questionDTO.getTotalQuestions()));
          questionPromptLabel.setText(questionDTO.getQuestionPrompt());

          // Right-to-left languages read the question the other way, so the fill-in slots must flow
          // right-to-left too — slot 1 on the RIGHT, slot 2 on its left — to line up with the
          // sentence. Only this row is mirrored (the app keeps LTR layout otherwise). Re-applied per
          // question so a language switch mid-exam is picked up.
          slotsBox.setNodeOrientation(
              L10n.isRtl()
                  ? javafx.geometry.NodeOrientation.RIGHT_TO_LEFT
                  : javafx.geometry.NodeOrientation.LEFT_TO_RIGHT);

          // Depending on the amount of slots given
          boolean hasSlot1 = questionDTO.getSlots().containsKey("slot1");
          slot1ComboBox.setVisible(hasSlot1);
          slot1ComboBox.setManaged(hasSlot1);
          if (hasSlot1) {
            updateComboBox(slot1ComboBox, "slot1", questionDTO);
          }

          boolean hasSlot2 = questionDTO.getSlots().containsKey("slot2");
          slot2ComboBox.setVisible(hasSlot2);
          slot2ComboBox.setManaged(hasSlot2);
          if (hasSlot2) {
            updateComboBox(slot2ComboBox, "slot2", questionDTO);
          }

          // Enforce the taught input method for this question (read now, after any tutorial step
          // advance has settled — .scratch/gui-exam-tutorial-input-enforce).
          applyGuiAnswerGate();
        });
  }

  private void resetView() {
    // Restore the full-width question layout and hide the results, so re-entering the exam after a
    // submit is clean — not squeezed. Both panes are stacked in one region; a visible+managed toggle
    // is all that's needed (no node re-parenting), and unmanaging the hidden pane frees its space.
    resultScrollPane.setVisible(false);
    resultScrollPane.setManaged(false);

    questionPane.setVisible(true);
    questionPane.setManaged(true);
    submitButton.setVisible(true);
    submitButton.setManaged(true);
  }

  private void updateComboBox(
      ComboBox<FinalExamChoiceDTO> comboBox, String slotId, ExamQuestionDTO questionDTO) {
    comboBox.getItems().clear();
    comboBox.getItems().addAll(questionDTO.getSlots().get(slotId).getChoices());
    comboBox.setConverter(
        new javafx.util.StringConverter<>() {
          @Override
          public String toString(FinalExamChoiceDTO object) {
            return object == null ? null : object.getChoiceText();
          }

          @Override
          public FinalExamChoiceDTO fromString(String string) {
            return null;
          }
        });

    String selectedChoiceId = questionDTO.getSelectedAnswers().get(slotId);
    if (selectedChoiceId != null) {
      for (FinalExamChoiceDTO choice : comboBox.getItems()) {
        if (choice.getChoiceId().equals(selectedChoiceId)) {
          comboBox.getSelectionModel().select(choice);
          break;
        }
      }
    }
  }
}

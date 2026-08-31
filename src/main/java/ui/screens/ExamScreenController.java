package ui.screens;

import client.exam.FinalExamController;
import common.dto.ExamQuestionDTO;
import common.dto.ExamResultDTO;
import java.io.IOException;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import ui.FinalExamViewController;
import ui.MainController;
import ui.i18n.L10n;
import ui.shell.ScreenController;

/**
 * Final Exam screen (ADR-0002): the exam paper view ({@code FinalExamView.fxml}), question/result
 * rendering for both the multiplayer {@link FinalExamController} and the single-player adapter, the
 * victory popup, and the post-exam terminal menu (1 = continue investigating, 2 = exit when
 * solved). The shell stays the {@code FinalExamListener}; every callback delegates here.
 */
public class ExamScreenController implements ScreenController {

  private static final org.slf4j.Logger logger =
      org.slf4j.LoggerFactory.getLogger(ExamScreenController.class);

  private final MainController shell;
  private final StackPane container = new StackPane();
  // The Final Exam keeps the toolbar, so the player can open the same trimmed Settings dossier they
  // reach in-game — raised over the exam paper without abandoning it (.scratch/gui-ingame-settings).
  private final InGameSettingsOverlay inGameSettings;
  private Parent finalExamView;
  private FinalExamViewController finalExamViewController;
  private FinalExamController finalExamController;
  private ExamResultDTO lastExamResult;
  // The last result already counted toward the anti-abuse cooldown, so a re-render of the same
  // result view never double-counts a single exam submission.
  private ExamResultDTO lastCountedResult;

  // True while the results view is showing (vs. a question mid-exam). Escape steps back to the
  // game only from the results view — never abandons a Final Exam in progress
  // (navigation-ux-smoothness issue 02).
  private boolean showingResults;

  public ExamScreenController(MainController shell) {
    this.shell = shell;
    this.inGameSettings = new InGameSettingsOverlay(shell, container);
    try {
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/FinalExamView.fxml"));
      finalExamView = loader.load();
      finalExamViewController = loader.getController();
      // Per-question tutorial input enforcement: the GUI may answer unless the current tutorial
      // step
      // teaches the terminal method; a blocked submit nudges the player to the terminal
      // (.scratch/gui-exam-tutorial-input-enforce).
      finalExamViewController.setGuiAnswerGate(
          shell::isExamGuiAnswerAllowed, shell::promptExamUseTerminal);
      // Anti-abuse: the Submit button is disabled while the exam is frozen for rapid re-submission.
      finalExamViewController.setExamFrozenSupplier(shell::isFinalExamFrozen);
      container.getChildren().add(finalExamView);
    } catch (IOException e) {
      logger.error("Failed to load FinalExamView.fxml", e);
    }
  }

  @Override
  public Node getView() {
    return container;
  }

  /**
   * True while a Final Exam is being answered (the question view), false once the result is
   * showing. The shell uses this to lock out routed gameplay commands only while the exam is in
   * progress (.scratch/exam-command-lockout).
   */
  public boolean isExamInProgress() {
    return !showingResults;
  }

  @Override
  public boolean showsGameChrome() {
    return true;
  }

  /**
   * Drops exam state from the previous Game Session (fresh SP run) AND tears down the "Case solved"
   * victory popup so it never lingers on the exam paper into the next tutorial/session
   * (.scratch/gui-tutorial-exit-cleanup). Called on tutorial exit and on session start/exit.
   */
  public void reset() {
    finalExamController = null;
    lastExamResult = null;
    lastCountedResult = null;
    showingResults = false;
    inGameSettings.hide();
    container.getChildren().removeIf(n -> "victoryBubble".equals(n.getId()));
  }

  /**
   * Counts one exam submission toward the anti-abuse cooldown (a completed/scored exam). Guarded so
   * a re-render of the same result never double-counts a single submission.
   */
  private void countExamSubmission(ExamResultDTO resultDTO) {
    if (resultDTO != null && resultDTO != lastCountedResult) {
      lastCountedResult = resultDTO;
      shell.recordFinalExamSubmission();
    }
  }

  /**
   * Mid-exam: unconsumed no-op — Escape never abandons the Final Exam by accident. On the results
   * view: same as option "1" (leave the exam view, back to the game).
   */
  @Override
  public boolean onEscape() {
    // Settings raised over the paper steps back first (no dead-ends), before any exam navigation.
    if (inGameSettings.isShowing()) {
      inGameSettings.hide();
      return true;
    }
    if (showingResults) {
      exitFinalExamMode();
      return true;
    }
    return false;
  }

  // ====================== In-game Settings (shared overlay) ======================

  /** Raises the trimmed Settings dossier over the exam paper (.scratch/gui-ingame-settings). */
  public void showInGameSettings() {
    inGameSettings.show();
  }

  /** Dismisses the Settings dossier if it is showing over the exam paper. */
  public void hideInGameSettings() {
    inGameSettings.hide();
  }

  public boolean isInGameSettingsShowing() {
    return inGameSettings.isShowing();
  }

  // ====================== Terminal input ======================

  // Reference tools the detective keeps open during the Final Exam (.scratch/exam-command-lockout):
  // chat, the Journal and the Pinboard. Everything else is refused in-world while the exam is in
  // progress; the engine authority (BaseCommand) is the real backstop, this is the feedback.
  private static final java.util.Set<String> ALLOWED_DURING_EXAM =
      java.util.Set.of("chat", "journal", "pinboard");

  @Override
  public boolean handleTerminalInput(String input) {
    // Results showing (exam scored): the post-exam menu and navigation work unchanged.
    if (showingResults) {
      if (input.equals("1")) {
        exitFinalExamMode();
        return true;
      }
      if (input.equals("2")) {
        boolean solved = false;
        if (shell.isSinglePlayerMode()
            && shell.getSinglePlayerGame() != null
            && shell.getSinglePlayerGame().getGameContext().getLastResultDTO() != null) {
          solved = shell.getSinglePlayerGame().getGameContext().getLastResultDTO().isCaseSolved();
        } else if (!shell.isSinglePlayerMode() && lastExamResult != null) {
          solved = lastExamResult.isCaseSolved();
        }

        if (solved) {
          if (shell.isSinglePlayerMode()) {
            shell.returnToMainMenu();
          } else if (shell.getGameClient() != null) {
            shell.getGameClient().enqueueUserInput("exit");
          }
        } else {
          shell.appendTerminalText(L10n.t("exam.invalidSelection") + "\n");
        }
        return true;
      }
      forwardToProcessor(input);
      return true;
    }

    // Exam in progress: only the reference tools stay open; refuse everything else in-world rather
    // than forwarding it (a typed "move" must not run in the background or close the exam paper).
    if (isAllowedDuringExam(input)) {
      forwardToProcessor(input);
    } else {
      shell.appendTerminalText(L10n.t("exam.commandBlocked") + "\n");
    }
    return true;
  }

  /** First-token allow-list check for the in-progress Final Exam. */
  private static boolean isAllowedDuringExam(String input) {
    if (input == null || input.isBlank()) {
      return false;
    }
    String verb = input.trim().split("\\s+", 2)[0].toLowerCase();
    return ALLOWED_DURING_EXAM.contains(verb);
  }

  /** Forwards a terminal command to the active Single-player Session or Game Session. */
  private void forwardToProcessor(String input) {
    if (shell.isSinglePlayerMode()) {
      if (shell.getSinglePlayerGame() != null) {
        shell.getSinglePlayerGame().processCommand(input);
      }
    } else if (shell.getGameClient() != null) {
      shell.getGameClient().enqueueUserInput(input);
    }
  }

  /**
   * Post-exam menu choices ("1" continue investigating, "2" exit when solved) plus the basic
   * in-game commands this screen passes through to the command processor
   * (.scratch/terminal-autocomplete issue 03).
   */
  @Override
  public ui.terminal.CompletionContext completionContext() {
    // Suggest the post-exam menu and the reference tools that stay open during the exam; not the
    // now-locked-out gameplay commands (look/tasks) — see .scratch/exam-command-lockout.
    return ui.terminal.CompletionContext.builder()
        .bareOption("1", "1. " + L10n.t("exam.option.continue"))
        .bareOption("2", "2. " + L10n.t("exam.option.exit"))
        .command("journal")
        .command("help")
        .build();
  }

  // ====================== FinalExamListener delegates ======================

  public void showQuestion(ExamQuestionDTO questionDTO) {
    showingResults = false;
    finalExamViewController.displayQuestion(questionDTO);
  }

  public void showExamResults(ExamResultDTO resultDTO) {
    this.lastExamResult = resultDTO;
    showingResults = true;
    countExamSubmission(resultDTO);
    finalExamViewController.displayResults(resultDTO);
    if (resultDTO.isCaseSolved()) {
      shell.recordActiveCaseSolved(resultDTO); // Completed-Case Record + wax seal (MENU_DESIGN #2)
      showVictoryPopup(resultDTO.getFinalRank(), resultDTO.getWinningMessage());
    }
  }

  public void showExamView() {
    if (!shell.isSinglePlayerMode() && shell.getGameClient() != null) {
      this.finalExamController = shell.getGameClient().getFinalExamController();
    }
    finalExamViewController.setFinalExamController(finalExamController, shell.isHostPlayer());
    shell.showScreen(this);
  }

  public void notifyUnansweredQuestions() {
    Platform.runLater(
        () -> {
          Alert alert = new Alert(Alert.AlertType.WARNING);
          alert.setTitle(L10n.t("exam.unansweredTitle"));
          alert.setHeaderText(null);
          alert.setContentText(L10n.t("exam.unansweredBody"));
          // A dialog is its own Scene: install the theme (which also tags the reading text-size
          // bucket) so it matches the app instead of the modena default.
          ui.util.Theme.install(alert.getDialogPane());
          ui.i18n.LocaleStyling.apply(alert.getDialogPane());
          alert.showAndWait();
        });
  }

  public void onFinalExamRequest(String requesterDisplayName) {
    Platform.runLater(
        () ->
            shell.appendTerminalText(
                "\n" + L10n.t("exam.requestedBy", requesterDisplayName) + "\n"));
  }

  // ====================== Single-player exam flow ======================

  public void onSinglePlayerExamStarted() {
    Platform.runLater(() -> shell.showScreen(this));
    // Trigger immediate update if question is ready
    onSinglePlayerQuestionUpdate();
  }

  public void onSinglePlayerQuestionUpdate() {
    if (shell.getSinglePlayerGame() == null || shell.getSinglePlayerGame().getGameContext() == null)
      return;

    ExamQuestionDTO dto = shell.getSinglePlayerGame().getGameContext().getCurrentExamQuestionDTO();
    if (dto != null) {
      if (finalExamController == null) {
        // SP adapter: the view expects a client FinalExamController; route the submission
        // straight into the in-process engine instead of a GameClient. The shell stays the
        // FinalExamListener (FinalExamController casts it back for exit/return navigation).
        common.dto.FinalExamDTO examDTO =
            shell.getSinglePlayerGame().getGameContext().getSelectedCase().getFinalExam();

        finalExamController =
            new FinalExamController(examDTO, shell, null, true) {
              @Override
              public void submitCurrentQuestion(java.util.Map<String, String> answers) {
                common.dto.ExamQuestionDTO currentQ =
                    shell.getSinglePlayerGame().getGameContext().getCurrentExamQuestionDTO();
                if (currentQ != null) {
                  shell
                      .getSinglePlayerGame()
                      .getGameContext()
                      .processSubmitQuestionAnswer(null, currentQ.getQuestionIndex(), answers);
                  // A GUI answer advances the exam tutorial's GUI step (the terminal path advances
                  // via the routed command); .scratch/gui-exam-tutorial.
                  if (shell.isTutorialActive()) {
                    shell.notifyTutorialExamAnsweredViaGui();
                  }
                }
                // else: exam might be finished or already processed; the result callback
                // handles it. Suppress an error message to avoid confusing the user.
              }
            };
      }

      showingResults = false;
      finalExamViewController.setFinalExamController(finalExamController, true);
      finalExamViewController.displayQuestion(dto);
    }
  }

  public void onSinglePlayerExamResult() {
    if (shell.getSinglePlayerGame() == null || shell.getSinglePlayerGame().getGameContext() == null)
      return;
    ExamResultDTO dto = shell.getSinglePlayerGame().getGameContext().getLastResultDTO();
    if (dto != null) {
      this.lastExamResult = dto;
      showingResults = true;
      countExamSubmission(dto);
      finalExamViewController.displayResults(dto);
      if (dto.isCaseSolved()) {
        shell.recordActiveCaseSolved(dto); // Completed-Case Record + wax seal (MENU_DESIGN #2)
        showVictoryPopup(dto.getFinalRank(), dto.getWinningMessage());
      }
    }
  }

  // ====================== Exit & victory ======================

  public void exitFinalExamMode() {
    exitFinalExamUI();
    if (shell.isSinglePlayerMode()) {
      shell.getSinglePlayerGame().processCommand("look");
    } else if (shell.getGameClient() != null) {
      // For multiplayer, we send the specific ContinueGameCommand.
      // Since we don't have a text command parser for this, we send the object directly.
      shell.getGameClient().sendDirectCommand(new common.commands.ContinueGameCommand());
    }
  }

  /** Leaves the exam paper for the in-game screen (e.g. a guest's host pressed Continue). */
  public void exitFinalExamUI() {
    showingResults = false;
    inGameSettings.hide();
    Platform.runLater(
        () -> {
          shell.showGameScreen();
          shell.refreshRoomView();
        });
  }

  private void showVictoryPopup(String rankName, String winningStatement) {
    Platform.runLater(
        () -> {
          // Remove existing bubble if any
          container
              .getChildren()
              .removeIf(node -> node.getId() != null && node.getId().equals("victoryBubble"));

          VBox bubble = new VBox(20);
          bubble.setId("victoryBubble");
          // Percent-bound to the exam pane with 8px-scale floors, not fixed 600×400
          // (.scratch/responsive-resizing issue 02).
          ui.util.ViewportSizing.bindMaxToViewport(bubble, container, 0.6, 320, 0.7, 240);
          bubble.setPadding(new Insets(30));
          bubble.setAlignment(Pos.CENTER);

          bubble.getStyleClass().add("victory-bubble");

          Label titleLabel = new Label(L10n.t("victory.title"));
          titleLabel.getStyleClass().add("victory-title");

          Label rankLabel = new Label(L10n.t("victory.rank", rankName));
          rankLabel.getStyleClass().add("victory-rank");

          Label statementLabel = new Label(winningStatement);
          statementLabel.setWrapText(true);
          statementLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
          statementLabel.getStyleClass().add("victory-statement");

          Button closeButton = new Button(L10n.t("common.close"));
          closeButton.setOnAction(e -> container.getChildren().remove(bubble));

          bubble.getChildren().addAll(titleLabel, rankLabel, statementLabel, closeButton);

          // Center in stackpane
          StackPane.setAlignment(bubble, Pos.CENTER);
          container.getChildren().add(bubble);
        });
  }
}

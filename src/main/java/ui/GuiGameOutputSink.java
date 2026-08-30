package ui;

import common.dto.CommandCooldownUpdateDTO;
import common.dto.DeductionCountUpdateDTO;
import common.dto.DialogueEventDTO;
import common.dto.TextMessage;
import common.dto.ExamQuestionDTO;
import common.dto.ExamResultDTO;
import common.dto.InitiateFinalExamDTO;
import common.dto.InsightTokenUpdateDTO;
import common.dto.JournalEntryDTO;
import common.dto.ReturnToCaseSelectionDTO;
import common.dto.RoomDescriptionDTO;
import common.dto.TaskStateUpdateDTO;
import java.io.Serializable;
import java.util.function.Consumer;
import javafx.application.Platform;
import singleplayer.ConsoleGameOutputSink;
import singleplayer.GameOutputSink;

/**
 * {@link GameOutputSink} that dispatches single-player engine events to the GUI. Each emitted DTO
 * drives the same {@link MainController} handlers the multiplayer client path feeds through {@code
 * client.GameClientStateListener}, plus a marker-free transcript line in the terminal area. This is
 * the typed replacement for the {@code System.out} {@code ->} {@code TextAreaOutputStream} {@code
 * ->} regex {@code GameOutputParser} pipeline (issue 03, .scratch/typed-game-events).
 *
 * <p>The engine may emit off the FX Application Thread (e.g. the token broadcast thread in {@code
 * GameContextSinglePlayer.initializeNewCase}), so every dispatch is marshalled through {@link
 * Platform#runLater} (injectable for tests).
 */
public class GuiGameOutputSink implements GameOutputSink {

  private final MainController controller;
  private final Consumer<Runnable> fxThreadMarshaller;
  private final ui.i18n.L10nGameTexts texts = new ui.i18n.L10nGameTexts();

  public GuiGameOutputSink(MainController controller) {
    this(controller, Platform::runLater);
  }

  GuiGameOutputSink(MainController controller, Consumer<Runnable> fxThreadMarshaller) {
    this.controller = controller;
    this.fxThreadMarshaller = fxThreadMarshaller;
  }

  @Override
  public void emit(Serializable event) {
    if (event == null) {
      return;
    }
    fxThreadMarshaller.accept(() -> dispatch(event));
  }

  private void dispatch(Serializable event) {
    // Resolve a generic Watson response's UI-language key once, up front, so both the transcript
    // line and the dialogue bubble below read localized text (.scratch/gui-localized-watson-hints).
    if (event instanceof DialogueEventDTO de) {
      event = ui.i18n.WatsonDialogue.localize(de);
    }

    // A keyed TextMessage carries a localization key + English fallback; resolve it to THIS client's
    // UI language here (the shared ConsoleGameOutputSink stays English/headless).
    String transcriptLine;
    if (event instanceof TextMessage tm && tm.getMessageKey() != null) {
      Object[] a = tm.getArgs() == null ? new Object[0] : tm.getArgs().toArray();
      transcriptLine = ui.i18n.L10n.tOr(tm.getMessageKey(), tm.getText(), a);
    } else {
      transcriptLine = ConsoleGameOutputSink.renderDisplayText(event, texts);
    }
    if (transcriptLine != null) {
      // Colour the line from the typed event (no string heuristics, no wire change) so the terminal
      // reads as distinct system/error/dialogue/normal lines — .scratch/ingame-terminal-polish
      // DEC-3.
      controller.appendTerminalText(transcriptLine + "\n", ui.terminal.TerminalLineKind.of(event));
    }

    if (event instanceof ReturnToCaseSelectionDTO) {
      controller.showCaseSelectionMenu();
    } else if (event instanceof InitiateFinalExamDTO) {
      controller.onSinglePlayerExamStarted();
    } else if (event instanceof RoomDescriptionDTO rd) {
      controller.updateRoomView(rd);
    } else if (event instanceof JournalEntryDTO) {
      controller.refreshJournalWindow();
    } else if (event instanceof DialogueEventDTO de) {
      controller.showDialogueBubble(de);
    } else if (event instanceof ExamQuestionDTO) {
      controller.onSinglePlayerQuestionUpdate();
    } else if (event instanceof ExamResultDTO) {
      controller.onSinglePlayerExamResult();
    } else if (event instanceof InsightTokenUpdateDTO tokens) {
      controller.onInsightTokensUpdate(tokens.getCount());
    } else if (event instanceof DeductionCountUpdateDTO deductions) {
      controller.onDeductionCountUpdate(deductions.getCount());
    } else if (event instanceof TaskStateUpdateDTO task) {
      controller.onTaskStateUpdate(task.getTaskIndex(), task.getIsCompleted());
    } else if (event instanceof CommandCooldownUpdateDTO cooldown) {
      controller.onCommandCooldownUpdate(cooldown.getCommandType(), cooldown.getCooldownUntil());
    }
  }
}

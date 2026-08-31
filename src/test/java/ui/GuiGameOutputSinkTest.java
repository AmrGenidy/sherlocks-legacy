package ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import common.dto.CommandCooldownUpdateDTO;
import common.dto.DeductionCountUpdateDTO;
import common.dto.DialogueEventDTO;
import common.dto.DialogueType;
import common.dto.ExamQuestionDTO;
import common.dto.ExamResultDTO;
import common.dto.FinalExamDTO;
import common.dto.InitiateFinalExamDTO;
import common.dto.InsightTokenUpdateDTO;
import common.dto.JournalEntryDTO;
import common.dto.ReturnToCaseSelectionDTO;
import common.dto.RoomDescriptionDTO;
import common.dto.TextMessage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import ui.terminal.TerminalLineKind;

/**
 * Pins the typed-event dispatch that replaces the {@code System.out} {@code ->} {@code
 * TextAreaOutputStream} {@code ->} regex {@code GameOutputParser} pipeline (issue 03,
 * .scratch/typed-game-events): each single-player DTO drives the same {@link MainController}
 * handlers the multiplayer client path uses, plus a marker-free line in the terminal area. The
 * information content mirrors the legacy console contract pinned by {@code
 * singleplayer.ConsoleGameOutputSinkTest}.
 */
public class GuiGameOutputSinkTest {

  private final MainController controller = mock(MainController.class);
  private final GuiGameOutputSink sink = new GuiGameOutputSink(controller, Runnable::run);

  @Test
  public void roomDescription_updatesRoomViewAndTerminal() {
    Map<String, String> exits = new LinkedHashMap<>();
    exits.put("east", "Terrace");
    RoomDescriptionDTO room =
        new RoomDescriptionDTO(
            "Ballroom", "A grand hall.", List.of("Vase"), List.of("Butler"), exits);

    sink.emit(room);

    verify(controller).updateRoomView(room);
    ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
    verify(controller).appendTerminalText(text.capture(), eq(TerminalLineKind.NORMAL));
    assertTrue(text.getValue().contains("--- Ballroom ---"));
    assertTrue(text.getValue().contains("Objects: Vase"));
    assertTrue(text.getValue().contains("Occupants: Butler"));
    assertTrue(text.getValue().contains("Exits: east (to Terrace)"));
    assertFalse(
        "machine markers must not reach the GUI terminal",
        text.getValue().contains("[ROOM_UPDATE]"));
  }

  @Test
  public void dialogueEvent_showsBubbleAndPlainTranscriptLine() {
    DialogueEventDTO event = new DialogueEventDTO("Watson", "Line one\nA | B", DialogueType.WATSON);

    sink.emit(event);

    verify(controller).showDialogueBubble(event);
    // Unlike the console rendering, no [NEWLINE]/[PIPE] escaping: the text never gets re-parsed.
    verify(controller).appendTerminalText("Watson: Line one\nA | B\n", TerminalLineKind.NORMAL);
  }

  @Test
  public void journalEntry_refreshesJournalWindow() {
    sink.emit(new JournalEntryDTO("A clue.", "player-1", 0L));

    verify(controller).refreshJournalWindow();
    verify(controller).appendTerminalText("Statement added to journal.\n", TerminalLineKind.NORMAL);
  }

  @Test
  public void plainTextMessage_onlyReachesTheTerminal() {
    sink.emit(new TextMessage("You move east.", false));

    verify(controller).appendTerminalText("You move east.\n", TerminalLineKind.NORMAL);
    verifyNoMoreInteractions(controller);
  }

  @Test
  public void initiateFinalExam_switchesToTheExamUi() {
    sink.emit(new InitiateFinalExamDTO(new FinalExamDTO()));

    verify(controller).onSinglePlayerExamStarted();
    verifyNoMoreInteractions(controller);
  }

  @Test
  public void finalExamStartedMessage_isTranscriptOnly() {
    // The exam-start signal is the typed InitiateFinalExamDTO; the marker text is flavour.
    sink.emit(new TextMessage("--- Final Exam Started ---", false));

    verify(controller).appendTerminalText("--- Final Exam Started ---\n", TerminalLineKind.NORMAL);
    verifyNoMoreInteractions(controller);
  }

  @Test
  public void returnToCaseSelection_showsTheCaseSelectionMenu() {
    sink.emit(new ReturnToCaseSelectionDTO());

    verify(controller).showCaseSelectionMenu();
    verifyNoMoreInteractions(controller);
  }

  @Test
  public void returnToCaseSelectionMessage_isTranscriptOnly() {
    // The exit signal is the typed ReturnToCaseSelectionDTO; the message text is flavour.
    sink.emit(new TextMessage("Exiting current case. Returning to case selection.", false));

    verify(controller)
        .appendTerminalText(
            "Exiting current case. Returning to case selection.\n", TerminalLineKind.NORMAL);
    verifyNoMoreInteractions(controller);
  }

  @Test
  public void examQuestion_updatesTheExamView() {
    ExamQuestionDTO question =
        new ExamQuestionDTO(0, 2, "Who is the culprit?", new LinkedHashMap<>(), Map.of());

    sink.emit(question);

    verify(controller).onSinglePlayerQuestionUpdate();
    ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
    verify(controller).appendTerminalText(text.capture(), eq(TerminalLineKind.NORMAL));
    assertTrue(text.getValue().contains("--- EXAM QUESTION 1 of 2 ---"));
    assertTrue(text.getValue().contains("Who is the culprit?"));
    assertFalse(text.getValue().contains("[EXAM_QUESTION]"));
  }

  @Test
  public void examResult_displaysTheResults() {
    sink.emit(new ExamResultDTO());

    verify(controller).onSinglePlayerExamResult();
  }

  @Test
  public void tokenAndDeductionUpdates_driveTheCountersWithoutTerminalNoise() {
    sink.emit(new InsightTokenUpdateDTO(5));
    sink.emit(new DeductionCountUpdateDTO(2));

    verify(controller).onInsightTokensUpdate(5);
    verify(controller).onDeductionCountUpdate(2);
    verifyNoMoreInteractions(controller);
  }

  @Test
  public void cooldownUpdate_drivesTheCooldownCountdown() {
    sink.emit(new CommandCooldownUpdateDTO("combine", 1234L, 300L));

    verify(controller).onCommandCooldownUpdate("combine", 1234L);
    verifyNoMoreInteractions(controller);
  }

  @Test
  public void nullEvent_isIgnored() {
    sink.emit(null);

    verifyNoInteractions(controller);
  }

  @Test
  public void emit_marshalsEveryDispatchThroughTheFxThreadMarshaller() {
    List<Runnable> queued = new ArrayList<>();
    GuiGameOutputSink deferringSink = new GuiGameOutputSink(controller, queued::add);

    deferringSink.emit(new TextMessage("hello", false));

    verifyNoInteractions(controller);
    assertEquals(1, queued.size());
    queued.get(0).run();
    verify(controller).appendTerminalText("hello\n", TerminalLineKind.NORMAL);
  }
}

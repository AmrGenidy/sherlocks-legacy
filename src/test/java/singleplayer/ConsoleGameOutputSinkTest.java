package singleplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import common.dto.DeductionCountUpdateDTO;
import common.dto.DialogueEventDTO;
import common.dto.DialogueType;
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

/**
 * Pins the legacy console text format the single-player engine used to write to {@code System.out}.
 * Issue 02 (.scratch/typed-game-events) relocates this formatting from {@code
 * GameContextSinglePlayer.sendResponseToPlayer} into {@link ConsoleGameOutputSink} verbatim, so
 * terminal play and the legacy GUI stdout-capture path stay byte-for-byte unchanged while the
 * engine starts emitting typed events. These assertions are the contract the GUI rewrite (issue 03)
 * must reproduce.
 */
public class ConsoleGameOutputSinkTest {

  @Test
  public void textMessage_rendersPlainText() {
    assertEquals(
        "You move east.", ConsoleGameOutputSink.render(new TextMessage("You move east.", false)));
  }

  @Test
  public void finalExamStartedText_getsTheExamStartedTag() {
    String rendered =
        ConsoleGameOutputSink.render(new TextMessage("--- Final Exam Started ---", false));
    assertEquals("[EXAM_STARTED] --- Final Exam Started ---", rendered);
  }

  @Test
  public void roomDescription_rendersTheRoomUpdateBlock() {
    Map<String, String> exits = new LinkedHashMap<>();
    exits.put("east", "Terrace");
    RoomDescriptionDTO room =
        new RoomDescriptionDTO(
            "Ballroom", "A grand hall.", List.of("Vase"), List.of("Butler"), exits);

    String rendered = ConsoleGameOutputSink.render(room);

    assertTrue(rendered.startsWith("[ROOM_UPDATE]\n"));
    assertTrue(rendered.contains("--- Ballroom ---"));
    assertTrue(rendered.contains("A grand hall."));
    assertTrue(rendered.contains("Objects: Vase"));
    assertTrue(rendered.contains("Occupants: Butler"));
    assertTrue(rendered.contains("Exits: east (to Terrace)"));
  }

  @Test
  public void roomHeaderUsesDisplayNameWhileObjectsAndOccupantsStayUniversal() {
    // .scratch/gui-localized-case-names: a Room is shown by its Display Name everywhere, but
    // Objects/Occupants stay Universal so the player sees the command-safe names to type.
    Map<String, String> exits = new LinkedHashMap<>();
    exits.put("east", "القاعة"); // exit value is the neighbour Display Name
    RoomDescriptionDTO room =
        new RoomDescriptionDTO(
            "Study", "A quiet study.", List.of("letter"), List.of("LadyEleanor"), exits, null, null,
            null, "المكتب", Map.of("letter", "الرسالة"), Map.of("LadyEleanor", "الليدي إلينور"));

    String rendered = ConsoleGameOutputSink.renderDisplayText(room);

    assertTrue("room header shows the Display Name", rendered.contains("--- المكتب ---"));
    assertTrue("Objects line stays Universal", rendered.contains("Objects: letter"));
    assertTrue("Occupants line stays Universal", rendered.contains("Occupants: LadyEleanor"));
    assertTrue("exit shows the neighbour Display Name", rendered.contains("east (to القاعة)"));
  }

  @Test
  public void emptyRoom_rendersNonePlaceholders() {
    RoomDescriptionDTO room =
        new RoomDescriptionDTO(
            "Cell", "Bare.", new ArrayList<>(), new ArrayList<>(), new LinkedHashMap<>());

    String rendered = ConsoleGameOutputSink.render(room);

    assertTrue(rendered.contains("Objects: None"));
    assertTrue(rendered.contains("Occupants: None"));
    assertTrue(rendered.contains("Exits: None"));
  }

  @Test
  public void dialogueEvent_escapesNewlinesAndPipes() {
    DialogueEventDTO event = new DialogueEventDTO("Watson", "Line one\nA | B", DialogueType.WATSON);

    String rendered = ConsoleGameOutputSink.render(event);

    assertEquals("[DIALOGUE_EVENT] Watson|WATSON|Line one[NEWLINE]A [PIPE] B", rendered);
  }

  @Test
  public void journalEntry_rendersTheJournalUpdateMarker() {
    JournalEntryDTO entry = new JournalEntryDTO("A clue.", "player-1", 0L);
    assertEquals(
        "[JOURNAL UPDATE] Statement added to journal.", ConsoleGameOutputSink.render(entry));
  }

  @Test
  public void examResult_rendersTheExamResultTag() {
    String rendered = ConsoleGameOutputSink.render(new ExamResultDTO());
    assertTrue(rendered.startsWith("[EXAM_RESULT]\n"));
  }

  @Test
  public void stateUpdateDtos_areSuppressedFromConsole() {
    assertNull(ConsoleGameOutputSink.render(new InsightTokenUpdateDTO(5)));
    assertNull(ConsoleGameOutputSink.render(new DeductionCountUpdateDTO(2)));
  }

  @Test
  public void initiateFinalExamDto_isSuppressedFromConsole() {
    // The typed exam-start signal; the console renders the accompanying marker TextMessage.
    assertNull(ConsoleGameOutputSink.render(new InitiateFinalExamDTO(new FinalExamDTO())));
    assertNull(
        ConsoleGameOutputSink.renderDisplayText(new InitiateFinalExamDTO(new FinalExamDTO())));
  }

  @Test
  public void returnToCaseSelectionDto_isSuppressedFromConsole() {
    // The typed exit signal; the console renders the accompanying flavour TextMessage.
    assertNull(ConsoleGameOutputSink.render(new ReturnToCaseSelectionDTO()));
    assertNull(ConsoleGameOutputSink.renderDisplayText(new ReturnToCaseSelectionDTO()));
  }

  @Test
  public void emit_writesRenderedLineToTheWriter() {
    List<String> lines = new ArrayList<>();
    ConsoleGameOutputSink sink = new ConsoleGameOutputSink(lines::add);

    sink.emit(new TextMessage("hello", false));

    assertEquals(List.of("hello"), lines);
  }

  @Test
  public void emit_writesNothingForSuppressedDtos() {
    List<String> lines = new ArrayList<>();
    ConsoleGameOutputSink sink = new ConsoleGameOutputSink(lines::add);

    sink.emit(new InsightTokenUpdateDTO(3));

    assertTrue("token updates produce no console line", lines.isEmpty());
  }
}

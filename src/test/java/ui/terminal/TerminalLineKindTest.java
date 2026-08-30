package ui.terminal;

import static org.junit.Assert.assertEquals;

import common.dto.DialogueEventDTO;
import common.dto.DialogueType;
import common.dto.ExamQuestionDTO;
import common.dto.ExamResultDTO;
import common.dto.JournalEntryDTO;
import common.dto.RoomDescriptionDTO;
import common.dto.TextMessage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Pins the pure, client-side line classification (.scratch/ingame-terminal-polish DEC-3): a typed
 * output DTO maps to a terminal ink colour from its type and the existing {@code isError} flag only
 * — never the (localized) text — so the mapping is i18n-safe and needs no FX toolkit.
 */
public class TerminalLineKindTest {

  @Test
  public void errorTextMessage_isError() {
    assertEquals(TerminalLineKind.ERROR, TerminalLineKind.of(new TextMessage("Nope.", true)));
  }

  @Test
  public void plainTextMessage_isNormal() {
    assertEquals(TerminalLineKind.NORMAL, TerminalLineKind.of(new TextMessage("You move east.", false)));
  }

  // Narrative / structural output is the DEFAULT ink, never petrol (.scratch/terminal-default-colour):
  // room descriptions, journal notices, exam blocks, and dialogue all classify as NORMAL. Only errors
  // are tinted (oxblood); success/contradiction colours stay reserved.

  @Test
  public void roomDescription_isNormal_notPetrol() {
    RoomDescriptionDTO room =
        new RoomDescriptionDTO(
            "Ballroom", "A grand hall.", List.of(), List.of(), new LinkedHashMap<>());
    assertEquals(TerminalLineKind.NORMAL, TerminalLineKind.of(room));
  }

  @Test
  public void journalEntry_isNormal() {
    assertEquals(
        TerminalLineKind.NORMAL, TerminalLineKind.of(new JournalEntryDTO("A clue.", "p1", 0L)));
  }

  @Test
  public void examQuestion_isNormal() {
    ExamQuestionDTO q = new ExamQuestionDTO(0, 1, "Who?", new LinkedHashMap<>(), Map.of());
    assertEquals(TerminalLineKind.NORMAL, TerminalLineKind.of(q));
  }

  @Test
  public void examResult_isNormal() {
    assertEquals(TerminalLineKind.NORMAL, TerminalLineKind.of(new ExamResultDTO()));
  }

  @Test
  public void dialogueEvent_isNormal_notTinted() {
    DialogueEventDTO d = new DialogueEventDTO("Watson", "Hello.", DialogueType.WATSON);
    assertEquals(TerminalLineKind.NORMAL, TerminalLineKind.of(d));
  }

  @Test
  public void nullEvent_isNormal() {
    assertEquals(TerminalLineKind.NORMAL, TerminalLineKind.of(null));
  }

  @Test
  public void everyKind_hasADistinctCssClass() {
    long distinct =
        java.util.Arrays.stream(TerminalLineKind.values())
            .map(TerminalLineKind::cssClass)
            .distinct()
            .count();
    assertEquals(TerminalLineKind.values().length, distinct);
  }
}

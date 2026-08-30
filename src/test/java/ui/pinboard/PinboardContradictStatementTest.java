package ui.pinboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;
import org.junit.Test;
import ui.pinboard.PinboardItemModel.ItemType;

/**
 * Regression for .scratch/gui-pinboard-contradict-statement: the board's Contradict must accept a
 * suspect's STATEMENT card (resolved to its owning suspect) OR a SUSPECT card, plus an evidence
 * card — so the statement↔evidence pair the player just linked (and the tutorial instructs) works.
 * Tests the pure resolver {@link PinboardController#contradictCommandFor}; the terminal command is
 * unchanged ({@code contradict <evidence> with <suspect>}; ContradictCommand resolves by name OR
 * id).
 */
public class PinboardContradictStatementTest {

  private static PinboardItemModel item(String refId, String title, ItemType type) {
    PinboardItemModel m = new PinboardItemModel();
    m.setRelatedJournalEntryId(refId);
    m.setTitle(title);
    m.setType(type);
    return m;
  }

  @Test
  public void statementCardPlusEvidenceResolvesToTheSuspect() {
    // A dropped SUSPECT_STATEMENT becomes ItemType.EVIDENCE with id stmt:<suspectId>:<state>.
    PinboardItemModel statement =
        item("stmt:the_valet:default", "The Valet — statement", ItemType.EVIDENCE);
    PinboardItemModel clue = item("clue:muddy_boot", "Muddy boot", ItemType.EVIDENCE);

    assertEquals(
        "selecting a statement + evidence must contradict the statement's suspect (by id)",
        "contradict clue:muddy_boot with the_valet",
        PinboardController.contradictCommandFor(List.of(statement, clue)));
    // Order-independent.
    assertEquals(
        "contradict clue:muddy_boot with the_valet",
        PinboardController.contradictCommandFor(List.of(clue, statement)));
  }

  @Test
  public void suspectCardPlusEvidenceStillWorks() {
    PinboardItemModel suspect = item(null, "The Valet", ItemType.SUSPECT);
    PinboardItemModel clue = item("clue:muddy_boot", "Muddy boot", ItemType.EVIDENCE);

    assertEquals(
        "a suspect card + evidence must still contradict the suspect (by name)",
        "contradict clue:muddy_boot with The Valet",
        PinboardController.contradictCommandFor(List.of(suspect, clue)));
  }

  @Test
  public void invalidPairsReturnNull() {
    PinboardItemModel clueA = item("clue:a", "A", ItemType.EVIDENCE);
    PinboardItemModel clueB = item("clue:b", "B", ItemType.EVIDENCE);
    PinboardItemModel stmtX = item("stmt:x:default", "X", ItemType.EVIDENCE);
    PinboardItemModel stmtY = item("stmt:y:default", "Y", ItemType.EVIDENCE);

    assertNull(
        "two evidence cards: no suspect side",
        PinboardController.contradictCommandFor(List.of(clueA, clueB)));
    assertNull(
        "two statements: no evidence side",
        PinboardController.contradictCommandFor(List.of(stmtX, stmtY)));
    assertNull("wrong count", PinboardController.contradictCommandFor(List.of(clueA)));
  }
}

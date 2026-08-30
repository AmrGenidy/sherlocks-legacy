package ui.review;

import static org.junit.Assert.*;

import common.dto.JournalEntryDTO;
import common.dto.JournalEntryType;
import common.dto.pinboard.PinboardItemDTO;
import common.dto.pinboard.PinboardStateDTO;
import common.dto.save.CompletedCaseRecord;
import java.util.List;
import org.junit.Test;

/**
 * The pure, FX-free review semantics behind the read-only Review viewer: a detailed record offers
 * its Journal and Pinboard for display, while a migrated (detail-less) record offers nothing to
 * review and must fall back to a graceful message. The model exposes display data only — there is
 * no mutation or command path, so Review is read-only by construction.
 */
public class CaseReviewModelTest {

  private static CompletedCaseRecord detailedRecord() {
    PinboardStateDTO pinboard = new PinboardStateDTO();
    PinboardItemDTO item = new PinboardItemDTO();
    item.setId("i1");
    pinboard.setItems(List.of(item));
    List<JournalEntryDTO> journal =
        List.of(
            new JournalEntryDTO(
                "clue:1", JournalEntryType.CLUE, "obj", "Knife", "Still wet", "p1", 1L));
    return CompletedCaseRecord.detailed(
        "The Sapphire Affair", "Master Detective", 4, 5, 5, 1700000000000L, journal, pinboard);
  }

  @Test
  public void detailedRecordExposesJournalAndPinboardForReview() {
    CaseReviewModel model = new CaseReviewModel(detailedRecord());

    assertTrue(model.hasDetail());
    assertEquals("Master Detective", model.getRankName());
    assertEquals(Integer.valueOf(4), model.getDeductionsUsed());
    assertEquals(Integer.valueOf(5), model.getFinalExamScore());
    assertEquals(Integer.valueOf(5), model.getFinalExamTotal());
    assertNotNull(model.getDateSolvedEpochMillis());

    List<String> lines = model.journalLines();
    assertEquals(1, lines.size());
    assertTrue("journal line uses the existing entry rendering", lines.get(0).contains("Knife"));

    assertTrue(model.hasPinboard());
    assertEquals(1, model.getPinboard().getItems().size());
  }

  @Test
  public void migratedRecordHasNothingToReview() {
    CaseReviewModel model = new CaseReviewModel(CompletedCaseRecord.migrated("Old Case"));

    assertFalse(model.hasDetail());
    assertTrue(model.journalLines().isEmpty());
    assertFalse(model.hasPinboard());
    assertNull(model.getRankName());
    assertNull(model.getDeductionsUsed());
  }
}

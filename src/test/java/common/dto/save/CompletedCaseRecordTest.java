package common.dto.save;

import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import common.dto.JournalEntryDTO;
import common.dto.JournalEntryType;
import common.dto.pinboard.PinboardItemDTO;
import common.dto.pinboard.PinboardStateDTO;
import java.util.List;
import org.junit.Test;

/**
 * Behaviour of the pure-data {@link CompletedCaseRecord}: it survives a JSON round-trip without
 * loss, distinguishes a detailed solve from a migrated stub, and orders solves by deductions used.
 */
public class CompletedCaseRecordTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static PinboardStateDTO samplePinboard() {
    PinboardStateDTO state = new PinboardStateDTO();
    PinboardItemDTO item = new PinboardItemDTO();
    item.setId("item-1");
    item.setType("EVIDENCE");
    item.setTitle("Bloody knife");
    item.setX(12.5);
    item.setY(40.0);
    state.setItems(List.of(item));
    return state;
  }

  private static List<JournalEntryDTO> sampleJournal() {
    return List.of(
        new JournalEntryDTO(
            "clue:1", JournalEntryType.CLUE, "obj-knife", "Knife", "Still wet", "p1", 1000L));
  }

  @Test
  public void detailedRecordSurvivesJsonRoundTrip() throws Exception {
    CompletedCaseRecord original =
        CompletedCaseRecord.detailed(
            "The Sapphire Affair",
            "Master Detective",
            4,
            5,
            5,
            1700000000000L,
            sampleJournal(),
            samplePinboard());

    String json = MAPPER.writeValueAsString(original);
    CompletedCaseRecord reread = MAPPER.readValue(json, CompletedCaseRecord.class);

    // Re-serialization is stable -> no field was dropped or reshaped on the way through.
    assertEquals(json, MAPPER.writeValueAsString(reread));

    assertEquals("The Sapphire Affair", reread.getUniversalTitle());
    assertEquals("Master Detective", reread.getRankName());
    assertEquals(Integer.valueOf(4), reread.getDeductionsUsed());
    assertEquals(Integer.valueOf(5), reread.getFinalExamScore());
    assertEquals(1, reread.getJournal().size());
    assertEquals("Knife", reread.getJournal().get(0).getTitle());
    assertNotNull(reread.getPinboard());
    assertEquals(1, reread.getPinboard().getItems().size());
    assertEquals(CompletedCaseRecord.CURRENT_FORMAT_VERSION, reread.getFormatVersion());
    assertTrue(reread.hasDetail());
  }

  @Test
  public void migratedRecordHasNoDetailButStillRoundTrips() throws Exception {
    CompletedCaseRecord migrated = CompletedCaseRecord.migrated("Murder at the Manor");

    String json = MAPPER.writeValueAsString(migrated);
    CompletedCaseRecord reread = MAPPER.readValue(json, CompletedCaseRecord.class);

    assertEquals(json, MAPPER.writeValueAsString(reread));
    assertEquals("Murder at the Manor", reread.getUniversalTitle());
    assertFalse(reread.hasDetail());
    assertNull(reread.getRankName());
    assertNull(reread.getDeductionsUsed());
    assertNull(reread.getPinboard());
    assertTrue(reread.getJournal().isEmpty());
  }

  @Test
  public void detailedRecordSeedsBestResultFromItsOwnRun() {
    CompletedCaseRecord r =
        CompletedCaseRecord.detailed("c", "Master Detective", 3, 5, 5, 1L, null, null);
    assertEquals("Master Detective", r.getBestRankName());
    assertEquals(Integer.valueOf(3), r.getBestDeductionsUsed());
  }

  /**
   * An old (format v1) record on disk has no best-result fields; loading it must seed the Best
   * Result from the stored latest run so the seal still shows a rank
   * (.scratch/completed-case-records DEC-9).
   */
  @Test
  public void oldRecordMigratesBestResultFromStoredRankAndDeductions() throws Exception {
    String legacyJson =
        "{\"formatVersion\":1,\"universalTitle\":\"c\",\"rankName\":\"Keen Investigator\","
            + "\"deductionsUsed\":4,\"finalExamScore\":5,\"finalExamTotal\":5,"
            + "\"dateSolvedEpochMillis\":1700000000000}";

    CompletedCaseRecord migrated = MAPPER.readValue(legacyJson, CompletedCaseRecord.class);

    assertEquals("Keen Investigator", migrated.getBestRankName());
    assertEquals(Integer.valueOf(4), migrated.getBestDeductionsUsed());
    // Latest-finish fields are untouched by migration.
    assertEquals("Keen Investigator", migrated.getRankName());
    assertEquals(Integer.valueOf(4), migrated.getDeductionsUsed());
  }

  @Test
  public void fewerDeductionsIsBetterAndAnyRealSolveBeatsMigrated() {
    CompletedCaseRecord four =
        CompletedCaseRecord.detailed("c", "Inspector", 4, 5, 5, 1L, null, null);
    CompletedCaseRecord six =
        CompletedCaseRecord.detailed("c", "Constable", 6, 5, 5, 1L, null, null);
    CompletedCaseRecord migrated = CompletedCaseRecord.migrated("c");

    assertTrue(four.isBetterThan(six));
    assertFalse(six.isBetterThan(four));
    // Equal deductions is not an improvement -> a record is never worsened by an equal replay.
    assertFalse(
        four.isBetterThan(CompletedCaseRecord.detailed("c", "Inspector", 4, 5, 5, 9L, null, null)));
    // A migrated stub (unknown deductions = worst) is beaten by any real solve and beats nothing.
    assertTrue(six.isBetterThan(migrated));
    assertFalse(migrated.isBetterThan(four));
  }
}

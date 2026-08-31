package client.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import common.dto.ExamResultDTO;
import common.dto.JournalEntryDTO;
import common.dto.JournalEntryType;
import common.dto.pinboard.PinboardItemDTO;
import common.dto.pinboard.PinboardStateDTO;
import common.dto.save.CompletedCaseRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;

/**
 * Multiplayer save model (docs/SAVE_AND_PROFILE.md — "each player records their own MP solve").
 *
 * <p>On a solved MP Final Exam BOTH clients write a Completed-Case Record on their OWN machine, keyed
 * to their OWN local profile, built from THAT client's final Journal + Pinboard — not host-only. Each
 * machine is its own {@link CompletedCaseStore} file, so the guest's record is independent of the
 * host's, and keep-the-best applies per local store. A record an MP solve writes must also load back
 * (Journal + Pinboard intact) for the unified single-player review mode (.scratch/gui-review-enter-case).
 */
public class MultiplayerRecordTest {

  private static final String TITLE = "The Sapphire Affair";

  private static ExamResultDTO solvedResult(String rank) {
    return new ExamResultDTO(5, 5, "Solved.", rank, List.of(), "Bravo!", true);
  }

  private static List<JournalEntryDTO> journal(String clueText) {
    return List.of(
        new JournalEntryDTO("clue:1", JournalEntryType.CLUE, "obj-knife", "Knife", clueText, "p", 1L));
  }

  private static PinboardStateDTO pinboard(String itemTitle) {
    PinboardStateDTO state = new PinboardStateDTO();
    PinboardItemDTO item = new PinboardItemDTO();
    item.setId("item-1");
    item.setType("EVIDENCE");
    item.setTitle(itemTitle);
    state.setItems(List.of(item));
    return state;
  }

  /** A record an MP solve writes round-trips through the store with its Journal + Pinboard — what SP review reads. */
  @Test
  public void mpSolveRecordRoundTripsThroughTheStoreForReview() throws Exception {
    Path file = Files.createTempDirectory("ccs-mp").resolve("records.json");
    CompletedCaseStore store = new CompletedCaseStore(file);

    CompletedCaseRecord built =
        CompletedCaseRecord.fromExamResult(
            TITLE, solvedResult("Inspector"), 4, journal("Still wet"), pinboard("Bloody knife"), 1L);
    store.save(built);

    // A fresh store over the same file (a later launch entering review) loads the full detail.
    CompletedCaseRecord loaded = new CompletedCaseStore(file).find(TITLE).orElseThrow();
    assertTrue(loaded.hasDetail());
    assertEquals(1, loaded.getJournal().size());
    assertEquals("Still wet", loaded.getJournal().get(0).getText());
    assertEquals(1, loaded.getPinboard().getItems().size());
    assertEquals("Bloody knife", loaded.getPinboard().getItems().get(0).getTitle());
  }

  /**
   * The same MP solve writes to TWO machines: host and guest each persist to their own store, from
   * their own Journal. Each record is independent, and keep-the-best is per local store.
   */
  @Test
  public void hostAndGuestEachKeepTheirOwnIndependentRecord() throws Exception {
    Path hostFile = Files.createTempDirectory("ccs-host").resolve("records.json");
    Path guestFile = Files.createTempDirectory("ccs-guest").resolve("records.json");
    CompletedCaseStore host = new CompletedCaseStore(hostFile);
    CompletedCaseStore guest = new CompletedCaseStore(guestFile);

    // One co-op solve; each client builds its record from its OWN cached journal/pinboard.
    host.save(
        CompletedCaseRecord.fromExamResult(
            TITLE, solvedResult("Inspector"), 5, journal("Host's notes"), pinboard("Host board"), 1L));
    guest.save(
        CompletedCaseRecord.fromExamResult(
            TITLE, solvedResult("Inspector"), 5, journal("Guest's notes"), pinboard("Guest board"), 1L));

    // Both have the seal, each over its own detail.
    assertEquals("Host's notes", host.find(TITLE).orElseThrow().getJournal().get(0).getText());
    assertEquals("Guest's notes", guest.find(TITLE).orElseThrow().getJournal().get(0).getText());

    // Keep-the-best is per local store: the guest improving their record never touches the host's.
    guest.save(
        CompletedCaseRecord.fromExamResult(
            TITLE, solvedResult("Master Detective"), 2, journal("Guest replay"), pinboard("Guest board"), 2L));
    assertEquals(Integer.valueOf(2), guest.find(TITLE).orElseThrow().getDeductionsUsed());
    assertEquals(Integer.valueOf(5), host.find(TITLE).orElseThrow().getDeductionsUsed());
  }
}

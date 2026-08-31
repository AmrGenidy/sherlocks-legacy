package engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import common.WireLimits;
import common.dto.JournalEntryDTO;
import common.dto.JournalEntryType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Engine-side growth and size caps on client-suppliable state (security-pass issue 02), asserted
 * against both contexts: the shared journal stops growing at its ceiling, and an implausibly
 * large exam answer map is rejected without advancing the exam.
 */
@RunWith(Parameterized.class)
public class WireAbuseContractTest {

  private static final Map<String, String> CORRECT = Map.of("slot1", "s1_opt1", "slot2", "s2_opt1");

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> contexts() {
    return ContextHarnessFactory.both();
  }

  @Parameterized.Parameter public ContextHarnessFactory factory;

  private ContextHarness sapphire() {
    return factory.start(EngineFixtures.sapphire());
  }

  private static JournalEntryDTO note(ContextHarness h, int i) {
    return new JournalEntryDTO(
        "note:" + i, JournalEntryType.NOTE, "player", "Note", "note " + i, h.playerId(), i);
  }

  @Test
  public void journalStopsGrowingAtTheCeiling() {
    ContextHarness h = sapphire();
    int alreadyPresent = h.context().getJournalEntries(h.playerId()).size();

    for (int i = 0; i < WireLimits.MAX_JOURNAL_ENTRIES + 10; i++) {
      h.context().addJournalEntry(note(h, i));
    }

    assertEquals(
        "the shared journal must be capped (notes have unique ids and would grow forever)",
        WireLimits.MAX_JOURNAL_ENTRIES,
        h.context().getJournalEntries(h.playerId()).size());
    // Sanity: the cap was reached by adding, not pre-existing state.
    assertEquals(0, alreadyPresent);
  }

  @Test
  public void oversizedExamAnswerMapIsRejectedWithoutAdvancingTheExam() {
    ContextHarness h = sapphire();
    h.context().startExamProcess(h.playerId());

    Map<String, String> bloated = new HashMap<>();
    for (int i = 0; i <= WireLimits.MAX_EXAM_ANSWER_ENTRIES; i++) {
      bloated.put("slot" + i, "answer" + i);
    }
    h.context().processSubmitQuestionAnswer(h.playerId(), 0, bloated);

    assertNull("the rejected submission must not score the exam", h.lastExamResult());

    // The exam is still on the same question: a well-formed answer scores normally.
    h.context().processSubmitQuestionAnswer(h.playerId(), 0, CORRECT);
    assertNotNull(h.lastExamResult());
    assertEquals(1, h.lastExamResult().getScore());
  }
}

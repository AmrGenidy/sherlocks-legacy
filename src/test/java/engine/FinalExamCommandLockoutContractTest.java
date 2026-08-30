package engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import common.commands.ExamineCommand;
import common.commands.JournalAddCommand;
import common.commands.MoveCommand;
import common.dto.JournalEntryType;
import common.dto.TextMessage;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Final Exam command lockout (.scratch/exam-command-lockout): while a Final Exam is in progress
 * ({@code examActive == true}) every gameplay/action command is refused by the engine authority in
 * BOTH contexts, while Journal stays usable; once the exam is scored every command works again.
 *
 * <p>The gate lives at the shared dispatch chokepoint ({@code BaseCommand.execute}), so the
 * single-player and multiplayer adapters honour it by construction (ADR-0001).
 */
@RunWith(Parameterized.class)
public class FinalExamCommandLockoutContractTest {

  private static final Map<String, String> CORRECT = Map.of("slot1", "s1_opt1", "slot2", "s2_opt1");

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> contexts() {
    return ContextHarnessFactory.both();
  }

  @Parameterized.Parameter public ContextHarnessFactory factory;

  private ContextHarness sapphire() {
    return factory.start(EngineFixtures.sapphire());
  }

  @Test
  public void moveIsBlockedWhileTheExamIsActive() {
    ContextHarness h = sapphire();
    assertEquals("Ballroom", h.currentRoom().getName());

    h.context().startExamProcess(h.playerId());
    assertTrue(h.context().isExamActive());

    h.execute(new MoveCommand("east")); // Ballroom -> Terrace, normally valid

    assertEquals(
        "an active Final Exam must block movement in both modes",
        "Ballroom",
        h.currentRoom().getName());
    assertTrue("a blocked command emits an in-world refusal", lastIsErrorTextMessage(h));
  }

  @Test
  public void examineIsBlockedWhileTheExamIsActive() {
    ContextHarness h = sapphire();
    h.context().startExamProcess(h.playerId());

    h.execute(new ExamineCommand("shattered_glass"));

    assertNull(
        "examine must not record a clue while the exam is active",
        h.context().getJournalEntryById(h.playerId(), "clue:shattered_glass"));
  }

  @Test
  public void journalAddStaysAllowedWhileTheExamIsActive() {
    ContextHarness h = sapphire();
    h.context().startExamProcess(h.playerId());

    h.execute(new JournalAddCommand("The countess seemed nervous."));

    assertEquals(
        "the Journal is a reference tool the detective keeps during the exam",
        1,
        h.context().getJournalEntriesByType(h.playerId(), JournalEntryType.NOTE).size());
  }

  @Test
  public void commandsWorkAgainAfterTheExamIsScored() {
    ContextHarness h = sapphire();
    h.context().startExamProcess(h.playerId());
    h.context().processSubmitQuestionAnswer(h.playerId(), 0, CORRECT);
    assertFalse("scoring ends the exam", h.context().isExamActive());

    h.execute(new MoveCommand("east"));

    assertEquals(
        "once the result is showing, navigation works unchanged",
        "Terrace",
        h.currentRoom().getName());
  }

  private static boolean lastIsErrorTextMessage(ContextHarness h) {
    Serializable last = null;
    for (Serializable dto : h.playerResponses()) {
      last = dto;
    }
    return last instanceof TextMessage tm && tm.isError();
  }
}

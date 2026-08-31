package engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import common.commands.ExamineCommand;
import common.commands.InitiateFinalExamCommand;
import common.commands.LookCommand;
import common.commands.MoveCommand;
import common.dto.JournalEntryDTO;
import common.dto.TextMessage;
import java.io.Serializable;
import org.junit.Test;

/**
 * Review Session contract (.scratch/gui-review-enter-case; CONTEXT.md "Review Session"). "Review
 * investigation" enters the solved case as a non-destructive, in-process Single-player Session:
 *
 * <ul>
 *   <li>the saved Journal seeds into the engine ({@code seedReviewJournal});
 *   <li>navigation (move/look) works so the player can walk the solved case;
 *   <li>gameplay mutations (examine-adds-entry, …) are no-ops with an in-world refusal;
 *   <li>the Final Exam cannot start, so the solve hook never fires and the saved record is never
 *       re-written — read-only by construction.
 * </ul>
 *
 * <p>Review is a single-player / host-local concern, so this is an SP-context test (the gate
 * mirrors the Final-Exam command lockout at {@code BaseCommand.execute}; ADR-0001).
 */
public class ReviewModeContractTest {

  private ContextHarness sapphire() {
    return ContextHarnessFactory.SINGLE_PLAYER.start(EngineFixtures.sapphire());
  }

  @Test
  public void seedReviewJournalLoadsTheSavedEntries() {
    ContextHarness h = sapphire();
    JournalEntryDTO saved = new JournalEntryDTO("The countess seemed nervous.", h.playerId(), 1L);

    h.context().seedReviewJournal(java.util.List.of(saved));

    boolean present =
        h.context().getJournalEntries(h.playerId()).stream()
            .anyMatch(e -> "The countess seemed nervous.".equals(e.getText()));
    assertTrue("the saved journal must seed into the review session", present);
  }

  @Test
  public void examineIsANoOpInReviewMode() {
    ContextHarness h = sapphire();
    h.context().setReviewMode(true);

    h.execute(new ExamineCommand("shattered_glass"));

    assertNull(
        "examine must not record a clue while reviewing",
        h.context().getJournalEntryById(h.playerId(), "clue:shattered_glass"));
    assertTrue("a blocked command emits an in-world refusal", lastIsErrorTextMessage(h));
  }

  @Test
  public void moveWorksInReviewMode() {
    ContextHarness h = sapphire();
    assertEquals("Ballroom", h.currentRoom().getName());

    h.context().setReviewMode(true);
    h.execute(new MoveCommand("east")); // Ballroom -> Terrace

    assertEquals(
        "the player can walk the solved case while reviewing",
        "Terrace",
        h.currentRoom().getName());
  }

  @Test
  public void lookWorksInReviewMode() {
    ContextHarness h = sapphire();
    h.context().setReviewMode(true);

    h.execute(new LookCommand());

    assertFalse("look is a reference/navigation command, never refused", lastIsErrorTextMessage(h));
  }

  /**
   * Regression (.scratch/gui-review-enter-case): "Play again" on a solved case reuses the same
   * single-player context that Review put into review mode, and starts by loading the case afresh
   * (initializeCase → {@code resetForNewCaseLoad}). That reload must clear review mode, otherwise
   * the fresh replay inherits Review's command lockout (examine/question/exam disabled).
   */
  @Test
  public void newCaseLoadClearsReviewModeSoReplayIsInteractive() {
    singleplayer.GameContextSinglePlayer ctx = new singleplayer.GameContextSinglePlayer();
    ctx.setReviewMode(true);

    ctx.resetForNewCaseLoad(); // what "Play again" triggers on the reused context

    assertFalse(
        "a fresh case load must clear review mode so Play again is fully interactive",
        ctx.isReviewMode());
  }

  @Test
  public void finalExamCannotStartInReviewMode() {
    ContextHarness h = sapphire();
    h.context().setReviewMode(true);

    h.execute(new InitiateFinalExamCommand());

    assertFalse(
        "review never starts the exam, so the solve hook never fires and the record is never written",
        h.context().isExamActive());
  }

  private static boolean lastIsErrorTextMessage(ContextHarness h) {
    Serializable last = null;
    for (Serializable dto : h.playerResponses()) {
      last = dto;
    }
    return last instanceof TextMessage tm && tm.isError();
  }
}

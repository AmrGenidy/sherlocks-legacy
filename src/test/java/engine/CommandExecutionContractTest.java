package engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import Core.Suspect.SuspectState;
import common.commands.CombineCommand;
import common.commands.ContradictCommand;
import common.commands.DeduceCommand;
import common.commands.ExamineCommand;
import common.commands.JournalAddCommand;
import common.commands.LookCommand;
import common.commands.MoveCommand;
import common.commands.QuestionCommand;
import common.dto.JournalEntryDTO;
import common.dto.JournalEntryType;
import common.dto.RoomDescriptionDTO;
import java.io.Serializable;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * One behavioural contract for command execution, run against BOTH {@code GameActionContext}
 * implementations.
 *
 * <p>Fixture: the bundled "The Stolen Sapphire" case, loaded through {@code CaseLoader}.
 */
@RunWith(Parameterized.class)
public class CommandExecutionContractTest {

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> contexts() {
    return ContextHarnessFactory.both();
  }

  @Parameterized.Parameter public ContextHarnessFactory factory;

  private ContextHarness sapphire() {
    return factory.start(EngineFixtures.sapphire());
  }

  // --- move ---------------------------------------------------------------

  @Test
  public void moveToValidNeighbourChangesRoom() {
    ContextHarness h = sapphire();
    assertEquals("Ballroom", h.currentRoom().getName());

    h.execute(new MoveCommand("east"));

    assertEquals("Terrace", h.currentRoom().getName());
  }

  @Test
  public void moveInInvalidDirectionKeepsRoom() {
    ContextHarness h = sapphire();
    assertEquals("Ballroom", h.currentRoom().getName());

    h.execute(new MoveCommand("north")); // Ballroom only exits east

    assertEquals("Ballroom", h.currentRoom().getName());
  }

  @Test
  public void caseStartsWithPlayerInStartingRoom() {
    ContextHarness h = sapphire();
    assertNotNull(h.currentRoom());
    assertEquals("Ballroom", h.currentRoom().getName());
    assertTrue(h.context().isCaseStarted());
  }

  // --- look ---------------------------------------------------------------

  @Test
  public void lookDescribesTheCurrentRoom() {
    ContextHarness h = sapphire();

    h.execute(new LookCommand());

    RoomDescriptionDTO room = lastRoomDescription(h);
    assertNotNull("look should emit a room description", room);
    assertEquals("Ballroom", room.getName());
    assertTrue(room.getObjectNames().contains("shattered_glass"));
    assertTrue("Ballroom exits east to the Terrace", room.getExits().containsKey("east"));
  }

  /**
   * Regression (.scratch/ingame-fixes-2 issue 01): {@code look} must re-emit the CURRENT room's
   * imagePath so the GUI does not blank the room background. The bug was {@code LookCommand} building
   * a partial DTO (no imagePath) via the 5-arg constructor; it now routes through the canonical
   * {@code createRoomDescriptionDTO}, identical to the room-entry path.
   */
  @Test
  public void lookCarriesTheRoomImageSoTheViewDoesNotBlank() {
    ContextHarness h = sapphire();
    String roomImage = h.currentRoom().getImagePath();
    assertNotNull("fixture room should declare an imagePath", roomImage);

    h.execute(new LookCommand());

    RoomDescriptionDTO room = lastRoomDescription(h);
    assertNotNull(room);
    assertEquals(
        "look must re-emit the current room's image so RoomView keeps it",
        roomImage,
        room.getImagePath());
  }

  private static RoomDescriptionDTO lastRoomDescription(ContextHarness h) {
    RoomDescriptionDTO last = null;
    for (Serializable dto : h.playerResponses()) {
      if (dto instanceof RoomDescriptionDTO rd) {
        last = rd;
      }
    }
    return last;
  }

  // --- examine ------------------------------------------------------------

  @Test
  public void examineKnownObjectRecordsClueInJournal() {
    ContextHarness h = sapphire();

    h.execute(new ExamineCommand("shattered_glass"));

    JournalEntryDTO clue = h.context().getJournalEntryById(h.playerId(), "clue:shattered_glass");
    assertNotNull("examine should record a clue", clue);
    assertEquals(JournalEntryType.CLUE, clue.getType());
    assertEquals("shattered_glass", clue.getSourceId());
    assertTrue(clue.getText().contains("broken from the inside"));
  }

  @Test
  public void examineUnknownObjectRecordsNothing() {
    ContextHarness h = sapphire();
    int before = h.context().getJournalEntries(h.playerId()).size();

    h.execute(new ExamineCommand("unicorn"));

    assertEquals(before, h.context().getJournalEntries(h.playerId()).size());
  }

  // --- question -----------------------------------------------------------

  @Test
  public void questionSuspectReturnsCurrentStateStatement() {
    ContextHarness h = sapphire();
    h.bringSuspectToPlayer("LordAshworth");

    h.execute(new QuestionCommand("LordAshworth"));

    JournalEntryDTO stmt =
        h.context().getJournalEntryById(h.playerId(), "stmt:lordashworth:default");
    assertNotNull("question should record the suspect statement", stmt);
    assertEquals(JournalEntryType.SUSPECT_STATEMENT, stmt.getType());
    // LIE is the initial state for Lord Ashworth.
    assertTrue(stmt.getText().contains("speaking with the ambassador"));
  }

  @Test
  public void questionSuspectNotInRoomRecordsNothing() {
    ContextHarness h = sapphire();
    // Pin Lord Ashworth to the Terrace; the player is in the Ballroom.
    h.suspect("LordAshworth").setCurrentRoom(h.context().getRoomByName("Terrace"));
    int before = h.context().getJournalEntries(h.playerId()).size();

    h.execute(new QuestionCommand("LordAshworth"));

    assertEquals(before, h.context().getJournalEntries(h.playerId()).size());
  }

  // --- deduce -------------------------------------------------------------

  @Test
  public void deduceObjectRecordsDeductionAndCountsAgainstRank() {
    ContextHarness h = sapphire();
    assertEquals(0, h.context().getSessionDeduceCount());

    h.execute(new DeduceCommand("shattered_glass"));

    JournalEntryDTO deduction =
        h.context().getJournalEntryById(h.playerId(), "ded:shattered_glass");
    assertNotNull(deduction);
    assertEquals(JournalEntryType.DEDUCTION, deduction.getType());
    // With no insight tokens, a deduction spends from the rank budget instead.
    assertEquals(1, h.context().getSessionDeduceCount());
  }

  @Test
  public void deduceMissingTargetIsRejectedAndCostsNothing() {
    ContextHarness h = sapphire();

    h.execute(new DeduceCommand("unicorn"));

    assertEquals(0, h.context().getSessionDeduceCount());
    assertTrue(h.context().getJournalEntries(h.playerId()).isEmpty());
  }

  @Test
  public void deduceSameObjectTwiceCountsOnlyOnce() {
    ContextHarness h = sapphire();

    h.execute(new DeduceCommand("shattered_glass"));
    h.execute(new DeduceCommand("shattered_glass")); // recalled, not re-charged

    assertEquals(1, h.context().getSessionDeduceCount());
  }

  // --- contradict (single LIE->TRUTH leg; full chain in the FSM contract test) ---

  @Test
  public void contradictWithMatchingEvidenceAdvancesStateAndRewards() {
    ContextHarness h = sapphire();
    h.execute(new MoveCommand("east"));
    h.execute(new ExamineCommand("cigar_stub")); // discover the evidence first
    h.bringSuspectToPlayer("LordAshworth");
    assertEquals(SuspectState.LIE, h.suspect("LordAshworth").getCurrentState());

    h.execute(new ContradictCommand("LordAshworth", "cigar_stub"));

    assertEquals(SuspectState.TRUTH, h.suspect("LordAshworth").getCurrentState());
    assertNotNull(
        "a successful contradiction unlocks its reward deduction",
        h.context().getJournalEntryById(h.playerId(), "ded:ashworth_opportunity"));
    assertEquals("a successful contradiction awards 1 insight token", 1, h.tokens());
  }

  @Test
  public void contradictRequiresTheSuspectToBePresent() {
    // Security-pass issue 06: room adjacency is server state — contradicting a suspect who is
    // not in the player's room must not fire (no state transition, no token), matching
    // question/deduce semantics.
    ContextHarness h = sapphire();
    h.execute(new MoveCommand("east"));
    h.execute(new ExamineCommand("cigar_stub")); // discover the evidence in the suspect's room
    // Leave: LordAshworth stays in the Terrace (NPCs are pinned while sharing a room and never
    // move into the player's new room), so he is deterministically absent now.
    h.execute(new MoveCommand("west"));
    int tokensBefore = h.tokens();

    h.execute(new ContradictCommand("LordAshworth", "cigar_stub"));

    assertEquals(
        "contradicting an absent suspect must not transition his state",
        SuspectState.LIE,
        h.suspect("LordAshworth").getCurrentState());
    assertEquals("no reward may be paid for an absent suspect", tokensBefore, h.tokens());
  }

  @Test
  public void contradictWithWrongEvidenceIsRejectedAndKeepsState() {
    ContextHarness h = sapphire();
    h.bringSuspectToPlayer("LordAshworth");

    h.execute(new ContradictCommand("LordAshworth", "shattered_glass")); // no rule in LIE

    assertEquals(SuspectState.LIE, h.suspect("LordAshworth").getCurrentState());
    assertEquals(0, h.tokens());
  }

  // --- combine ------------------------------------------------------------

  @Test
  public void combineMatchingNotesYieldsDeductionAndTokenReward() {
    ContextHarness h = sapphire();
    discoverBothCombinables(h);

    h.execute(new CombineCommand("shattered_glass", "cigar_stub"));

    JournalEntryDTO combined =
        h.context().getJournalEntryById(h.playerId(), "ded:glass_plus_cigar_insight");
    assertNotNull("combine should record the derived deduction", combined);
    assertEquals(1, h.tokens()); // tokenReward == 1
  }

  @Test
  public void combineIsRejectedWhenNotesAreNotBothDiscovered() {
    ContextHarness h = sapphire();
    h.execute(new ExamineCommand("shattered_glass")); // only one of the pair

    h.execute(new CombineCommand("shattered_glass", "cigar_stub"));

    assertNull(h.context().getJournalEntryById(h.playerId(), "ded:glass_plus_cigar_insight"));
    assertEquals(0, h.tokens());
  }

  @Test
  public void nonRepeatableCombineDoesNotRewardTwice() {
    ContextHarness h = sapphire();
    discoverBothCombinables(h);

    h.execute(new CombineCommand("shattered_glass", "cigar_stub"));
    h.execute(new CombineCommand("shattered_glass", "cigar_stub")); // repeatable:false

    assertEquals("repeatable:false combine must not pay out twice", 1, h.tokens());
  }

  // --- journal add / filter ----------------------------------------------

  @Test
  public void journalAddStoresNoteRetrievableByType() {
    ContextHarness h = sapphire();

    h.execute(new JournalAddCommand("The countess seemed nervous."));

    List<JournalEntryDTO> notes =
        h.context().getJournalEntriesByType(h.playerId(), JournalEntryType.NOTE);
    assertEquals(1, notes.size());
    assertEquals("The countess seemed nervous.", notes.get(0).getText());
  }

  @Test
  public void journalFilterSeparatesCluesFromNotes() {
    ContextHarness h = sapphire();

    h.execute(new ExamineCommand("shattered_glass"));
    h.execute(new JournalAddCommand("A handwritten note."));

    assertEquals(
        1, h.context().getJournalEntriesByType(h.playerId(), JournalEntryType.CLUE).size());
    assertEquals(
        1, h.context().getJournalEntriesByType(h.playerId(), JournalEntryType.NOTE).size());
  }

  /** Examines both objects that form the sapphire combine rule (one per room). */
  private static void discoverBothCombinables(ContextHarness h) {
    h.execute(new ExamineCommand("shattered_glass")); // Ballroom
    h.execute(new MoveCommand("east"));
    assertEquals("Terrace", h.currentRoom().getName());
    h.execute(new ExamineCommand("cigar_stub")); // Terrace
  }
}

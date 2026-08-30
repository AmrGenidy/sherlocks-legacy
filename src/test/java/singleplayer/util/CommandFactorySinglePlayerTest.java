package singleplayer.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import common.commands.Command;
import common.commands.ContradictCommand;
import common.commands.InitiateFinalExamCommand;
import org.junit.Test;

/**
 * Single-player parser/factory contract (.scratch/gui-contradict-syntax): the canonical
 * Contradiction verb is {@code contradict <evidence> with <suspect>} (app-wide standard); the
 * legacy {@code present … to …} / {@code contradict … to …} stay accepted for back-compat onto the
 * same {@link ContradictCommand}, and the multiplayer phrasing {@code initiate final exam} is
 * accepted for {@code final exam}. Parser-level only — no wire Command class changes.
 */
public class CommandFactorySinglePlayerTest {

  private static Command create(String input) {
    return CommandFactorySinglePlayer.createCommand(
        CommandParserSinglePlayer.parseInputSimple(input));
  }

  @Test
  public void contradictWithIsTheCanonicalContradictionVerb() {
    Command command = create("contradict timecard with butler");
    assertTrue(command instanceof ContradictCommand);
    ContradictCommand contradict = (ContradictCommand) command;
    assertEquals("timecard", contradict.getEvidenceId());
    assertEquals("butler", contradict.getSuspectName());
  }

  @Test
  public void legacyContradictToStillParses() {
    Command command = create("contradict timecard to butler");
    assertTrue(command instanceof ContradictCommand);
    ContradictCommand contradict = (ContradictCommand) command;
    assertEquals("timecard", contradict.getEvidenceId());
    assertEquals("butler", contradict.getSuspectName());
  }

  @Test
  public void legacyPresentToStillParses() {
    Command command = create("present timecard to butler");
    assertTrue(command instanceof ContradictCommand);
    ContradictCommand contradict = (ContradictCommand) command;
    assertEquals("timecard", contradict.getEvidenceId());
    assertEquals("butler", contradict.getSuspectName());
  }

  @Test
  public void withSplitsBeforeToSoEvidenceMayContainTo() {
    // Canonical "with" wins precedence: an evidence id containing "to" is not mis-split.
    Command command = create("contradict note to self with butler");
    assertTrue(command instanceof ContradictCommand);
    ContradictCommand contradict = (ContradictCommand) command;
    assertEquals("note to self", contradict.getEvidenceId());
    assertEquals("butler", contradict.getSuspectName());
  }

  @Test
  public void contradictWithoutSeparatorIsRejected() {
    assertNull(create("contradict timecard butler"));
  }

  @Test
  public void finalExamAndItsMultiplayerPhrasingBothParse() {
    assertTrue(create("final exam") instanceof InitiateFinalExamCommand);
    assertTrue(create("initiate final exam") instanceof InitiateFinalExamCommand);
  }

  @Test
  public void bareJournalAddShowsUsageInsteadOfSearchingForAdd() {
    String[] parsed = CommandParserSinglePlayer.parseInputSimple("journal add");
    assertEquals("journal add", parsed[0]);
    assertNull(CommandFactorySinglePlayer.createCommand(parsed));
  }
}

package client.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import client.ClientState;
import common.commands.Command;
import common.commands.ContradictCommand;
import common.commands.InitiateFinalExamCommand;
import common.commands.RequestInitiateExamCommand;
import org.junit.Test;

/**
 * Multiplayer parser/factory contract (.scratch/gui-contradict-syntax): same vocabulary as the
 * single-player parser — canonical {@code contradict <evidence> with <suspect>} (app-wide
 * standard), with the legacy {@code present … to …} / {@code contradict … to …} accepted for
 * back-compat onto the same {@link ContradictCommand}.
 */
public class CommandFactoryClientTest {

  private static Command create(String input, boolean isHost) {
    return CommandFactoryClient.createCommand(
        CommandParserClient.parse(input), isHost, ClientState.IN_GAME);
  }

  @Test
  public void contradictWithIsTheCanonicalContradictionVerb() {
    Command command = create("contradict timecard with butler", false);
    assertTrue(command instanceof ContradictCommand);
    ContradictCommand contradict = (ContradictCommand) command;
    assertEquals("timecard", contradict.getEvidenceId());
    assertEquals("butler", contradict.getSuspectName());
  }

  @Test
  public void legacyContradictToStillParses() {
    Command command = create("contradict timecard to butler", false);
    assertTrue(command instanceof ContradictCommand);
    ContradictCommand contradict = (ContradictCommand) command;
    assertEquals("timecard", contradict.getEvidenceId());
    assertEquals("butler", contradict.getSuspectName());
  }

  @Test
  public void legacyPresentToStillParses() {
    Command command = create("present timecard to butler", false);
    assertTrue(command instanceof ContradictCommand);
    ContradictCommand contradict = (ContradictCommand) command;
    assertEquals("timecard", contradict.getEvidenceId());
    assertEquals("butler", contradict.getSuspectName());
  }

  @Test
  public void withSplitsBeforeToSoEvidenceMayContainTo() {
    // Canonical "with" wins precedence: an evidence id containing "to" is not mis-split.
    Command command = create("contradict note to self with butler", false);
    assertTrue(command instanceof ContradictCommand);
    ContradictCommand contradict = (ContradictCommand) command;
    assertEquals("note to self", contradict.getEvidenceId());
    assertEquals("butler", contradict.getSuspectName());
  }

  @Test
  public void contradictWithoutSeparatorIsRejected() {
    assertNull(create("contradict timecard butler", false));
  }

  @Test
  public void finalExamRoutesByRole() {
    assertTrue(create("final exam", true) instanceof InitiateFinalExamCommand);
    assertTrue(create("final exam", false) instanceof RequestInitiateExamCommand);
    assertTrue(create("initiate final exam", true) instanceof InitiateFinalExamCommand);
  }
}

package client.tutorial;

import static org.junit.Assert.*;

import Core.Suspect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import org.junit.Test;
import singleplayer.GameContextSinglePlayer;

/**
 * Feedback loop for the tutorial overhaul. Each test bootstraps a real SinglePlayerMain on the
 * bundled practice case via the production factory, then asserts that user input drives BOTH the
 * game engine AND the tutorial step machine — i.e. taught commands execute for real.
 */
public class TutorialIntegrationTest {

  private static TutorialManager manager(RecordingTutorialHost host, Path progressFile) {
    // Identity resolver: overlay "message" comes back as the raw textKey, which makes the i18n
    // wiring assertable without loading a bundle. Temp progress file keeps the test off $HOME.
    return new TutorialManager(
        host, Function.identity(), new TutorialLoader(), new TutorialProgressStore(progressFile));
  }

  @Test
  public void moveTutorialRunsAgainstThePracticeCaseAndPersistsCompletion() throws Exception {
    RecordingTutorialHost host = new RecordingTutorialHost();
    Path progressFile = Files.createTempDirectory("tut-progress").resolve("progress.json");
    TutorialManager tutorial = manager(host, progressFile);
    TutorialOrchestrator orchestrator =
        TutorialOrchestrator.bootstrap(tutorial, tutorial.startRoomFor("move_tutorial"));
    GameContextSinglePlayer ctx = orchestrator.getGame().getGameContext();

    tutorial.startTutorial("move_tutorial");

    assertTrue("tutorial should be active after startTutorial", tutorial.isActive());
    assertEquals(
        "first overlay should carry the i18n key (identity resolver)",
        "tutorial.move.s1",
        host.overlayMessages.get(0));
    assertEquals("Study", ctx.getCurrentRoomForPlayer(null).getName());
    assertTrue("case must be started for movement to execute", ctx.isCaseStarted());

    orchestrator.handleUserInput("move east");
    assertEquals(
        "engine must actually move the player east to the Parlour",
        "Parlour",
        ctx.getCurrentRoomForPlayer(null).getName());

    orchestrator.handleUserInput("move west");
    assertEquals("Study", ctx.getCurrentRoomForPlayer(null).getName());

    // Closing note is gated on a tutorial-only 'continue' (never sent to the engine).
    orchestrator.handleUserInput("continue");
    assertFalse("tutorial should have completed (END reached)", tutorial.isActive());
    assertTrue(
        "completion must persist so the player is not re-prompted",
        new TutorialProgressStore(progressFile).isCompleted("move_tutorial"));
  }

  @Test
  public void everyStepEmitsItsInstructionBeforeAwaitingThatStepsInput() throws Exception {
    // Guards the "first instruction never shows until you type something" bug: each step's
    // overlay must be painted on ENTRY to that step — including the very first on launch — so
    // the player always sees the guidance before being asked to act on it.
    RecordingTutorialHost host = new RecordingTutorialHost();
    Path progressFile = Files.createTempDirectory("tut-progress").resolve("progress.json");
    TutorialManager tutorial = manager(host, progressFile);
    TutorialOrchestrator orchestrator =
        TutorialOrchestrator.bootstrap(tutorial, tutorial.startRoomFor("move_tutorial"));

    // Launching the tutorial must emit exactly the first step's instruction, before any input.
    tutorial.startTutorial("move_tutorial");
    assertEquals(
        "starting a tutorial must emit the first instruction before any command is processed",
        List.of("tutorial.move.s1"),
        host.overlayMessages);

    // The expected command advances; the SECOND instruction must appear before the next input.
    orchestrator.handleUserInput("move east");
    assertEquals(
        "the next step's instruction must be emitted on entry, before its input is awaited",
        List.of("tutorial.move.s1", "tutorial.move.s2"),
        host.overlayMessages);

    // And the third, again before its input — no step may require the player to act blind.
    orchestrator.handleUserInput("move west");
    assertEquals(
        List.of("tutorial.move.s1", "tutorial.move.s2", "tutorial.move.s3"), host.overlayMessages);
  }

  @Test
  public void practiceCaseChainExecutesAgainstTheRealEngine() throws Exception {
    RecordingTutorialHost host = new RecordingTutorialHost();
    Path progressFile = Files.createTempDirectory("tut-progress").resolve("progress.json");
    TutorialManager tutorial = manager(host, progressFile);
    // Start in the Study (both clues reachable); the suspect is pinned to the Parlour.
    TutorialOrchestrator orch = TutorialOrchestrator.bootstrap(tutorial, "Study");
    GameContextSinglePlayer ctx = orch.getGame().getGameContext();

    // Drive raw commands (no tutorial running) straight through the engine.
    orch.handleUserInput("examine torn_letter");
    orch.handleUserInput("examine muddy_boot");
    orch.handleUserInput("combine torn_letter muddy_boot");
    orch.handleUserInput("move east");
    assertEquals("Parlour", ctx.getCurrentRoomForPlayer(null).getName());

    Suspect valet = ctx.getAllSuspects().get(0);
    assertEquals(
        "valet should start in his LIE state", Suspect.SuspectState.LIE, valet.getCurrentState());

    orch.handleUserInput("contradict muddy_boot with the valet");
    assertEquals(
        "presenting the muddy boot must flip the valet to TRUTH via the real engine",
        Suspect.SuspectState.TRUTH,
        valet.getCurrentState());
  }
}

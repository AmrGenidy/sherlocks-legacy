package client.tutorial;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import org.junit.Test;

/**
 * Regression for .scratch/gui-tutorial-bubble-polish: every tutorial guidance bubble is dismissible
 * (shows a close ×) EXCEPT the final completion ("type continue") bubble — the last SHOW_OVERLAY in
 * a script. {@link TutorialManager} passes that flag to {@code host.showTutorialOverlay}.
 */
public class TutorialBubbleDismissibleTest {

  private static TutorialManager manager(RecordingTutorialHost host, Path progressFile) {
    return new TutorialManager(
        host, Function.identity(), new TutorialLoader(), new TutorialProgressStore(progressFile));
  }

  @Test
  public void onlyTheFinalBubbleIsNotDismissible() throws Exception {
    RecordingTutorialHost host = new RecordingTutorialHost();
    Path progressFile = Files.createTempDirectory("tut-progress").resolve("progress.json");
    TutorialManager tutorial = manager(host, progressFile);
    TutorialOrchestrator orchestrator =
        TutorialOrchestrator.bootstrap(tutorial, tutorial.startRoomFor("move_tutorial"));

    tutorial.startTutorial("move_tutorial");
    // First instruction is dismissible (more steps follow).
    assertEquals(List.of(true), host.overlayDismissible);

    orchestrator.handleUserInput("move east");
    assertEquals(List.of(true, true), host.overlayDismissible);

    // The third overlay is the closing "type continue" bubble — the last SHOW_OVERLAY, so NOT
    // dismissible.
    orchestrator.handleUserInput("move west");
    assertEquals(List.of(true, true, false), host.overlayDismissible);

    // Sanity on the contract directly.
    assertTrue(host.overlayDismissible.get(0));
    assertFalse(host.overlayDismissible.get(host.overlayDismissible.size() - 1));
  }

  /**
   * The final ("type continue") bubble carries a Continue button in the GUI. Its action is exactly
   * {@code routeToTutorialIfActive("continue")} → {@code orchestrator.handleUserInput("continue")}
   * — the identical path a typed "continue" takes — so clicking it (like typing) ends the tutorial.
   */
  @Test
  public void continueCommandOnTheFinalBubbleEndsTheTutorial() throws Exception {
    RecordingTutorialHost host = new RecordingTutorialHost();
    Path progressFile = Files.createTempDirectory("tut-progress").resolve("progress.json");
    TutorialManager tutorial = manager(host, progressFile);
    TutorialOrchestrator orchestrator =
        TutorialOrchestrator.bootstrap(tutorial, tutorial.startRoomFor("move_tutorial"));

    tutorial.startTutorial("move_tutorial");
    orchestrator.handleUserInput("move east");
    orchestrator.handleUserInput("move west");

    // We're on the final, non-dismissible bubble — the one the GUI gives a Continue button.
    assertFalse(host.overlayDismissible.get(host.overlayDismissible.size() - 1));
    assertTrue("still on the final step before continuing", tutorial.isActive());

    // Exactly what the Continue button sends (and what typing "continue" sends).
    orchestrator.handleUserInput("continue");
    assertFalse("clicking Continue (or typing it) ends the tutorial", tutorial.isActive());
    assertTrue("completion is persisted", tutorial.isCompleted("move_tutorial"));
  }
}

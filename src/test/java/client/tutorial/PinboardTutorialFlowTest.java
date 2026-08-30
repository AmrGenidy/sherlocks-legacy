package client.tutorial;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import common.dto.JournalEntryDTO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import org.junit.Test;
import singleplayer.GameContextSinglePlayer;

/**
 * Regression for .scratch/gui-pinboard-tutorial: the Pinboard tutorial enters with pre-seeded
 * evidence and walks the player through open → link → contradict → combine on the board, each step
 * advancing on the action. Exercised against the real {@code tutorials.json} manifest + production
 * orchestrator/engine; the board's GUI actions are driven by their command/sentinel equivalents
 * (the actual window/drag rendering is left to visual sign-off).
 */
public class PinboardTutorialFlowTest {

  private static boolean hasEntry(List<JournalEntryDTO> entries, String id) {
    return entries.stream().anyMatch(e -> id.equals(e.getId()));
  }

  @Test
  public void seedsEvidenceAndWalksLinkContradictCombine() throws Exception {
    RecordingTutorialHost host = new RecordingTutorialHost();
    Path progressFile = Files.createTempDirectory("tut-progress").resolve("progress.json");
    TutorialManager tutorial =
        new TutorialManager(
            host,
            Function.identity(),
            new TutorialLoader(),
            new TutorialProgressStore(progressFile));
    TutorialOrchestrator orch =
        TutorialOrchestrator.bootstrap(
            tutorial,
            tutorial.startRoomFor("pinboard_tutorial"),
            tutorial.seedCommandsFor("pinboard_tutorial"));
    GameContextSinglePlayer ctx = orch.getGame().getGameContext();

    tutorial.startTutorial("pinboard_tutorial");

    // The silent seed examined both clues, walked to the Parlour, and questioned the valet.
    assertEquals("Parlour", ctx.getCurrentRoomForPlayer(null).getName());
    List<JournalEntryDTO> seeded = ctx.getJournalEntries(null);
    assertTrue("torn letter clue must be pre-synced", hasEntry(seeded, "clue:torn_letter"));
    assertTrue("muddy boot clue must be pre-synced", hasEntry(seeded, "clue:muddy_boot"));
    assertTrue(
        "the valet's statement must be pre-synced", hasEntry(seeded, "stmt:the_valet:default"));
    assertTrue(host.overlayMessages.contains("tutorial.pinboard.s1"));

    // Open the pinboard (the toolbar button routes the GUI-only "pinboard" token).
    orch.handleUserInput("pinboard");
    assertTrue(
        "opening the pinboard must advance to the link step",
        host.overlayMessages.contains("tutorial.pinboard.s2"));

    // Draw a link (the board notifies via the link sentinel).
    tutorial.processInput(ui.MainController.TUTORIAL_PINBOARD_LINKED);
    assertTrue(
        "drawing a link must advance to the contradict step",
        host.overlayMessages.contains("tutorial.pinboard.s3"));

    // Contradict from the board (dynamic command, matched by the "contradict *" wildcard) — and the
    // real action must fire (valet flips to TRUTH, confession deduction awarded).
    orch.handleUserInput("contradict muddy_boot with the valet");
    assertTrue(
        "the board contradict must advance to the combine step",
        host.overlayMessages.contains("tutorial.pinboard.s4"));
    assertTrue(
        "the contradict must really run",
        hasEntry(ctx.getJournalEntries(null), "ded:valet_confession"));

    // Combine from the board ("combine *" wildcard) — and the real deduction must be forged.
    orch.handleUserInput("combine torn_letter muddy_boot");
    assertTrue(
        "the board combine must advance to the closing step",
        host.overlayMessages.contains("tutorial.pinboard.s5"));
    assertTrue(
        "the combine must really run",
        hasEntry(ctx.getJournalEntries(null), "ded:valet_trail_insight"));

    // Closing.
    orch.handleUserInput("continue");
    assertFalse("the tutorial completes", tutorial.isActive());
  }
}

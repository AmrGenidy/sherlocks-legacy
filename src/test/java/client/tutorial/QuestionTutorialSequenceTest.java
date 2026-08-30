package client.tutorial;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import org.junit.Test;
import singleplayer.GameContextSinglePlayer;

/**
 * Regression for .scratch/gui-question-tutorial-sequence: the Question tutorial must start in the
 * Study (the real practice-case start) and walk the player east to the Parlour — the Valet's home
 * room — BEFORE asking them to question him. Exercised against the real {@code tutorials.json}
 * manifest + production orchestrator/engine.
 */
public class QuestionTutorialSequenceTest {

  private static TutorialManager manager(RecordingTutorialHost host, Path progressFile) {
    return new TutorialManager(
        host, Function.identity(), new TutorialLoader(), new TutorialProgressStore(progressFile));
  }

  @Test
  public void walksToTheParlourBeforeQuestioningTheValet() throws Exception {
    RecordingTutorialHost host = new RecordingTutorialHost();
    Path progressFile = Files.createTempDirectory("tut-progress").resolve("progress.json");
    TutorialManager tutorial = manager(host, progressFile);
    TutorialOrchestrator orchestrator =
        TutorialOrchestrator.bootstrap(tutorial, tutorial.startRoomFor("question_tutorial"));
    GameContextSinglePlayer ctx = orchestrator.getGame().getGameContext();

    tutorial.startTutorial("question_tutorial");

    // Starts in the Study, not co-located with the Valet.
    assertEquals("Study", ctx.getCurrentRoomForPlayer(null).getName());

    // Step 1: move east to the Parlour.
    orchestrator.handleUserInput("move east");
    assertEquals(
        "moving east must take the player to the Parlour",
        "Parlour",
        ctx.getCurrentRoomForPlayer(null).getName());
    assertTrue(
        "the move step must advance the tutorial",
        host.overlayMessages.contains("tutorial.question.s2"));

    // Step 2: question the Valet, now co-located in the Parlour.
    orchestrator.handleUserInput("question the valet");
    assertTrue(
        "questioning the Valet must advance the tutorial",
        host.overlayMessages.contains("tutorial.question.s3"));
  }
}

package client.tutorial;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import org.junit.Test;

/**
 * Regression for .scratch/gui-watson-tutorial-stuck: the Ask Watson tutorial's targeted step must
 * advance on the syntax the parser and autocomplete actually accept — {@code ask watson <target>}
 * ({@code ask watson torn_letter}), not {@code ask watson about <target>} (the "about" is never
 * stripped, so it can't match a real target and the autocomplete never offers it). Exercised
 * against the real {@code tutorials.json} manifest + production orchestrator/engine.
 */
public class WatsonTutorialStepTest {

  private static TutorialManager manager(RecordingTutorialHost host, Path progressFile) {
    return new TutorialManager(
        host, Function.identity(), new TutorialLoader(), new TutorialProgressStore(progressFile));
  }

  @Test
  public void targetedAskWatsonAdvancesTheStep() throws Exception {
    RecordingTutorialHost host = new RecordingTutorialHost();
    Path progressFile = Files.createTempDirectory("tut-progress").resolve("progress.json");
    TutorialManager tutorial = manager(host, progressFile);
    TutorialOrchestrator orchestrator =
        TutorialOrchestrator.bootstrap(tutorial, tutorial.startRoomFor("ask_watson_tutorial"));

    tutorial.startTutorial("ask_watson_tutorial");

    // Step 1: the free general hint.
    orchestrator.handleUserInput("ask watson");
    assertTrue(
        "the general ask-watson step should advance",
        host.overlayMessages.contains("tutorial.askWatson.s2"));

    // Step 2: the targeted ask in the accepted syntax (no "about") — this is what the player types
    // and what the autocomplete offers; it must advance the step.
    orchestrator.handleUserInput("ask watson torn_letter");
    assertTrue(
        "the targeted ask-watson step must advance on 'ask watson torn_letter'",
        host.overlayMessages.contains("tutorial.askWatson.s3"));
  }
}

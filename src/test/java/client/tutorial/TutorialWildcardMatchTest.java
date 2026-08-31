package client.tutorial;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import org.junit.Test;

/**
 * Unit cover for the opt-in verb-prefix wildcard in {@link TutorialManager} step matching
 * (.scratch/gui-pinboard-tutorial): an {@code expectedCommand} ending in {@code " *"} matches any
 * command starting with that verb phrase (so a board's dynamic {@code contradict <id> with <title>}
 * advances the step), while literal steps keep exact matching.
 */
public class TutorialWildcardMatchTest {

  private static TutorialManager managerFor(String json, RecordingTutorialHost host)
      throws Exception {
    Path progress = Files.createTempDirectory("tut").resolve("p.json");
    TutorialLoader loader = TutorialLoader.fromJson(json);
    return new TutorialManager(
        host, Function.identity(), loader, new TutorialProgressStore(progress));
  }

  @Test
  public void wildcardMatchesVerbPrefixButLiteralStaysExact() throws Exception {
    String json =
        "{\"tutorials\":{\"t\":{\"steps\":["
            + "{\"type\":\"SHOW_OVERLAY\",\"textKey\":\"k1\"},"
            + "{\"type\":\"AWAIT_COMMAND\",\"expectedCommand\":\"contradict *\"},"
            + "{\"type\":\"SHOW_OVERLAY\",\"textKey\":\"k2\"},"
            + "{\"type\":\"AWAIT_COMMAND\",\"expectedCommand\":\"move east\"},"
            + "{\"type\":\"END\"}]}}}";
    RecordingTutorialHost host = new RecordingTutorialHost();
    TutorialManager mgr = managerFor(json, host);
    mgr.startTutorial("t");

    // At the wildcard step "contradict *".
    assertFalse("bare verb (no args) must NOT match the wildcard", mgr.processInput("contradict"));
    assertFalse("a different verb must NOT match", mgr.processInput("contradictx foo"));
    assertTrue(
        "a dynamic board contradict command must match the wildcard",
        mgr.processInput("contradict clue:muddy_boot with The Valet"));
    assertTrue(host.overlayMessages.contains("k2"));

    // Now at the literal step "move east".
    assertFalse("literal step must not match a prefix", mgr.processInput("move"));
    assertTrue("literal step matches exactly", mgr.processInput("move east"));
  }
}

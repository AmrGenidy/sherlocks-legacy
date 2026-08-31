package client.tutorial;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

/** Local completion persistence: write, re-read, idempotency, and best-effort failure handling. */
public class TutorialProgressStoreTest {

  @Test
  public void marksReadsAndPersistsAcrossInstances() throws Exception {
    Path file = Files.createTempDirectory("tps").resolve("nested").resolve("progress.json");
    TutorialProgressStore store = new TutorialProgressStore(file);

    assertFalse(store.isCompleted("move_tutorial"));
    store.markCompleted("move_tutorial");
    assertTrue(store.isCompleted("move_tutorial"));

    // A fresh instance over the same file sees the recorded completion.
    assertTrue(new TutorialProgressStore(file).isCompleted("move_tutorial"));
  }

  @Test
  public void markIsIdempotent() throws Exception {
    Path file = Files.createTempDirectory("tps").resolve("progress.json");
    TutorialProgressStore store = new TutorialProgressStore(file);
    store.markCompleted("look_tutorial");
    store.markCompleted("look_tutorial");
    assertEquals(1, store.completed().size());
  }

  @Test
  public void missingFileReadsAsEmptyAndNeverThrows() {
    TutorialProgressStore store =
        new TutorialProgressStore(Paths.get("Z:/definitely/not/here/progress.json"));
    assertTrue(store.completed().isEmpty());
    assertFalse(store.isCompleted("anything"));
  }

  @Test
  public void nullAndBlankIdsAreIgnored() throws Exception {
    Path file = Files.createTempDirectory("tps").resolve("progress.json");
    TutorialProgressStore store = new TutorialProgressStore(file);
    store.markCompleted(null);
    store.markCompleted("  ");
    assertTrue(store.completed().isEmpty());
  }
}

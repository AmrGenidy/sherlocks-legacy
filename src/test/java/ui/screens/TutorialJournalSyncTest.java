package ui.screens;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import org.junit.BeforeClass;
import org.junit.Test;
import ui.MainController;
import ui.i18n.L10n;

/**
 * Regression for .scratch/gui-journal-pinboard-sync: during a tutorial the Journal window and the
 * Pinboard evidence panel pull from {@code shell.getSinglePlayerGame().getGameContext()}. The bug
 * was that {@code MainController.startTutorial} wired the tutorial game's output sink (so the
 * terminal worked) but never registered that game as the active single-player game — so every
 * pull-based reader saw {@code getSinglePlayerGame() == null} and stayed empty.
 *
 * <p>This drives the real FXML-loaded controller so {@code initialize()} wires the tutorial manager
 * and game screen, then asserts the active SP game (the windows' data source) is the live tutorial
 * game and carries the journal entry an {@code examine} produces.
 */
public class TutorialJournalSyncTest {

  private static MainController controller;

  @BeforeClass
  public static void initJfx() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    try {
      Platform.startup(latch::countDown);
    } catch (IllegalStateException alreadyStarted) {
      latch.countDown();
    }
    assertTrue("JavaFX did not start", latch.await(5, TimeUnit.SECONDS));

    onFx(
        () -> {
          FXMLLoader loader =
              new FXMLLoader(TutorialJournalSyncTest.class.getResource("/fxml/main.fxml"));
          BorderPane root = loader.load();
          controller = loader.getController();
          new Scene(root); // initialize() mounts the menu via runLater
        });
    flush();
  }

  @org.junit.AfterClass
  public static void resetLanguage() throws Exception {
    onFx(() -> L10n.setLanguage(L10n.ENGLISH));
    flush();
  }

  @Test
  public void tutorialRegistersItsGameSoJournalAndPinboardCanSync() throws Exception {
    onFx(() -> controller.startTutorial("examine_tutorial"));
    flush();

    onFx(
        () ->
            assertNotNull(
                "a running tutorial must register its game as the active single-player game so the"
                    + " Journal window and Pinboard can pull entries (they read"
                    + " getSinglePlayerGame().getGameContext())",
                controller.getSinglePlayerGame()));

    // examine in the tutorial routes through the orchestrator to the same active game; it must add
    // a
    // journal entry that the Journal/Pinboard would then read.
    onFx(() -> controller.sendCommand("examine torn_letter"));
    flush();

    onFx(
        () -> {
          var entries = controller.getSinglePlayerGame().getGameContext().getJournalEntries(null);
          assertNotNull(entries);
          assertFalse(
              "examine during a tutorial must produce a journal entry visible to the Journal window"
                  + " and Pinboard evidence panel",
              entries.isEmpty());
        });
  }

  private interface FxTask {
    void run() throws Exception;
  }

  private static void onFx(FxTask task) throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    Throwable[] error = new Throwable[1];
    Platform.runLater(
        () -> {
          try {
            task.run();
          } catch (Throwable t) {
            error[0] = t;
          } finally {
            done.countDown();
          }
        });
    assertTrue("FX task timed out", done.await(10, TimeUnit.SECONDS));
    if (error[0] != null) {
      if (error[0] instanceof AssertionError) {
        throw (AssertionError) error[0];
      }
      fail(error[0].toString());
    }
  }

  private static void flush() throws Exception {
    onFx(() -> {});
  }
}

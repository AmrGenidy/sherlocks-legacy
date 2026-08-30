package ui.screens;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.BorderPane;
import org.junit.BeforeClass;
import org.junit.Test;
import ui.menu.MenuPage;

/**
 * Headless regression for the startup flash (.scratch/gui-startup-menu-first): the main menu must
 * be the FIRST rendered frame — mounted synchronously during {@code MainController.initialize()}
 * (i.e. during {@code loader.load()}, before the stage is shown), NOT via a {@code
 * Platform.runLater} that fires after {@code show()} and lets the FXML in-game layout (toolbar +
 * room + terminal) flash for a frame. So immediately after {@code load()} — with NO runLater
 * drained — the menu is already the BorderPane center, the in-game terminal is detached, and the
 * toolbar is hidden.
 */
public class StartupMenuFirstTest {

  @BeforeClass
  public static void initJfx() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    try {
      Platform.startup(latch::countDown);
    } catch (IllegalStateException alreadyStarted) {
      latch.countDown();
    }
    assertTrue("JavaFX did not start", latch.await(5, TimeUnit.SECONDS));
  }

  @Test
  public void mainMenuIsMountedSynchronouslyDuringLoadBeforeAnyRunLater() throws Exception {
    onFx(
        () -> {
          FXMLLoader loader =
              new FXMLLoader(StartupMenuFirstTest.class.getResource("/fxml/main.fxml"));
          BorderPane root = loader.load(); // initialize() runs here, on the FX thread

          // Assert in the SAME FX pulse — no flush(), so no deferred runLater has fired yet. This
          // is
          // the "first frame" contract: only a synchronous mount in initialize() satisfies it.
          assertNotNull(
              "the main menu must already be mounted right after load (no deferred runLater)",
              root.lookup(".menu-page"));
          assertTrue(
              "the mounted first-frame content is the MenuPage",
              root.lookup(".menu-page") instanceof MenuPage);
          assertNull(
              "the in-game terminal must NOT be in the first frame (no room-view flash)",
              root.lookup(".terminal-area"));
          assertNotNull("the toolbar node still exists", root.getTop());
          assertFalse("the in-game toolbar must be hidden at launch", root.getTop().isManaged());
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
}

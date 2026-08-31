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
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import ui.i18n.L10n;
import ui.menu.MenuPage;

/**
 * Headless FX coverage for the full-window main menu (.scratch/main-menu): it mounts as the
 * BorderPane center with the toolbar hidden, and — the load-bearing guarantee from MENU_DESIGN — it
 * <b>never shows a scrollbar</b>, at the minimum and maximum window sizes, in a portrait window,
 * and in all three UI languages. No snapshot harness exists in this repo, so visual fidelity to
 * docs/art-refs/main_menu_reference.png is human sign-off; this test pins the structural contract.
 */
public class MainMenuLayoutTest {

  private static final double[][] SIZES = {
    {1024, 720}, // DESIGN.md §4 minimum
    {1920, 1080}, // a typical maximised landscape window
    {1024, 1200} // a tall/portrait window — exercises the centred-stack reflow
  };
  private static final String[] LANGUAGES = {L10n.ENGLISH, L10n.ARABIC, L10n.RUSSIAN};

  private static Parent root;

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
              new FXMLLoader(MainMenuLayoutTest.class.getResource("/fxml/main.fxml"));
          root = loader.load();
          Scene scene = new Scene(root);
          scene
              .getStylesheets()
              .add(
                  MainMenuLayoutTest.class
                      .getResource("/css/detective-theme.css")
                      .toExternalForm());
        });
    // Let the controller's initialize() finish mounting the main menu (it posts via runLater).
    flush();
  }

  @AfterClass
  public static void resetLanguage() throws Exception {
    onFx(() -> L10n.setLanguage(L10n.ENGLISH));
    flush();
  }

  @Test
  public void mainMenuIsFullWindowWithNoScrollbar_acrossLanguagesAndSizes() throws Exception {
    for (String language : LANGUAGES) {
      onFx(() -> L10n.setLanguage(language));
      flush(); // the language switch re-renders the menu via runLater

      for (double[] size : SIZES) {
        double w = size[0];
        double h = size[1];
        onFx(
            () -> {
              BorderPane shellRoot = (BorderPane) root;

              // Full-window mount (DEC-1): the MenuPage is in the scene, the toolbar is hidden, and
              // the in-game content pane (its terminal) is detached — so neither shows on the menu.
              assertNotNull(
                  "the MenuPage should be mounted (" + language + ")",
                  shellRoot.lookup(".menu-page"));
              assertTrue(
                  "the mounted page should be a MenuPage (" + language + ")",
                  shellRoot.lookup(".menu-page") instanceof MenuPage);
              assertNull(
                  "the terminal must not be present on the full-window menu (" + language + ")",
                  shellRoot.lookup(".terminal-area"));
              assertFalse(
                  "toolbar should be hidden on the full-window menu (" + language + ")",
                  shellRoot.getTop().isManaged());

              // Lay the scene out at this size, then assert nothing scrolls.
              shellRoot.resize(w, h);
              shellRoot.applyCss();
              shellRoot.layout();

              for (Node bar : shellRoot.lookupAll(".scroll-bar")) {
                assertFalse(
                    "a scrollbar must never be visible on the main menu — "
                        + language
                        + " at "
                        + (int) w
                        + "x"
                        + (int) h,
                    bar.isVisible());
              }
            });
      }
    }
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

  /** Drains pending FX runnables by running (and awaiting) one more — runLater is FIFO. */
  private static void flush() throws Exception {
    onFx(() -> {});
  }
}

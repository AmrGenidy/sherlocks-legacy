package ui.screens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Headless regression guard for the app-wide light/dark switch (DESIGN.md §8).
 *
 * <p>Dark mode was once a silent no-op: the base {@code detective-theme.css} was attached to the
 * root {@code BorderPane} via the FXML {@code stylesheets=} attribute — a <b>Parent</b> stylesheet,
 * which JavaFX ranks <i>above</i> the Scene's. The dark override is installed at the Scene level,
 * so the base {@code .root} colour values always won and nothing repainted. This test pins the fix:
 * the scene-level dark override must actually recolour the root surface from parchment to
 * night-ground, and back. If a Parent-level base stylesheet is ever reintroduced, the dark
 * assertion fails here.
 */
public class ThemeSwitchTest {

  // The two .root surface values from detective-theme.css / theme_dark.css (DESIGN.md §2).
  private static final Color LIGHT_PARCHMENT = Color.web("#EFE3C8");
  private static final Color DARK_NIGHT_GROUND = Color.web("#1A1611");

  private static Parent root;
  private static Scene scene;

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
          FXMLLoader loader = new FXMLLoader(ThemeSwitchTest.class.getResource("/fxml/main.fxml"));
          root = loader.load();
          scene = new Scene(root);
          // Mirror GameClientFX: the base theme is installed at the SCENE level, never on the root.
          scene.getStylesheets().add(ui.util.Theme.baseStylesheet());
        });
    flush();
  }

  @Test
  public void sceneLevelDarkOverrideRepaintsTheRootSurface() throws Exception {
    onFx(
        () -> {
          // Light first.
          ui.util.Theme.apply(scene, ui.settings.AppSettings.LIGHT);
          assertEquals(
              "the root surface should be light parchment under the base theme",
              LIGHT_PARCHMENT,
              rootSurface());

          // Toggle dark — the scene-level override must win and repaint the root to night-ground.
          ui.util.Theme.apply(scene, ui.settings.AppSettings.DARK);
          assertEquals(
              "dark mode must recolour the root surface to night-ground (regression: the dark"
                  + " override lost to a Parent-level base stylesheet)",
              DARK_NIGHT_GROUND,
              rootSurface());

          // Toggle back — removing the override returns to parchment.
          ui.util.Theme.apply(scene, ui.settings.AppSettings.LIGHT);
          assertEquals(
              "toggling back to light must restore parchment", LIGHT_PARCHMENT, rootSurface());
        });
  }

  /** The resolved first background fill of the scene root after CSS is applied. */
  private static Color rootSurface() {
    root.applyCss();
    Region region = (Region) root;
    return (Color) region.getBackground().getFills().get(0).getFill();
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

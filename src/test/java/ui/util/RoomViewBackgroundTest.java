package ui.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.junit.BeforeClass;
import org.junit.Test;
import ui.MainController;

/**
 * Guards that the room view shows exactly ONE room image with a themed letterbox and nothing
 * leaking behind it (.scratch/gui-roomview-single-image).
 *
 * <p>The single contained room image is letterboxed (preserveRatio); the bars must be an OPAQUE
 * themed mat painted by {@code RoomView} itself, so a landscape image in a narrow pane shows
 * faded-vellum parchment — never a second/previous image behind the plate. The tutorial guidance
 * card is an OPAQUE themed dossier card (vellum + ink contour), capped to its PREF size so it stays
 * a small bottom-centre card and never covers the room (.scratch/gui-tutorial-bubble-polish).
 * Resolved against the real {@code detective-theme.css} (+ {@code theme_dark.css}), mirroring
 * {@code ContentScaleStylingTest}.
 */
public class RoomViewBackgroundTest {

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
  public void roomViewCarriesTheRoomViewStyleClass() throws Exception {
    onFx(
        () -> {
          RoomView roomView = new RoomView(new MainController() {});
          assertTrue(
              "RoomView must carry .room-view so it paints an opaque themed letterbox mat",
              roomView.getStyleClass().contains("room-view"));
        });
  }

  @Test
  public void roomViewMatIsOpaqueAndThemed() throws Exception {
    onFx(
        () -> {
          Color light = resolvedBackground("room-view", false);
          Color dark = resolvedBackground("room-view", true);
          assertOpaque("the room letterbox mat must be opaque (no image leaks behind it)", light);
          assertOpaque("the room letterbox mat must be opaque in dark too", dark);
          assertThemed("the room mat must re-theme light↔dark (-sl-faded-vellum)", light, dark);
        });
  }

  @Test
  public void tutorialGuidanceCardIsAnOpaqueThemedDossierCard() throws Exception {
    onFx(
        () -> {
          // The tutorial card is now an on-brand dossier card: opaque vellum + ink contour, themed
          // (.scratch/gui-tutorial-bubble-polish). It is capped to PREF size so it never stretches
          // to
          // cover the room (.scratch/gui-tutorial-overlay-transparent). The label is transparent —
          // the card supplies the surface.
          Color light = resolvedBackground("tutorial-overlay-card", false);
          Color dark = resolvedBackground("tutorial-overlay-card", true);
          assertOpaque("the tutorial card must be an opaque dossier card", light);
          assertOpaque("the tutorial card must be opaque in dark too", dark);
          assertThemed("the tutorial card must re-theme light↔dark (-sl-vellum)", light, dark);
          assertTransparent(
              "the tutorial label is transparent — the card supplies the surface",
              resolvedBackground("tutorial-overlay-label", false));
        });
  }

  /** The resolved first background fill colour of a node carrying {@code styleClass}. */
  private static Color resolvedBackground(String styleClass, boolean dark) {
    Region node = new Region();
    node.getStyleClass().add(styleClass);
    VBox root = new VBox(node);
    Scene scene = new Scene(root);
    scene.getStylesheets().add(ui.util.Theme.baseStylesheet());
    if (dark) {
      scene
          .getStylesheets()
          .add(RoomViewBackgroundTest.class.getResource("/css/theme_dark.css").toExternalForm());
    }
    root.applyCss();
    root.layout();
    assertTrue("." + styleClass + " must declare a background fill", node.getBackground() != null);
    assertTrue(
        "." + styleClass + " must declare a background fill",
        !node.getBackground().getFills().isEmpty());
    return (Color) node.getBackground().getFills().get(0).getFill();
  }

  private static void assertOpaque(String message, Color c) {
    assertEquals(message + " (alpha)", 1.0, c.getOpacity(), 0.0001);
  }

  private static void assertTransparent(String message, Color c) {
    assertEquals(message + " (alpha)", 0.0, c.getOpacity(), 0.0001);
  }

  private static void assertThemed(String message, Color light, Color dark) {
    assertTrue(message + " (" + light + " vs " + dark + ")", !light.equals(dark));
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

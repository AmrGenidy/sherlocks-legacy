package ui.screens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import org.junit.BeforeClass;
import org.junit.Test;
import ui.MainController;
import ui.i18n.L10n;

/**
 * Regression for .scratch/gui-tutorial-bubble-position: the tutorial guidance card is pinned AWAY
 * from the pointed-at target so the referenced object/control is never covered. A room-object step
 * (CENTER, e.g. "examine torn_letter") puts the card at the TOP (the object sits below, arrow
 * down); the exits sidebar (RIGHT_PANEL) puts it on the LEFT; targets at/below the bottom edge keep
 * it bottom-centre.
 */
public class TutorialBubblePositionTest {

  private static MainController controller;

  @BeforeClass
  public static void initJfx() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    try {
      Platform.startup(latch::countDown);
    } catch (IllegalStateException already) {
      latch.countDown();
    }
    assertTrue("JavaFX did not start", latch.await(5, TimeUnit.SECONDS));

    onFx(
        () -> {
          FXMLLoader loader =
              new FXMLLoader(TutorialBubblePositionTest.class.getResource("/fxml/main.fxml"));
          BorderPane root = loader.load();
          controller = loader.getController();
          new Scene(root);
        });
    flush();
  }

  @org.junit.AfterClass
  public static void resetLanguage() throws Exception {
    onFx(() -> L10n.setLanguage(L10n.ENGLISH));
    flush();
  }

  @Test
  public void examineStepPlacesTheBubbleAboveTheObject() throws Exception {
    onFx(() -> controller.startTutorial("examine_tutorial"));
    flush();
    onFx(
        () ->
            assertEquals(
                "the examine step (CENTER target) must place the bubble ABOVE the object, not over it",
                Pos.TOP_CENTER,
                cardAlignment()));
  }

  @Test
  public void otherTargetsOffsetAwayToo() throws Exception {
    // move_tutorial s1 points at the TERMINAL (bottom edge / off-pane) — bottom-centre is clear.
    onFx(() -> controller.startTutorial("move_tutorial"));
    flush();
    onFx(
        () ->
            assertEquals(
                "a TERMINAL step keeps the bubble bottom-centre",
                Pos.BOTTOM_CENTER,
                cardAlignment()));

    // s2 points at the exits sidebar (RIGHT_PANEL) — the bubble moves beside it (left).
    onFx(() -> controller.sendCommand("move east"));
    flush();
    onFx(
        () ->
            assertEquals(
                "a RIGHT_PANEL step moves the bubble to the left, away from the sidebar",
                Pos.CENTER_LEFT,
                cardAlignment()));
  }

  // ---- reflection helpers ----

  private static Pos cardAlignment() {
    Node card = (Node) field(controller, "tutorialOverlayCard");
    return StackPane.getAlignment(card);
  }

  private static Object field(Object target, String name) {
    try {
      for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
        try {
          Field f = c.getDeclaredField(name);
          f.setAccessible(true);
          return f.get(target);
        } catch (NoSuchFieldException ignored) {
          // walk up
        }
      }
      throw new NoSuchFieldException(name);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("reflect field " + name, e);
    }
  }

  // ---- FX plumbing ----

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

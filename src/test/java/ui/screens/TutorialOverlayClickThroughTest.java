package ui.screens;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Regression for .scratch/gui-tutorial-overlay-clickthrough: the full-size tutorial overlay pane
 * must be click-through over its empty areas so the room sprites and the examine/question popup
 * beneath it stay clickable, while the guidance card stays interactive (its close ×/buttons must
 * work).
 *
 * <p>JavaFX picking consults {@link javafx.scene.Node#contains} after the mouse-transparent check.
 * The gotcha this guards: a {@code -fx-background-color: transparent} FILL is still pickable
 * ({@code contains==true}) with {@code pickOnBounds=false} — only a pane with NO background fill is
 * click-through. So the pane must resolve to no pickable surface, while the card (opaque vellum)
 * must stay pickable. Resolved against the real {@code detective-theme.css}, mirroring {@code
 * RoomViewBackgroundTest}.
 */
public class TutorialOverlayClickThroughTest {

  @BeforeClass
  public static void initJfx() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    try {
      Platform.startup(latch::countDown);
    } catch (IllegalStateException already) {
      latch.countDown();
    }
    assertTrue("JavaFX did not start", latch.await(5, TimeUnit.SECONDS));
  }

  @Test
  public void overlayPaneIsClickThroughButTheCardStaysInteractive() throws Exception {
    onFx(
        () -> {
          // The full-size pane: as production configures it (pickOnBounds=false). It must NOT be
          // pickable on its surface, so clicks fall through to the sprites/popup beneath.
          Region pane = styled("tutorial-overlay-pane", false);
          assertFalse(
              "the tutorial overlay pane must be click-through (no pickable background fill)",
              pane.contains(50, 50));

          // The guidance card (opaque vellum dossier) must stay pickable so its close ×/buttons
          // work,
          // even with pickOnBounds=false (i.e. its own surface intercepts).
          Region card = styled("tutorial-overlay-card", false);
          assertTrue(
              "the tutorial guidance card must stay interactive (its surface intercepts clicks)",
              card.contains(50, 50));
        });
  }

  /** A 100×100 Region carrying {@code styleClass}, resolved against the real base stylesheet. */
  private static Region styled(String styleClass, boolean pickOnBounds) {
    Region node = new Region();
    node.getStyleClass().add(styleClass);
    node.setPickOnBounds(pickOnBounds);
    Scene scene = new Scene(new VBox(node));
    scene.getStylesheets().add(ui.util.Theme.baseStylesheet());
    node.resize(100, 100);
    node.applyCss();
    return node;
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

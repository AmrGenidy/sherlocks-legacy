package ui.screens;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Headless regression for the result-popup body (.scratch/gui-popup-text-wrap): a long suspect
 * statement must show in FULL — the card stretches vertically to fit every wrapped line. It wraps
 * to the (width-bound) card, has NO max-height clamp and NO scroll pane, so nothing is ever clipped
 * with "…". Resolved against the real {@code detective-theme.css}, mirroring {@code
 * InputThemingTest}.
 */
public class DialogueBubbleWrapTest {

  private static final String SHORT_BODY = "A small brass key, recently oiled.";

  // Long enough that, at a narrow card width, it must wrap to many lines.
  private static final String LONG_STATEMENT =
      "I was here the entire evening, Inspector. I took my brandy by the fire, read the late edition"
          + " cover to cover, and I will not pretend otherwise — I fell asleep at my table around"
          + " eleven, and woke only when the clock struck two. I heard nothing, saw no one, and the"
          + " first I knew of any trouble was the constable's knock upon the door the following"
          + " morning. You may believe me or not, but that is the whole of it.";

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
  public void longBodyIsShownInFullWithNoClampAndNoScroll() throws Exception {
    onFx(
        () -> {
          VBox bubble = laidOutBubble(LONG_STATEMENT);

          // No scroll pane anywhere — the popup must stretch, not scroll.
          assertNull(
              "the popup must NOT contain a scroll pane — it stretches vertically instead",
              bubble.lookup(".scroll-pane"));

          Label content = directContent(bubble);
          assertNotNull("the body label must be a direct child of the card", content);
          assertTrue("the body must wrap, never single-line", content.isWrapText());

          // The body wrapped to many lines AND is shown in full: its laid-out height matches the
          // height its wrapped text needs at this width (no max-height clamp truncating it).
          double width = content.getWidth();
          double needed = content.prefHeight(width);
          assertTrue("the long statement must wrap to several lines", needed > 80);
          assertTrue(
              "the body label must be tall enough to show every line (got "
                  + content.getHeight()
                  + ", needs "
                  + needed
                  + ")",
              content.getHeight() >= needed - 1.0);
          // The card fully contains the body (it grew to fit, not clipped it).
          assertTrue("the card must be at least as tall as its body", bubble.getHeight() >= needed);
        });
  }

  @Test
  public void cardStretchesVerticallyWithMoreText() throws Exception {
    onFx(
        () -> {
          double shortHeight = laidOutBubble(SHORT_BODY).getHeight();
          double longHeight = laidOutBubble(LONG_STATEMENT).getHeight();
          assertTrue(
              "the card must grow taller for a longer statement (short "
                  + shortHeight
                  + " vs long "
                  + longHeight
                  + ")",
              longHeight > shortHeight + 40);
        });
  }

  /** Builds a bubble, binds max WIDTH only (as the controller does), lays it out, returns it. */
  private static VBox laidOutBubble(String body) {
    VBox bubble =
        GameScreenController.buildDialogueBubble(
            "suspect", "Detective Thorne", null, body, "Close", () -> {});
    bubble.setMaxWidth(320); // the in-game width bind; height stays content-driven (no max height)
    StackPane root = new StackPane(bubble);
    StackPane.setAlignment(bubble, javafx.geometry.Pos.CENTER);
    Scene scene = new Scene(root, 420, 700);
    scene.getStylesheets().add(ui.util.Theme.baseStylesheet());
    root.applyCss();
    root.layout();
    return bubble;
  }

  private static Label directContent(VBox bubble) {
    return (Label)
        bubble.getChildren().stream()
            .filter(n -> n.getStyleClass().contains("dialogue-bubble-content"))
            .findFirst()
            .orElse(null);
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

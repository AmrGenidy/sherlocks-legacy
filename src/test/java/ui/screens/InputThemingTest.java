package ui.screens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Headless regression guard for input theming in dark mode (.scratch/gui-g1-theming-integrity).
 *
 * <p>The terminal styling lives under compound {@code .scroll-pane.terminal-area} selectors that
 * only match the real {@code TerminalView}; a {@code TextArea} tagged {@code terminal-area} matched
 * nothing and kept modena's white {@code -fx-control-inner-background}, and the Case Maker's
 * classless fields were white too. The fix sets {@code -fx-control-inner-background:
 * -sl-faded-vellum} at {@code .root}, so EVERY input's inner surface follows the theme. This test
 * pins that a plain (classless) and a {@code themed-input} {@code TextArea} both resolve a dark
 * inner surface under the dark override — and that the menu profile chip's name stays lamp-lit, not
 * buried.
 */
public class InputThemingTest {

  // Dark-theme token (theme_dark.css): lamp-lit ink for the profile-chip name.
  private static final Color DARK_INK = Color.web("#E8D4A8");

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
  public void plainAndThemedTextAreasGetADarkInnerSurface() throws Exception {
    onFx(
        () -> {
          TextArea classless = new TextArea("Case Maker field");
          TextArea themed = new TextArea("Case File prose");
          themed.getStyleClass().add("themed-input");

          VBox rootBox = new VBox(classless, themed);
          buildDarkScene(rootBox);
          rootBox.applyCss();
          rootBox.layout();

          // modena paints .content as a gradient derived from -fx-control-inner-background; the
          // invariant we care about is that the inner surface is the DARK warm well, never white.
          assertDarkWell(
              "a classless TextArea's .content must follow the theme (regression: Case Maker fields"
                  + " were white in dark)",
              contentBackground(classless));
          assertDarkWell(
              "a themed-input TextArea's .content must be the dark well (regression: Case File prose"
                  + " was white)",
              contentBackground(themed));
        });
  }

  @Test
  public void profileChipNameStaysLegibleInDark() throws Exception {
    onFx(
        () -> {
          Button chip = new Button("Sherlock");
          chip.getStyleClass().add("menu-profile-chip");
          VBox rootBox = new VBox(chip);
          buildDarkScene(rootBox);
          rootBox.applyCss();

          assertSimilar(
              "the profile chip name must be lamp-lit ink, not the .button vellum (which is dark on"
                  + " dark) in candlelight",
              DARK_INK,
              (Color) chip.getTextFill());
        });
  }

  /** Installs the base theme + the dark override on a fresh scene wrapping {@code root}. */
  private static void buildDarkScene(Region root) {
    Scene scene = new Scene(root);
    scene.getStylesheets().add(ui.util.Theme.baseStylesheet());
    scene.getStylesheets().add(ui.util.Theme.darkStylesheet());
  }

  /** A representative fill colour of a TextArea's {@code .content} region after CSS. */
  private static Color contentBackground(TextArea area) {
    Region content = (Region) area.lookup(".content");
    assertNotNull("the TextArea skin must expose a .content region", content);
    return representativeColor(content.getBackground().getFills().get(0).getFill());
  }

  /** A Paint reduced to one Color: itself if a Color, else the last stop of a gradient. */
  private static Color representativeColor(javafx.scene.paint.Paint paint) {
    if (paint instanceof Color color) {
      return color;
    }
    if (paint instanceof javafx.scene.paint.LinearGradient gradient) {
      java.util.List<javafx.scene.paint.Stop> stops = gradient.getStops();
      return stops.get(stops.size() - 1).getColor();
    }
    throw new AssertionError("unexpected paint type: " + paint);
  }

  /** Asserts the surface is a dark warm well — low brightness, never the modena white. */
  private static void assertDarkWell(String message, Color actual) {
    double brightness = (actual.getRed() + actual.getGreen() + actual.getBlue()) / 3.0;
    assertTrue(
        message
            + " — expected a dark inner surface but got "
            + actual
            + " (brightness "
            + brightness
            + ")",
        brightness < 0.35);
  }

  private static void assertSimilar(String message, Color expected, Color actual) {
    assertEquals(message + " (red)", expected.getRed(), actual.getRed(), 0.02);
    assertEquals(message + " (green)", expected.getGreen(), actual.getGreen(), 0.02);
    assertEquals(message + " (blue)", expected.getBlue(), actual.getBlue(), 0.02);
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

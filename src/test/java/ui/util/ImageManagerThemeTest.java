package ui.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Two related guarantees around missing-art fallback.
 *
 * <ol>
 *   <li><b>Presets intercept and are cached.</b> Since docs/PRESET_ART_WIRING.md, a missing room
 *       image resolves to a deterministic engraving {@link PresetArtResolver} preset — a real
 *       theme-independent asset — <em>ahead</em> of the procedural placeholder. Presets are cached
 *       (they don't re-theme), so the same missing path returns the same instance.
 *   <li><b>The procedural placeholder still follows the theme.</b> When even a preset cannot
 *       resolve, {@link PlaceholderImageGenerator} is the last resort and must paint from the
 *       theme-aware {@link Palette} (DESIGN.md §8) — a LIGHT faded-vellum plate in light, a DARK
 *       one in dark (the .scratch/gui-g1-theming-integrity "light patch" guard, kept at the
 *       generator level now that ImageManager serves presets for these kinds).
 * </ol>
 */
public class ImageManagerThemeTest {

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

  @AfterClass
  public static void resetPalette() {
    // Leave the shared Palette in its default (light) state for other tests.
    Palette.applyTheme("light");
  }

  @Test
  public void proceduralRoomPlaceholderFollowsTheTheme() throws Exception {
    onFx(
        () -> {
          // The last-resort placeholder is generated directly (ImageManager now serves presets for
          // rooms, so the generator is the durable home of the theme-awareness guarantee).
          Palette.applyTheme("light");
          Color lightGround =
              dominantGround(PlaceholderImageGenerator.createRoomPlaceholder("Room", 800, 600));
          assertSimilar(
              "light placeholder ground is light faded-vellum", Palette.FADED_VELLUM, lightGround);

          Palette.applyTheme("dark");
          Color darkGround =
              dominantGround(PlaceholderImageGenerator.createRoomPlaceholder("Room", 800, 600));
          assertSimilar(
              "dark placeholder ground is dark night-vellum", Palette.FADED_VELLUM, darkGround);

          assertTrue(
              "the dark placeholder must differ from the light one (theme-aware, never stale)",
              !similar(lightGround, darkGround));
        });
  }

  @Test
  public void missingRoomArtResolvesToCachedEngravingPreset() throws Exception {
    onFx(
        () -> {
          ImageManager images = new ImageManager();
          Image first = images.getRoomImage("does/not/exist.png", "Conservatory");
          Image second = images.getRoomImage("does/not/exist.png", "Conservatory");
          // A missing room now resolves to a real engraving preset (1280x720), not the 800px
          // procedural placeholder — and presets ARE cached (theme-independent real art).
          assertEquals("missing room → engraving room preset", 1280.0, first.getWidth(), 0.0);
          assertSame("resolved presets are cached (same instance)", first, second);
        });
  }

  /**
   * The most common colour across the placeholder interior — the engraved ground. The hatch lines
   * are sparse by design, so the bare ground dominates and this reads the theme's plate colour
   * without depending on where a hatch line happens to fall.
   */
  private static Color dominantGround(Image image) {
    var reader = image.getPixelReader();
    int w = (int) image.getWidth();
    int h = (int) image.getHeight();
    int margin = Math.min(w, h) / 8; // well clear of the ink frame and mat
    java.util.Map<Color, Integer> counts = new java.util.HashMap<>();
    for (int y = margin; y < h - margin; y += 3) {
      for (int x = margin; x < w - margin; x += 3) {
        counts.merge(reader.getColor(x, y), 1, Integer::sum);
      }
    }
    return counts.entrySet().stream()
        .max(java.util.Map.Entry.comparingByValue())
        .orElseThrow()
        .getKey();
  }

  private static void assertSimilar(String message, Color expected, Color actual) {
    assertEquals(message + " (red)", expected.getRed(), actual.getRed(), 0.02);
    assertEquals(message + " (green)", expected.getGreen(), actual.getGreen(), 0.02);
    assertEquals(message + " (blue)", expected.getBlue(), actual.getBlue(), 0.02);
  }

  private static boolean similar(Color a, Color b) {
    return Math.abs(a.getRed() - b.getRed()) < 0.02
        && Math.abs(a.getGreen() - b.getGreen()) < 0.02
        && Math.abs(a.getBlue() - b.getBlue()) < 0.02;
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

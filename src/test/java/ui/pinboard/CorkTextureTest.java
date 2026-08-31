package ui.pinboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.junit.Test;
import ui.util.Palette;

/**
 * Invariants for the procedural cork tile (GUI G5a). Pure raster checks on a {@link WritableImage} —
 * no JavaFX toolkit/display needed — so they lock down the things that silently regress
 * (determinism, subtlety, theme-derivation, seamless edges) while the eye signs off the look.
 */
public class CorkTextureTest {

  private static final int SIZE = 128;

  /** Same base + seed must paint the identical tile, every time (no per-frame shimmer). */
  @Test
  public void sameSeedIsDeterministic() {
    WritableImage a = CorkTexture.makeTile(Palette.CORK, 1890L, SIZE);
    WritableImage b = CorkTexture.makeTile(Palette.CORK, 1890L, SIZE);
    assertEquals("Identical inputs must yield identical pixels", 0, pixelDiff(a, b));
  }

  /** A different seed must give a different tile (the flecks are actually seeded). */
  @Test
  public void differentSeedDiffers() {
    WritableImage a = CorkTexture.makeTile(Palette.CORK, 1L, SIZE);
    WritableImage b = CorkTexture.makeTile(Palette.CORK, 2L, SIZE);
    assertTrue("Different seeds should differ", pixelDiff(a, b) > 0);
  }

  /** Texture via repetition: flecks present, but a clear minority — never a flat fill, never noise. */
  @Test
  public void hasSubtleFlecksNotFlatNorNoise() {
    WritableImage tile = CorkTexture.makeTile(Palette.CORK, 7L, SIZE);
    int total = SIZE * SIZE;
    int flecked = countDifferentFrom(tile, Palette.CORK);
    assertTrue("Must carry flecks (not a flat fill)", flecked > total / 50); // > 2%
    assertTrue("Flecks must stay a quiet minority (not noise)", flecked < total / 2); // < 50%
  }

  /**
   * Legibility guardrail: every fleck stays low-contrast against the cork base — small luminance
   * deviation only, so the oxblood thread and vellum cards stay the focus (DESIGN.md §6).
   */
  @Test
  public void flecksStayLowContrast() {
    WritableImage tile = CorkTexture.makeTile(Palette.CORK, 7L, SIZE);
    PixelReader r = tile.getPixelReader();
    double baseLum = luminance(Palette.CORK);
    double maxDelta = 0;
    for (int y = 0; y < SIZE; y++) {
      for (int x = 0; x < SIZE; x++) {
        maxDelta = Math.max(maxDelta, Math.abs(luminance(r.getColor(x, y)) - baseLum));
      }
    }
    assertTrue("Flecks exist (some deviation)", maxDelta > 0.01);
    assertTrue("Flecks must be subtle, not harsh (delta " + maxDelta + ")", maxDelta < 0.16);
  }

  /**
   * Theme-derivation: the tile is built FROM the supplied base, so it tracks the palette. A daylight
   * tan base reads clearly lighter overall than a candlelight cork-brown base — no fixed speckle that
   * would become a light patch in dark mode (DESIGN.md §8).
   */
  @Test
  public void tileTracksItsBaseColour() {
    Palette.applyTheme("light");
    WritableImage light = CorkTexture.makeTile(Palette.CORK, 7L, SIZE);
    double lightMean = meanLuminance(light);
    double lightBase = luminance(Palette.CORK);

    Palette.applyTheme("dark");
    WritableImage dark = CorkTexture.makeTile(Palette.CORK, 7L, SIZE);
    double darkMean = meanLuminance(dark);
    double darkBase = luminance(Palette.CORK);
    Palette.applyTheme("light"); // restore

    assertTrue("Daylight cork must read lighter than candlelight cork", lightMean > darkMean + 0.2);
    assertTrue("Tile mean must sit near its base (flecks derive from it)",
        Math.abs(lightMean - lightBase) < 0.06);
    assertTrue("Tile mean must sit near its base (flecks derive from it)",
        Math.abs(darkMean - darkBase) < 0.06);
  }

  /**
   * Seamless when repeated: the tile is toroidal, so opposite edges are statistically continuous —
   * no border band or seam line appears where copies meet.
   */
  @Test
  public void oppositeEdgesAreContinuous() {
    WritableImage tile = CorkTexture.makeTile(Palette.CORK, 7L, SIZE);
    int band = 4;
    assertTrue(
        "Left/right edges must match (no vertical seam)",
        Math.abs(edgeMean(tile, true, band, true) - edgeMean(tile, true, band, false)) < 0.04);
    assertTrue(
        "Top/bottom edges must match (no horizontal seam)",
        Math.abs(edgeMean(tile, false, band, true) - edgeMean(tile, false, band, false)) < 0.04);
  }

  // --- helpers -------------------------------------------------------------

  private static int pixelDiff(WritableImage a, WritableImage b) {
    PixelReader ra = a.getPixelReader();
    PixelReader rb = b.getPixelReader();
    int w = (int) a.getWidth();
    int h = (int) a.getHeight();
    int diff = 0;
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        if (!ra.getColor(x, y).equals(rb.getColor(x, y))) diff++;
      }
    }
    return diff;
  }

  private static int countDifferentFrom(WritableImage img, Color base) {
    PixelReader r = img.getPixelReader();
    int w = (int) img.getWidth();
    int h = (int) img.getHeight();
    int n = 0;
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        if (!r.getColor(x, y).equals(base)) n++;
      }
    }
    return n;
  }

  private static double meanLuminance(WritableImage img) {
    PixelReader r = img.getPixelReader();
    int w = (int) img.getWidth();
    int h = (int) img.getHeight();
    double sum = 0;
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        sum += luminance(r.getColor(x, y));
      }
    }
    return sum / (w * h);
  }

  /** Mean luminance of a {@code band}-wide edge strip — vertical (left/right) or horizontal (top/bottom). */
  private static double edgeMean(WritableImage img, boolean vertical, int band, boolean leadingEdge) {
    PixelReader r = img.getPixelReader();
    int size = (int) img.getWidth();
    double sum = 0;
    int count = 0;
    for (int i = 0; i < band; i++) {
      int line = leadingEdge ? i : size - 1 - i;
      for (int j = 0; j < size; j++) {
        Color c = vertical ? r.getColor(line, j) : r.getColor(j, line);
        sum += luminance(c);
        count++;
      }
    }
    return sum / count;
  }

  private static double luminance(Color c) {
    return 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
  }
}

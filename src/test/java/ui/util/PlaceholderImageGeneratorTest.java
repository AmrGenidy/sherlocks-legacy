package ui.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.paint.Color;
import org.junit.After;
import org.junit.Test;

/**
 * Invariants for the Victorian-engraving placeholder art (GUI G3). These are pure raster checks on
 * {@link javafx.scene.image.WritableImage} buffers — no JavaFX toolkit/display needed — so they
 * lock down the engraving rules (line + cross-hatch tone, theme-aware ground, suspect-reads-larger)
 * that the eye then signs off visually.
 */
public class PlaceholderImageGeneratorTest {

  @After
  public void resetTheme() {
    Palette.applyTheme("light");
  }

  @Test
  public void placeholdersAreCorrectlySizedAndNonNull() {
    Image room = PlaceholderImageGenerator.createRoomPlaceholder("Study", 800, 600);
    Image suspect = PlaceholderImageGenerator.createSuspectPlaceholder("Suspect", 256);
    Image object = PlaceholderImageGenerator.createObjectPlaceholder("Object", 256);
    assertNotNull(room);
    assertNotNull(suspect);
    assertNotNull(object);
    assertEquals(800.0, room.getWidth(), 0.0);
    assertEquals(600.0, room.getHeight(), 0.0);
    assertEquals(256.0, suspect.getWidth(), 0.0);
    assertEquals(256.0, object.getWidth(), 0.0);
  }

  /**
   * Dark mode must be inverted line art on a night ground, never a daylight patch. The book-plate
   * frame corner is always inked, and {@link Palette#INK} differs between themes (near-black ink on
   * paper vs lamp-lit ochre on night), so the same pixel must change with the theme.
   */
  @Test
  public void roomPlaceholderIsThemeAware() {
    Palette.applyTheme("light");
    Color lightCorner =
        cornerInk(PlaceholderImageGenerator.createRoomPlaceholder("Study", 800, 600));
    Palette.applyTheme("dark");
    Color darkCorner =
        cornerInk(PlaceholderImageGenerator.createRoomPlaceholder("Study", 800, 600));
    assertTrue(
        "Dark-mode room art must differ from light mode (no light patch in candlelight)",
        colorDistance(lightCorner, darkCorner) > 0.1);
  }

  /**
   * Engraving, not a flat box: a tone band inside the room plate must contain BOTH inked hatch
   * pixels and bare-ground pixels interleaved. A single flat fill (the old placeholder) has no ink
   * inside the frame and fails this.
   */
  @Test
  public void roomToneIsCrossHatchedNotFlatFill() {
    Palette.applyTheme("light");
    Image room = PlaceholderImageGenerator.createRoomPlaceholder("Study", 800, 600);
    PixelReader r = room.getPixelReader();
    int ink = 0;
    int ground = 0;
    // A horizontal band well inside the frame, in the lower (floor) half where tone is laid.
    int y0 = 360;
    int y1 = 480;
    for (int y = y0; y < y1; y++) {
      for (int x = 120; x < 680; x++) {
        Color c = r.getColor(x, y);
        if (isInkish(c)) ink++;
        else ground++;
      }
    }
    assertTrue("Engraved tone band must contain inked hatch lines", ink > 200);
    assertTrue("Engraved tone band must leave bare ground between hatch lines", ground > 200);
  }

  /**
   * Suspect plate reads clearly larger than an object plate: the drawn figure fills a larger
   * vertical fraction of its own raster than the small object motif fills its raster. This holds
   * even before the layout's base-size rule (RoomViewLayout) is applied.
   */
  @Test
  public void suspectFigureFillsMoreOfPlateThanObjectMotif() {
    Palette.applyTheme("light");
    double suspectFrac =
        drawnHeightFraction(
            PlaceholderImageGenerator.createSuspectPlaceholder("Suspect", 256), 256);
    double objectFrac =
        drawnHeightFraction(PlaceholderImageGenerator.createObjectPlaceholder("Object", 256), 256);
    assertTrue(
        "Suspect figure ("
            + suspectFrac
            + ") must read larger than object motif ("
            + objectFrac
            + ")",
        suspectFrac > objectFrac);
    assertTrue("Object motif must read as a small thing, not fill the plate", objectFrac < 0.62);
  }

  // --- helpers -------------------------------------------------------------

  /** The always-inked book-plate frame corner. */
  private static Color cornerInk(Image img) {
    return img.getPixelReader().getColor(0, 0);
  }

  /** Closer to {@link Palette#INK} than to the light ground (faded vellum). */
  private static boolean isInkish(Color c) {
    return colorDistance(c, Palette.INK) < colorDistance(c, Palette.FADED_VELLUM);
  }

  /** Vertical span of non-transparent (drawn) pixels, as a fraction of image height. */
  private static double drawnHeightFraction(Image img, int size) {
    PixelReader r = img.getPixelReader();
    int top = -1;
    int bottom = -1;
    for (int y = 0; y < size; y++) {
      boolean rowHasInk = false;
      for (int x = 0; x < size; x++) {
        if (r.getColor(x, y).getOpacity() > 0.05) {
          rowHasInk = true;
          break;
        }
      }
      if (rowHasInk) {
        if (top < 0) top = y;
        bottom = y;
      }
    }
    if (top < 0) return 0.0;
    return (bottom - top + 1) / (double) size;
  }

  private static double colorDistance(Color a, Color b) {
    double dr = a.getRed() - b.getRed();
    double dg = a.getGreen() - b.getGreen();
    double db = a.getBlue() - b.getBlue();
    return Math.sqrt(dr * dr + dg * dg + db * db);
  }
}

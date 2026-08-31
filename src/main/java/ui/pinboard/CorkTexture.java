package ui.pinboard;

import java.util.Random;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

/**
 * Procedural cork texture (GUI G5a) — a small seamless tile of fine flecks painted over a flat cork
 * ground, meant to be repeated across the pinboard board so the evidence wall reads as <i>cork</i>
 * rather than a flat panel.
 *
 * <p><b>Texture via repetition</b> (DESIGN.md §6), not a raster photo: the flecks are a quiet
 * stipple derived from the supplied cork base — a touch lighter and a touch darker — so the texture
 * stays subtle and never competes with the pinned vellum cards or the oxblood thread. The base
 * comes from {@link ui.util.Palette#CORK}, so the same painter yields warm tan in daylight and deep
 * cork-brown in candlelight (DESIGN.md §8); no shadow, no gradient.
 *
 * <p>Pure and deterministic: a fixed {@code seed} paints the identical tile every call (no
 * per-frame shimmer), and flecks wrap at the tile edges so copies meet seamlessly. Uses {@link
 * WritableImage} + {@link PixelWriter} so it runs headless (no JavaFX toolkit) and is
 * unit-testable.
 */
public final class CorkTexture {

  private CorkTexture() {}

  /** Default tile edge in px — small enough to repeat cheaply, large enough to hide the period. */
  public static final int DEFAULT_TILE = 128;

  /** Fraction of tile pixels seeded as fleck centres — a quiet stipple, not noise. */
  private static final double FLECK_DENSITY = 0.11;

  /** Of those flecks, the share that grow to a 2px speck (a little grain variety). */
  private static final double LARGE_FLECK_RATIO = 0.18;

  // Fleck contrast is an ABSOLUTE HSB-brightness step from the base, not a multiplier (GUI G5a-fix).
  // A multiplicative factor collapses to nothing on a near-black base, which made the candlelight
  // stipple invisible; a fixed step stays perceptible on both the light tan and the dark cork-brown
  // while remaining subtle (the board is backdrop, not foreground). The lighter speck loses a little
  // saturation for a warm, dusty cork highlight.
  private static final double LIGHT_STEP = 0.12;
  private static final double DARK_STEP = 0.08;
  private static final double LIGHTER_DESAT = 0.9;

  /** The lighter speck colour for a given cork base (extracted so tests assert the real fleck). */
  static Color lighterFleck(Color base) {
    return Color.hsb(
        base.getHue(),
        base.getSaturation() * LIGHTER_DESAT,
        clamp01(base.getBrightness() + LIGHT_STEP));
  }

  /** The darker speck colour for a given cork base. */
  static Color darkerFleck(Color base) {
    return Color.hsb(
        base.getHue(), base.getSaturation(), clamp01(base.getBrightness() - DARK_STEP));
  }

  private static double clamp01(double v) {
    return v < 0 ? 0 : (v > 1 ? 1 : v);
  }

  /** Paints a {@value #DEFAULT_TILE}px tile from {@code base} and {@code seed}. */
  public static WritableImage makeTile(Color base, long seed) {
    return makeTile(base, seed, DEFAULT_TILE);
  }

  /**
   * Paints a {@code size}×{@code size} seamless cork tile from {@code base}, seeded by {@code
   * seed}.
   *
   * @throws IllegalArgumentException if {@code size <= 0}
   */
  public static WritableImage makeTile(Color base, long seed, int size) {
    if (size <= 0) {
      throw new IllegalArgumentException("tile size must be > 0, was " + size);
    }
    WritableImage img = new WritableImage(size, size);
    PixelWriter px = img.getPixelWriter();

    // 1. Flat cork ground.
    for (int y = 0; y < size; y++) {
      for (int x = 0; x < size; x++) {
        px.setColor(x, y, base);
      }
    }

    // 2. Fine flecks — seeded and toroidal (wrap at the edges) so the tile repeats without a seam.
    Color lighter = lighterFleck(base);
    Color darker = darkerFleck(base);
    Random rng = new Random(seed);
    int fleckCount = (int) Math.round((double) size * size * FLECK_DENSITY);
    for (int i = 0; i < fleckCount; i++) {
      int cx = rng.nextInt(size);
      int cy = rng.nextInt(size);
      Color c = rng.nextBoolean() ? lighter : darker;
      px.setColor(cx, cy, c);
      if (rng.nextDouble() < LARGE_FLECK_RATIO) {
        // A 2px speck: extend right + down, wrapped so an edge fleck continues on the far side.
        px.setColor((cx + 1) % size, cy, c);
        px.setColor(cx, (cy + 1) % size, c);
      }
    }
    return img;
  }
}

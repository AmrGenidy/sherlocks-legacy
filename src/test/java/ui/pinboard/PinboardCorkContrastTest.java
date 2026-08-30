package ui.pinboard;

import static org.junit.Assert.assertTrue;

import javafx.scene.paint.Color;
import org.junit.After;
import org.junit.Test;
import ui.util.Palette;

/**
 * GUI G5a dark-mode regression: the cork board must read as a believable, visibly-textured evidence
 * wall in BOTH themes — not a flat dark void that blends into the surrounding mahogany panels.
 *
 * <p>"Barely visible" is made deterministic by two perceptual measures, with light mode (which
 * reads well) as the known-good reference:
 *
 * <ul>
 *   <li><b>Board-vs-panel distinctness</b> — the cork ground must sit clearly apart from the
 *       nearest surrounding panel ({@code -sl-vellum} sidebar / {@code -sl-faded-vellum} toolbar),
 *       so the ink frame reads as an edge, not a hairline on one flat field.
 *   <li><b>Fleck contrast</b> — the lighter stipple must be perceptibly lighter than the cork
 *       ground so the texture is visible (yet still subtle), in candlelight as in daylight.
 * </ul>
 */
public class PinboardCorkContrastTest {

  // The cork ground must be at least this far (normalised RGB distance) from the nearest panel.
  private static final double DISTINCT_FLOOR = 0.12;
  // The lighter fleck must lift luminance by at least this much above the ground to be perceptible…
  private static final double FLECK_FLOOR = 0.05;
  // …but stay subtle: the board is backdrop, not foreground.
  private static final double FLECK_CEILING = 0.18;

  @After
  public void restoreLight() {
    Palette.applyTheme("light");
  }

  @Test
  public void darkCorkIsDistinctFromSurroundingPanels() {
    Palette.applyTheme("dark");
    double nearest =
        Math.min(dist(Palette.CORK, Palette.VELLUM), dist(Palette.CORK, Palette.FADED_VELLUM));
    assertTrue(
        "Dark cork board blends into the mahogany panels (distance "
            + nearest
            + " < "
            + DISTINCT_FLOOR
            + ") — it reads as a flat void",
        nearest > DISTINCT_FLOOR);
  }

  @Test
  public void lightCorkIsDistinctFromSurroundingPanels() {
    Palette.applyTheme("light");
    double nearest =
        Math.min(dist(Palette.CORK, Palette.VELLUM), dist(Palette.CORK, Palette.FADED_VELLUM));
    assertTrue("Light cork must stay distinct from panels", nearest > DISTINCT_FLOOR);
  }

  @Test
  public void darkFlecksArePerceptibleYetSubtle() {
    Palette.applyTheme("dark");
    assertFleckBand("Dark");
  }

  @Test
  public void lightFlecksArePerceptibleYetSubtle() {
    Palette.applyTheme("light");
    assertFleckBand("Light");
  }

  /** The cork ground must never approach a light patch — cards (beige in dark) must dominate it. */
  @Test
  public void darkCorkStaysClearlyBelowTheNoteCards() {
    Palette.applyTheme("dark");
    // The lightest dark-mode note tint is the beige -sl-note-plain (#E7DCC2); cork must stay well
    // below it so the cards remain the foreground.
    double cards = luminance(Color.web("#E7DCC2"));
    double cork = luminance(Palette.CORK);
    assertTrue("Dark cork must stay clearly darker than the note cards", cards - cork > 0.4);
  }

  private void assertFleckBand(String theme) {
    double ground = luminance(Palette.CORK);
    double lighter = luminance(CorkTexture.lighterFleck(Palette.CORK));
    double delta = lighter - ground;
    assertTrue(
        theme + " fleck is invisible against the cork (delta " + delta + " < " + FLECK_FLOOR + ")",
        delta > FLECK_FLOOR);
    assertTrue(
        theme + " fleck is too harsh (delta " + delta + " > " + FLECK_CEILING + ")",
        delta < FLECK_CEILING);
  }

  private static double dist(Color a, Color b) {
    double dr = a.getRed() - b.getRed();
    double dg = a.getGreen() - b.getGreen();
    double db = a.getBlue() - b.getBlue();
    return Math.sqrt(dr * dr + dg * dg + db * db) / Math.sqrt(3.0);
  }

  private static double luminance(Color c) {
    return 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
  }
}

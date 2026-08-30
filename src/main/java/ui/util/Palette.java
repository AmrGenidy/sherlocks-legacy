package ui.util;

import javafx.scene.paint.Color;

/**
 * The DESIGN.md §2 palette as {@link Color} values — the canvas/shape twin of the {@code -sl-*}
 * looked-up colors in {@code detective-theme.css}.
 *
 * <p>JavaFX {@code Canvas}/{@code Shape} drawing cannot reference CSS looked-up colors, so engraved
 * scenes (the menu frame, the frontispiece, the wax seals) read their palette here instead of
 * hard-coding hex at each call site. This is the single documented place a §2 colour appears as a
 * literal in Java; keep it in lock-step with {@code .root} (light) and {@code theme_dark.css}
 * (dark) (DESIGN.md §8).
 *
 * <p><b>Theme-aware (DESIGN.md §8):</b> the fields hold the <i>active</i> theme's colours; {@link
 * #applyTheme(String)} swaps the whole set between "The Study by Daylight" and "…by Candlelight".
 * Canvas elements drawn before a switch keep their pixels, so callers redraw after switching (the
 * shell re-renders the visible screen on theme change; others rebuild on navigation).
 */
public final class Palette {

  // The active theme's colours (default: light). Not final — swapped by applyTheme in lock-step
  // with the CSS looked-up colors; every reader sees the current theme at draw time.
  public static Color PARCHMENT; // app surface — aged paper / night ground
  public static Color VELLUM; // raised panels — cards, journal, dialogs
  public static Color FADED_VELLUM; // sunken — insets, input wells, room frame
  public static Color INK; // primary text, contour lines
  public static Color SEPIA; // secondary text, captions, hints
  public static Color PETROL; // primary accent — buttons, links, active state
  public static Color PETROL_BRIGHT; // hover / focus on primary
  public static Color OCHRE; // highlights, insight tokens, badges, flourishes
  public static Color OXBLOOD; // contradictions, errors, accent dots, wax seals
  public static Color MOSS; // deductions confirmed, task complete
  public static Color CORK; // pinboard corkboard surface — warm cork tan / deep cork-brown

  static {
    applyTheme("light");
  }

  private Palette() {}

  /**
   * Swaps the palette to "light" or "dark" (any non-"dark" value is light), mirroring {@code
   * detective-theme.css .root} (light) and {@code theme_dark.css} (dark) exactly.
   */
  public static void applyTheme(String theme) {
    boolean dark = "dark".equals(theme);
    // Light — "The Study by Daylight"             // Dark — "The Study by Candlelight"
    PARCHMENT = Color.web(dark ? "#1A1611" : "#EFE3C8");
    VELLUM = Color.web(dark ? "#2A231A" : "#F6EEDB");
    FADED_VELLUM = Color.web(dark ? "#221C14" : "#E3D4B0");
    INK = Color.web(dark ? "#E8D4A8" : "#241E17");
    SEPIA = Color.web(dark ? "#A38F6E" : "#6B5A43");
    PETROL = Color.web(dark ? "#3E96AC" : "#1C5D6E");
    PETROL_BRIGHT = Color.web(dark ? "#5BB0C5" : "#2E8198");
    OCHRE = Color.web(dark ? "#D9A45C" : "#C8893A");
    OXBLOOD = Color.web(dark ? "#C25B49" : "#8E3B2E");
    MOSS = Color.web(dark ? "#7E8C53" : "#5A6B3B");
    // Pinboard cork — mirrors -sl-cork (detective-theme.css .root / theme_dark.css). Canvas/raster
    // code (CorkTexture) reads it here because it can't use the CSS looked-up colour (DESIGN.md
    // §8).
    CORK = Color.web(dark ? "#5A4730" : "#C2A06B");
  }
}

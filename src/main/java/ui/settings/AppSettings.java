package ui.settings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Immutable local app settings persisted across launches (MENU_DESIGN #6): the chosen UI {@code
 * language}, the {@code theme} ("light" / "dark"), and TWO independent text-size multipliers
 * (.scratch/gui-typography-readability) — {@code terminalTextScale} (the terminal transcript/input/
 * prompt) and {@code readingTextScale} (the popups, journal, case file, exam, room text, Watson
 * hints and pinboard notes). Audio keeps its own {@code AudioSettings}.
 *
 * <p>Pure value type, persisted by {@link AppSettingsStore}; no FX dependency. Honours roadmap Hard
 * Constraint 1 (an optional local profile file for offline single-player). Unknown JSON fields are
 * ignored so a file written by a newer/older build still reads back its known values; a pre-split
 * file carrying the old single {@code textScale} seeds BOTH new scales (graceful migration).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class AppSettings {

  public static final String LIGHT = "light";
  public static final String DARK = "dark";

  /** Text-size slider bounds + default (1.0 = the designed base sizes), shared by both scales. */
  public static final double MIN_TEXT_SCALE = 0.8;

  public static final double MAX_TEXT_SCALE = 1.6;
  public static final double DEFAULT_TEXT_SCALE = 1.0;

  /**
   * The discrete step between text-size notches — the sliders are notch-based (0.8 / 1.0 "Normal" /
   * 1.2 / 1.4 / 1.6), and a stored scale is snapped to the nearest step so persistence is never an
   * in-between value. 20% per notch so each step is a clearly visible size change (a 10% step moved
   * body text by barely a pixel).
   */
  public static final double TEXT_SCALE_STEP = 0.2;

  private final String language;
  private final String theme;
  private final double terminalTextScale;
  private final double readingTextScale;

  /** Primary constructor. */
  public AppSettings(
      String language, String theme, double terminalTextScale, double readingTextScale) {
    this(language, theme, terminalTextScale, readingTextScale, 0.0);
  }

  /**
   * Jackson constructor. The legacy single {@code textScale} (pre-split files) is accepted only to
   * seed both scales when the split fields are absent (a missing primitive reads back as 0.0); it
   * is never re-serialised.
   */
  @JsonCreator
  AppSettings(
      @JsonProperty("language") String language,
      @JsonProperty("theme") String theme,
      @JsonProperty("terminalTextScale") double terminalTextScale,
      @JsonProperty("readingTextScale") double readingTextScale,
      @JsonProperty("textScale") double legacyTextScale) {
    this.language = language;
    this.theme = normalizeTheme(theme);
    this.terminalTextScale =
        normalizeScale(terminalTextScale > 0.0 ? terminalTextScale : legacyTextScale);
    this.readingTextScale =
        normalizeScale(readingTextScale > 0.0 ? readingTextScale : legacyTextScale);
  }

  /** Defaults: no stored language (the app keeps its current default), light theme, scales 1.0. */
  public static AppSettings defaults() {
    return new AppSettings(null, LIGHT, DEFAULT_TEXT_SCALE, DEFAULT_TEXT_SCALE);
  }

  @JsonProperty("language")
  public String language() {
    return language;
  }

  @JsonProperty("theme")
  public String theme() {
    return theme;
  }

  @JsonProperty("terminalTextScale")
  public double terminalTextScale() {
    return terminalTextScale;
  }

  @JsonProperty("readingTextScale")
  public double readingTextScale() {
    return readingTextScale;
  }

  public boolean isDark() {
    return DARK.equals(theme);
  }

  public AppSettings withLanguage(String newLanguage) {
    return new AppSettings(newLanguage, theme, terminalTextScale, readingTextScale);
  }

  public AppSettings withTheme(String newTheme) {
    return new AppSettings(language, newTheme, terminalTextScale, readingTextScale);
  }

  public AppSettings withTerminalTextScale(double newScale) {
    return new AppSettings(language, theme, newScale, readingTextScale);
  }

  public AppSettings withReadingTextScale(double newScale) {
    return new AppSettings(language, theme, terminalTextScale, newScale);
  }

  private static String normalizeTheme(String theme) {
    return DARK.equals(theme) ? DARK : LIGHT;
  }

  /**
   * Clamp to the slider range and SNAP to the nearest discrete notch ({@link #TEXT_SCALE_STEP}); a
   * non-finite or 0 (missing-field) value falls back to the default. So a stored scale is always
   * one of the steps, never an in-between value.
   */
  private static double normalizeScale(double scale) {
    if (!Double.isFinite(scale) || scale <= 0.0) {
      return DEFAULT_TEXT_SCALE;
    }
    double clamped = Math.max(MIN_TEXT_SCALE, Math.min(MAX_TEXT_SCALE, scale));
    // Round to the 0.2 notch (×5 = whole notches) so the value is a clean step with no float drift.
    return Math.round(clamped * 5.0) / 5.0;
  }
}

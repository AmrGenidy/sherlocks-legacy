package ui.util;

import ui.settings.AppSettings;

/**
 * Maps a text-size multiplier (a Settings slider, {@code 0.8–1.6}) to a discrete style-class bucket
 * under a given prefix — e.g. {@code term-scale-120} or {@code read-scale-140} — mirroring the
 * {@code lang-<code>} scheme. The class is toggled on a Scene root by {@link ContentScaleStyling}.
 * The terminal bucket's {@code .term-scale-NNN .<surface-class>} rules resize ONLY the terminal
 * family (absolute px); the reading bucket re-bases the ROOT font size, which every em-sized rule
 * in the stylesheets — i.e. the whole interface except the terminal — inherits.
 *
 * <p>Pure — no FX. Snapping to twenty-percent buckets keeps the CSS finite and makes each notch a
 * clearly visible size change.
 */
public final class ContentScale {

  /** Root-class prefix for the TERMINAL family (transcript lines + input + prompt + chips). */
  public static final String TERMINAL_PREFIX = "term-scale-";

  /** Root-class prefix for the READING family (everything except the terminal family). */
  public static final String READING_PREFIX = "read-scale-";

  private static final int STEP_PERCENT = 20;

  private ContentScale() {}

  /** The clamped multiplier, snapped to the nearest ten-percent slider stop. */
  public static double snap(double scale) {
    return bucketPercent(scale) / 100.0;
  }

  /**
   * The style class for a multiplier under {@code prefix}, e.g. {@code 1.25 -> "read-scale-120"}.
   */
  public static String styleClass(String prefix, double scale) {
    return prefix + bucketPercent(scale);
  }

  /**
   * True for any class this helper assigns under {@code prefix}, so callers can strip prior ones.
   */
  public static boolean isScaleClass(String prefix, String styleClass) {
    return styleClass != null && styleClass.startsWith(prefix);
  }

  private static int bucketPercent(double scale) {
    double clamped =
        Math.max(AppSettings.MIN_TEXT_SCALE, Math.min(AppSettings.MAX_TEXT_SCALE, finite(scale)));
    long stepped = Math.round(clamped * 100.0 / STEP_PERCENT) * STEP_PERCENT;
    return (int) stepped;
  }

  private static double finite(double scale) {
    return Double.isFinite(scale) ? scale : AppSettings.DEFAULT_TEXT_SCALE;
  }
}

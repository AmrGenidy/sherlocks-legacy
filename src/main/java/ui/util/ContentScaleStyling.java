package ui.util;

import javafx.scene.Node;
import ui.settings.AppSettings;

/**
 * Per-root text-size style-class helper (.scratch/gui-typography-readability), the twin of {@link
 * ui.i18n.LocaleStyling} for the Settings "Terminal text size" / "Reading text size" sliders.
 * {@link ContentScale#styleClass(String, double)} maps a multiplier to a {@code <prefix>NNN}
 * bucket. The {@code read-scale-NNN} bucket re-bases the ROOT font size (12px at 100%); every
 * non-terminal size in the stylesheets is an em ratio of the inherited font, so the whole interface
 * — menus, buttons, titles, sub-windows, dialogs — follows the Reading slider. The terminal family
 * pins absolute px sizes and follows only the {@code term-scale-NNN} bucket.
 *
 * <p>The main Scene root carries BOTH a {@code term-scale-*} and a {@code read-scale-*} class
 * (their rule sets are disjoint). Every other window/dialog root gets the reading class at creation
 * via {@link ui.util.Theme#install} → {@link #applyReading}; the active multiplier is tracked here
 * statically (mirroring {@code Theme}'s active-theme tracking) so a freshly-built Scene is right
 * from its first frame. Changing a class on a live root re-runs CSS on its descendants, so the main
 * window resizes immediately; the shell disposes/rebuilds cached sub-windows on a scale change, so
 * creation-time application is sufficient for them.
 */
public final class ContentScaleStyling {

  // The active Reading multiplier, so a freshly-built window tags itself correctly. Volatile: set
  // on the FX thread at startup/slider move, read when any window is constructed.
  private static volatile double activeReadingScale = AppSettings.DEFAULT_TEXT_SCALE;

  private ContentScaleStyling() {}

  /** Records the active Reading multiplier so new window roots tag themselves with it. */
  public static void setActiveReadingScale(double scale) {
    activeReadingScale = scale;
  }

  /** The active Reading multiplier, so code that sizes chrome can grow it with the font. */
  public static double getActiveReadingScale() {
    return activeReadingScale;
  }

  /** Tags a freshly-built window/dialog root with the active {@code read-scale-NNN} bucket. */
  public static void applyReading(Node sceneRoot) {
    apply(sceneRoot, ContentScale.READING_PREFIX, activeReadingScale);
  }

  /** Tags the root with the active {@code <prefix>NNN} class (replacing any previous one). */
  public static void apply(Node sceneRoot, String prefix, double scale) {
    if (sceneRoot == null) {
      return;
    }
    sceneRoot.getStyleClass().removeIf(c -> ContentScale.isScaleClass(prefix, c));
    sceneRoot.getStyleClass().add(ContentScale.styleClass(prefix, scale));
  }
}

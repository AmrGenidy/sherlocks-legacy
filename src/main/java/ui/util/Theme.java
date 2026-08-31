package ui.util;

import javafx.collections.ObservableList;
import javafx.scene.Parent;
import javafx.scene.Scene;
import ui.settings.AppSettings;

/**
 * Light/dark theme switching (DESIGN.md §8, MENU_DESIGN #6). Dark mode ("Study by Candlelight") is
 * an <b>override layer</b>: {@code theme_dark.css} only redefines the {@code -sl-*} looked-up
 * colors, so applying it is simply adding that stylesheet on top of {@code detective-theme.css}.
 * Because every control references {@code -sl-*}, the whole UI recolours live.
 *
 * <p>Every sub-window, dialog and pop-out has its <b>own</b> {@code Scene}/{@code DialogPane},
 * which the main scene's stylesheets do not reach — so each must install the theme itself. {@link
 * #install} does that against the <i>active</i> theme (tracked here statically so a window built
 * while dark is dark from the first frame); {@link #apply} flips an already-installed scene when
 * the user toggles.
 */
public final class Theme {

  private static final String BASE_CSS = "/css/detective-theme.css";
  private static final String DARK_CSS = "/css/theme_dark.css";

  // The active theme, so a freshly-built Scene installs the matching sheets. Volatile: set on the
  // FX
  // thread at startup/toggle, read when any window is constructed.
  private static volatile boolean dark = false;

  private Theme() {}

  /** Records the active theme ("light"/"dark") so new Scenes install the matching stylesheets. */
  public static void setActive(String theme) {
    dark = AppSettings.DARK.equals(theme);
  }

  public static boolean isDark() {
    return dark;
  }

  public static String baseStylesheet() {
    return url(BASE_CSS);
  }

  /** The dark override stylesheet URL (or null if missing), for loading onto a scene. */
  public static String darkStylesheet() {
    return url(DARK_CSS);
  }

  /**
   * Installs the base theme + the dark override (when active) onto a freshly-built Scene, and tags
   * its root with the active {@code read-scale-NNN} bucket so the "Reading text size" slider
   * reaches every window from its first frame (.scratch/gui-typography-readability, Phase 3).
   */
  public static void install(Scene scene) {
    if (scene != null) {
      install(scene.getStylesheets());
      ContentScaleStyling.applyReading(scene.getRoot());
    }
  }

  /**
   * Installs the base theme + the dark override (when active) onto a node subtree / DialogPane, and
   * tags it with the active {@code read-scale-NNN} bucket (see {@link #install(Scene)}).
   */
  public static void install(Parent parent) {
    if (parent != null) {
      install(parent.getStylesheets());
      ContentScaleStyling.applyReading(parent);
    }
  }

  private static void install(ObservableList<String> sheets) {
    addOnce(sheets, baseStylesheet());
    if (dark) {
      addOnce(sheets, darkStylesheet());
    }
  }

  /**
   * Flips an already-installed scene to {@code theme} (adds/removes the dark override) and records
   * it as active. Used for the main window on a live toggle.
   */
  public static void apply(Scene scene, String theme) {
    setActive(theme);
    if (scene == null) {
      return;
    }
    String darkUrl = darkStylesheet();
    if (darkUrl == null) {
      return;
    }
    scene.getStylesheets().remove(darkUrl);
    if (dark) {
      scene.getStylesheets().add(darkUrl);
    }
  }

  private static void addOnce(ObservableList<String> sheets, String url) {
    if (url != null && !sheets.contains(url)) {
      sheets.add(url);
    }
  }

  private static String url(String path) {
    return Theme.class.getResource(path) != null
        ? Theme.class.getResource(path).toExternalForm()
        : null;
  }
}

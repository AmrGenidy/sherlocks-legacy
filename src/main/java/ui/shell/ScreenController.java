package ui.shell;

import javafx.scene.Node;

/**
 * Contract between the navigation shell ({@code ui.MainController}) and one screen of the app
 * (Menu, Lobby, Game, Exam — .scratch/split-maincontroller, ADR-0002).
 *
 * <p>A screen owns everything inside the content pane while it is showing: its view graph, its
 * sub-state, and its share of terminal input. The shell owns what is common to all screens: the
 * window chrome (toolbar, sidebar, terminal widgets), scene/state transitions, localization
 * application, and the Escape chain.
 */
public interface ScreenController {

  /** The root node the shell mounts into the content pane. Re-queried after onShow(). */
  Node getView();

  /** Called after the view is mounted. Render initial state and print any terminal menu here. */
  default void onShow() {}

  /** Called before the view is unmounted. */
  default void onHide() {}

  /**
   * One Escape press, after the shell has dismissed shell-level overlays (autocomplete strip,
   * sub-windows). Step back one level of this screen's sub-state if there is one.
   *
   * @return true if consumed; false lets the shell apply its default (no-op on root screens —
   *     Escape never silently exits the app).
   */
  default boolean onEscape() {
    return false;
  }

  /**
   * One line typed into the terminal while this screen is showing (tutorial routing happens
   * upstream in the shell).
   *
   * @return true if consumed; false falls through to the shell's legacy routing.
   */
  default boolean handleTerminalInput(String input) {
    return false;
  }

  /** UI language changed while this screen is showing — re-render localized content. */
  default void onLanguageChanged() {}

  /**
   * Light/dark theme changed while this screen is showing (DESIGN.md §8). CSS recolours
   * automatically; a screen with canvas-drawn chrome should re-render so its painted pixels pick up
   * the new {@code ui.util.Palette}. Default: no-op (CSS-only screens need nothing).
   */
  default void onThemeChanged() {}

  /**
   * What is completable in the terminal right now (.scratch/terminal-autocomplete): command
   * names/aliases with their live argument domains, or the screen's bare menu options. Called on
   * the FX thread on every keystroke — keep it cheap. Default: nothing completable.
   */
  default ui.terminal.CompletionContext completionContext() {
    return ui.terminal.CompletionContext.empty();
  }

  /** Whether the shell shows the in-game chrome (toolbar buttons, sidebar) for this screen. */
  default boolean showsGameChrome() {
    return false;
  }

  /**
   * Whether this screen wants the whole window (.scratch/main-menu DEC-1): the shell mounts {@link
   * #getView()} as the {@code BorderPane} center and hides the toolbar, so the terminal and sidebar
   * do not show. Used by the main menu to match the full-bleed reference. May be queried again
   * after {@code onShow()}/sub-state changes (the screen calls {@code
   * MainController.relayoutScreen()}). Default false: the screen mounts into the in-game content
   * pane alongside the terminal.
   */
  default boolean usesFullWindow() {
    return false;
  }
}

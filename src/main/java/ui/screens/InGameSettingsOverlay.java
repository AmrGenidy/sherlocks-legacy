package ui.screens;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import ui.MainController;
import ui.i18n.L10n;

/**
 * The TRIMMED in-game Settings dossier (.scratch/gui-ingame-settings), extracted so any screen that
 * owns a {@link StackPane} content root can raise it over its own view — the in-game screen AND the
 * Final Exam paper (which keeps the toolbar, so the player can reach Settings mid-exam without
 * abandoning the paper).
 *
 * <p>A {@code .settings-card} floats over a warm dimmed veil ({@code .pause-scrim}, never black —
 * DESIGN.md §1), consistent with the pause chrome (MENU_DESIGN #7). It reuses the SAME {@link
 * SettingsSections} (and therefore the same {@code AppSettings}) as the main-menu Settings, so
 * audio / the two text-size sliders / the theme toggle apply live and persist identically. It shows
 * ONLY audio, the two font sliders (with their live previews but WITHOUT the explanatory
 * paragraph), and the theme toggle — no language picker. The card sits in a transparent scroll
 * capped to the host area, so it reflows and scrolls vertically only if it cannot fit at the largest
 * reading scale.
 */
public final class InGameSettingsOverlay {

  private final MainController shell;
  private final StackPane host;
  private StackPane overlay;

  /**
   * @param shell the navigation shell (owns {@link SettingsSections} and sounds)
   * @param host the content root this overlay mounts into (the owning screen's container)
   */
  public InGameSettingsOverlay(MainController shell, StackPane host) {
    this.shell = shell;
    this.host = host;
  }

  public boolean isShowing() {
    return overlay != null && host.getChildren().contains(overlay);
  }

  /** Raises the dossier over the host (idempotent while already showing). */
  public void show() {
    if (isShowing()) {
      return;
    }

    Region scrim = new Region();
    scrim.getStyleClass().add("pause-scrim");
    scrim.setOnMouseClicked(event -> hide());

    // Fixed header: the title on the left, an always-visible engraved close ✕ pinned top-right so
    // the player never has to scroll to the bottom to dismiss the dossier.
    Label title = new Label(L10n.t("settings.title"));
    title.getStyleClass().add("pause-title");
    Region headerSpacer = new Region();
    HBox.setHgrow(headerSpacer, Priority.ALWAYS);
    Button closeIcon =
        ui.menu.CloseIconButton.create(
            "common.close",
            () -> {
              shell.playSound("click.wav");
              hide();
            });
    HBox header = new HBox(10, title, headerSpacer, closeIcon);
    header.setAlignment(Pos.CENTER_LEFT);

    SettingsSections sections = new SettingsSections(shell);

    // The scrolling body — only this scrolls under the fixed header, if it can't fit at the largest
    // reading scale. Transparent so the vellum card shows through; vertical only.
    VBox body =
        new VBox(
            16,
            sections.audioSection(),
            sections.terminalTextSizeSection(false),
            sections.readingTextSizeSection(false),
            sections.themeSection());
    body.setFillWidth(true);

    ScrollPane scroll = new ScrollPane(body);
    scroll.setFitToWidth(true);
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    scroll.getStyleClass().add("settings-scroll");
    scroll.setMinHeight(0);
    VBox.setVgrow(scroll, Priority.ALWAYS);

    VBox card = new VBox(12, header, scroll);
    card.getStyleClass().add("settings-card");
    card.setFillWidth(true);
    card.setMaxWidth(560);
    // Cap the whole card to the dimmed host area so it never spills past it; the header stays fixed
    // and only the body scrolls when the content can't fit.
    ui.util.ViewportSizing.bindMaxToViewport(card, host, 0.96, 480, 0.94, 320);

    overlay = new StackPane(scrim, card);
    StackPane.setAlignment(card, Pos.CENTER);
    overlay.setViewOrder(-100); // above the room view / exam paper + any dialogue bubble
    host.getChildren().add(overlay);
    ui.util.Motion.fadeIn(overlay, ui.util.Motion.SCREEN).play();
    Platform.runLater(closeIcon::requestFocus);
  }

  /** Dismisses the overlay (Close / Escape / clicking the veil). No-op when not showing. */
  public void hide() {
    if (overlay != null) {
      host.getChildren().remove(overlay);
      overlay = null;
    }
  }
}

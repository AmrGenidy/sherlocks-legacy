package ui.menu;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import ui.i18n.L10n;
import ui.util.Palette;

/**
 * A small engraved corner "close" (✕) icon button — a thin-line hand-drawn glyph (DESIGN.md §6, not
 * a flat material glyph), in the shared {@code .menu-icon-button} style. Used to pin a
 * always-visible close affordance in the top-right of an overlay (the in-game Settings dossier) so
 * the player never has to scroll to dismiss it. Mirrors {@code MenuController}'s gear/globe/power
 * corner icons.
 */
public final class CloseIconButton {

  private CloseIconButton() {}

  /** A close icon button: {@code .menu-icon-button} chrome, an engraved ✕, a localized tooltip. */
  public static Button create(String tooltipKey, Runnable onAction) {
    double size = 22;
    Canvas icon = new Canvas(size, size);
    draw(icon.getGraphicsContext2D(), size);
    Button button = new Button();
    button.getStyleClass().add("menu-icon-button");
    button.setGraphic(icon);
    button.setTooltip(new Tooltip(L10n.t(tooltipKey)));
    button.setOnAction(event -> onAction.run());
    return button;
  }

  /** Two crossed thin sepia strokes — the engraved ✕. */
  private static void draw(GraphicsContext g, double s) {
    g.clearRect(0, 0, s, s);
    g.setStroke(Palette.SEPIA);
    g.setLineWidth(1.8);
    double m = s * 0.30; // inset from the edges
    g.strokeLine(m, m, s - m, s - m);
    g.strokeLine(s - m, m, m, s - m);
  }
}

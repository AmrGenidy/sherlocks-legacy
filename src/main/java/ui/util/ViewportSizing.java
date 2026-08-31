package ui.util;

import javafx.beans.binding.Bindings;
import javafx.scene.layout.Region;

/**
 * Overlay sizing per DESIGN.md §4 (.scratch/responsive-resizing issue 02): overlays size relative
 * to the viewport that contains them — percent-bound max sizes with floors on the 8px scale — never
 * fixed pixels. At 1024×720 the overlay shrinks with the pane; maximized it grows in proportion
 * instead of rattling at a fixed 600×400.
 */
public final class ViewportSizing {

  private ViewportSizing() {}

  /**
   * Binds the node's max width/height to fractions of the viewport's live size, floored at {@code
   * minWidth}/{@code minHeight} (8px-scale values) so the overlay stays readable in a small pane.
   */
  public static void bindMaxToViewport(
      Region node,
      Region viewport,
      double widthFraction,
      double minWidth,
      double heightFraction,
      double minHeight) {
    node.maxWidthProperty()
        .bind(
            Bindings.createDoubleBinding(
                () -> Math.max(minWidth, viewport.getWidth() * widthFraction),
                viewport.widthProperty()));
    node.maxHeightProperty()
        .bind(
            Bindings.createDoubleBinding(
                () -> Math.max(minHeight, viewport.getHeight() * heightFraction),
                viewport.heightProperty()));
  }

  /** Width-only variant for content whose height should stay content-driven (e.g. tip cards). */
  public static void bindMaxWidthToViewport(
      Region node, Region viewport, double widthFraction, double minWidth) {
    node.maxWidthProperty()
        .bind(
            Bindings.createDoubleBinding(
                () -> Math.max(minWidth, viewport.getWidth() * widthFraction),
                viewport.widthProperty()));
  }
}

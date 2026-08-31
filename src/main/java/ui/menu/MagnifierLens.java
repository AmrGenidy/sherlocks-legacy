package ui.menu;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeLineCap;
import ui.util.Palette;

/**
 * A working magnifying-glass lens overlay for the menu (gui-frontispiece-plate): a circular "glass"
 * that shows a magnified and barrel-warped (fisheye) view of a snapshot of the menu under the
 * cursor, ringed by an engraved brass frame + handle (the same ligne-claire language as the desk
 * magnifier, DESIGN.md §6 — no shadows). The owner snapshots the static menu once into {@link
 * #setSnapshot} (re-snapshotting on resize / theme change) and calls {@link #showAt} on every mouse
 * move; only the ~150px lens region is re-sampled per move via {@link PixelReader}/{@link
 * PixelWriter}.
 *
 * <p>The barrel mapping bulges the centre: the source/output ratio runs from {@code 1/M} at the
 * centre (most magnified) to {@code 1} at the rim (no magnification), so the centre reads ~{@code M}×
 * and the magnification eases off toward the edge.
 */
final class MagnifierLens {

  private final int diameter;
  private final double radius;
  private final double magnification;
  private final Group node;
  private final WritableImage warped;

  private Image snapshot;
  private PixelReader reader;

  MagnifierLens(double diameter, double magnification) {
    this.diameter = (int) Math.round(diameter);
    this.radius = this.diameter / 2.0;
    this.magnification = magnification;

    warped = new WritableImage(this.diameter, this.diameter);
    ImageView glass = new ImageView(warped);
    glass.setSmooth(true);
    glass.setClip(new Circle(radius, radius, radius)); // crisp circular edge

    // A frame canvas large enough to hold the handle extending past the lower-right of the glass.
    Canvas frame = new Canvas(this.diameter + radius, this.diameter + radius);
    drawFrame(frame.getGraphicsContext2D());

    node = new Group(glass, frame); // glass below, brass ring/handle on top
    node.setMouseTransparent(true);
    node.setManaged(false); // positioned by layoutX/Y; never laid out by the host pane
  }

  /** The overlay node to add at the Scene root (positioned by {@link #showAt}). */
  Node node() {
    return node;
  }

  /** Sets the menu snapshot the lens magnifies (its pixel space == scene coordinates). */
  void setSnapshot(Image snap) {
    this.snapshot = snap;
    this.reader = snap == null ? null : snap.getPixelReader();
  }

  /** Re-sample the warped glass around scene point (x,y) and centre the glass there. */
  void showAt(double x, double y) {
    if (reader != null) {
      sample(x, y);
    }
    node.setLayoutX(x - radius);
    node.setLayoutY(y - radius);
  }

  /** Fills {@link #warped} with the barrel-distorted magnification of the snapshot around (cx,cy). */
  private void sample(double cx, double cy) {
    PixelWriter w = warped.getPixelWriter();
    int sw = (int) snapshot.getWidth();
    int sh = (int) snapshot.getHeight();
    double m = magnification;
    for (int oy = 0; oy < diameter; oy++) {
      double dyo = oy - radius;
      for (int ox = 0; ox < diameter; ox++) {
        double dxo = ox - radius;
        double ro = Math.sqrt(dxo * dxo + dyo * dyo);
        if (ro > radius) {
          w.setArgb(ox, oy, 0); // transparent outside the glass (the clip also enforces this)
          continue;
        }
        double u = ro / radius;
        double scale = (1.0 / m) * (1.0 + (m - 1.0) * u * u); // 1/m at centre → 1 at the rim
        int sx = clamp((int) Math.round(cx + dxo * scale), sw);
        int sy = clamp((int) Math.round(cy + dyo * scale), sh);
        w.setArgb(ox, oy, reader.getArgb(sx, sy));
      }
    }
  }

  private static int clamp(int v, int size) {
    if (v < 0) {
      return 0;
    }
    return v >= size ? size - 1 : v;
  }

  /** An engraved brass ring around the glass + a short handle (ligne-claire, no shadow). */
  private void drawFrame(GraphicsContext g) {
    double c = radius; // ring centre within the frame canvas
    double ring = Math.max(4, diameter * 0.06);

    // Handle: a thick brass bar from the lower-right of the ring outward, with an ink contour.
    double a = Math.PI / 4;
    double hx0 = c + Math.cos(a) * (radius + ring * 0.3);
    double hy0 = c + Math.sin(a) * (radius + ring * 0.3);
    double hx1 = c + Math.cos(a) * (radius + radius * 0.85);
    double hy1 = c + Math.sin(a) * (radius + radius * 0.85);
    g.setLineCap(StrokeLineCap.ROUND);
    g.setStroke(Palette.INK);
    g.setLineWidth(ring * 1.7);
    g.strokeLine(hx0, hy0, hx1, hy1);
    g.setStroke(Palette.OCHRE);
    g.setLineWidth(ring * 1.2);
    g.strokeLine(hx0, hy0, hx1, hy1);

    // Brass ring with thin ink contours inside + out (the flat-fill-between-clean-lines look).
    double d = (radius - ring / 2) * 2;
    double o = c - radius + ring / 2;
    g.setStroke(Palette.OCHRE);
    g.setLineWidth(ring);
    g.strokeOval(o, o, d, d);
    g.setStroke(Palette.INK);
    g.setLineWidth(Math.max(1, ring * 0.16));
    g.strokeOval(c - radius + 1, c - radius + 1, (radius - 1) * 2, (radius - 1) * 2);
    g.strokeOval(c - radius + ring, c - radius + ring, (radius - ring) * 2, (radius - ring) * 2);
  }
}

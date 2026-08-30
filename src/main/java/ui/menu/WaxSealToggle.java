package ui.menu;

import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import ui.util.Palette;

/**
 * A wax-seal toggle (MENU_DESIGN #6, DESIGN.md §5 "wax seal" badges): a clickable control showing
 * an oxblood wax seal — <b>stamped</b> (a filled, scalloped roundel with an impressed monogram)
 * when on, an empty engraved ring when off — beside a label. Drawn entirely in ligne claire (no
 * shadow); the seal and the border are the affordance.
 *
 * <p>Extends {@link Button} so it joins the keyboard focus ring and fires on Enter. State is held
 * here; toggling repaints the seal and notifies {@code onChange}. Presentation-only — the label is
 * passed in already localized.
 */
public class WaxSealToggle extends Button {

  private static final double SEAL = 28;

  private final Canvas seal = new Canvas(SEAL, SEAL);
  private final Consumer<Boolean> onChange;
  private boolean selected;

  public WaxSealToggle(String label, boolean initial, Consumer<Boolean> onChange) {
    this.selected = initial;
    this.onChange = onChange != null ? onChange : value -> {};

    getStyleClass().add("wax-toggle");
    Label text = new Label(label);
    text.getStyleClass().add("wax-toggle-label");
    HBox box = new HBox(12, seal, text);
    box.setAlignment(Pos.CENTER_LEFT);
    setGraphic(box);

    draw();
    setOnAction(event -> toggle());
  }

  public boolean isSelected() {
    return selected;
  }

  /** Sets the state without firing {@code onChange} (e.g. an external/programmatic change). */
  public void setSelected(boolean value) {
    if (selected != value) {
      selected = value;
      draw();
    }
  }

  private void toggle() {
    selected = !selected;
    draw();
    onChange.accept(selected);
  }

  private void draw() {
    GraphicsContext g = seal.getGraphicsContext2D();
    double s = SEAL;
    g.clearRect(0, 0, s, s);
    double cx = s / 2;
    double cy = s / 2;
    double r = s * 0.40;

    if (selected) {
      // A stamped wax seal: an oxblood scalloped roundel with an impressed ring + check monogram.
      g.setFill(Palette.OXBLOOD);
      int scallops = 12;
      g.beginPath();
      for (int i = 0; i <= scallops * 2; i++) {
        double a = Math.PI * i / scallops;
        double rr = (i % 2 == 0) ? r : r * 0.86;
        double x = cx + Math.cos(a) * rr;
        double y = cy + Math.sin(a) * rr;
        if (i == 0) {
          g.moveTo(x, y);
        } else {
          g.lineTo(x, y);
        }
      }
      g.closePath();
      g.fill();

      g.setStroke(Color.color(0, 0, 0, 0.28)); // impressed inner ring
      g.setLineWidth(1.2);
      g.strokeOval(cx - r * 0.66, cy - r * 0.66, r * 1.32, r * 1.32);

      g.setStroke(Color.color(1, 1, 1, 0.7)); // embossed check monogram
      g.setLineWidth(2.0);
      g.strokeLine(cx - r * 0.34, cy + r * 0.02, cx - r * 0.06, cy + r * 0.30);
      g.strokeLine(cx - r * 0.06, cy + r * 0.30, cx + r * 0.40, cy - r * 0.32);
    } else {
      // An empty seat for the seal: a faint engraved ring (sepia), no wax.
      g.setStroke(Palette.SEPIA);
      g.setLineWidth(1.6);
      g.strokeOval(cx - r, cy - r, r * 2, r * 2);
    }
  }
}

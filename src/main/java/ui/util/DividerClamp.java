package ui.util;

import javafx.geometry.Orientation;
import javafx.scene.control.SplitPane;

/**
 * Guards a single-divider {@link SplitPane} against the divider collapsing a pane below its minimum
 * size through resize sequences (the terminal-disappears-on-resize bug,
 * .scratch/responsive-resizing issue 01). SplitPane honors content min sizes during user drags, but
 * divider positions are proportional and are not reliably re-validated through programmatic or
 * window resizes (shrink-then-grow, maximize-then-restore). {@link #install} re-clamps the divider
 * into the valid pixel range whenever the pane size or the divider position changes.
 *
 * <p>The clamp maths ({@link #clamp}) is pure so it can be unit-tested without a display.
 */
public final class DividerClamp {

  private static final double EPSILON = 1e-9;

  private DividerClamp() {}

  /**
   * Clamps {@code divider} — the fraction (0–1) of {@code total} pixels allotted to the leading
   * pane — so the leading pane keeps at least {@code minLeading} pixels and the trailing pane at
   * least {@code minTrailing}.
   *
   * <p>When the two minimums cannot both fit in {@code total}, the trailing pane wins: the terminal
   * panel must never disappear, so it gets its full minimum and the leading pane takes what is
   * left. Degenerate sizes (zero, negative, non-finite) only normalize the divider into [0, 1].
   */
  public static double clamp(double divider, double total, double minLeading, double minTrailing) {
    double d = Double.isFinite(divider) ? Math.min(1.0, Math.max(0.0, divider)) : 0.5;
    if (!Double.isFinite(total) || total <= 0) {
      return d;
    }
    double lo = Math.min(1.0, Math.max(0.0, minLeading / total));
    double hi = Math.min(1.0, Math.max(0.0, 1.0 - minTrailing / total));
    if (lo > hi) {
      // Cannot honor both minimums: the trailing (terminal) pane wins.
      return hi;
    }
    return Math.min(hi, Math.max(lo, d));
  }

  /**
   * Installs the clamp on the split pane's first divider. Re-clamps on every width/height change
   * (window resizes, maximize/restore) and on every divider movement (SplitPane's own proportional
   * layout resets), so the panes' minimum sizes hold through any resize sequence. The
   * user-draggable divider is preserved — drags inside the valid range are untouched.
   */
  public static void install(SplitPane splitPane, double minLeading, double minTrailing) {
    if (splitPane.getDividers().isEmpty()) {
      return;
    }
    SplitPane.Divider divider = splitPane.getDividers().get(0);
    boolean[] reentrant = {false};
    Runnable reclamp =
        () -> {
          if (reentrant[0]) {
            return;
          }
          double total =
              splitPane.getOrientation() == Orientation.VERTICAL
                  ? splitPane.getHeight()
                  : splitPane.getWidth();
          double clamped = clamp(divider.getPosition(), total, minLeading, minTrailing);
          if (Math.abs(clamped - divider.getPosition()) > EPSILON) {
            reentrant[0] = true;
            try {
              divider.setPosition(clamped);
            } finally {
              reentrant[0] = false;
            }
          }
        };
    splitPane.widthProperty().addListener((obs, oldVal, newVal) -> reclamp.run());
    splitPane.heightProperty().addListener((obs, oldVal, newVal) -> reclamp.run());
    divider.positionProperty().addListener((obs, oldVal, newVal) -> reclamp.run());
  }
}

package ui.pinboard;

/**
 * Pure (no-FX) placement math for cascading freshly-added pinboard notes
 * (.scratch/ingame-fixes-3 issue 01). A new note is offset from the previous one by a small step so
 * additions never land directly on top of each other; the offset wraps back to zero every {@code
 * wrap} notes so the cascade stays near the visible top-left and never marches off the board.
 *
 * <p>Kept FX-free so the cascade is unit-testable without the JavaFX toolkit, like {@code
 * TerminalScroll} / {@code DisplayNames}.
 */
public final class NoteCascade {

  private NoteCascade() {}

  /**
   * The per-axis pixel offset to add to the base position for the note at {@code step}. Step 0 is the
   * base (offset 0); each subsequent step adds {@code offset} px; the value wraps to 0 every {@code
   * wrap} steps. Negative steps are handled safely.
   *
   * @param step the running new-note index (0-based)
   * @param offset px added per step (e.g. 24)
   * @param wrap number of steps before wrapping back to the base (must be {@code > 0})
   */
  public static double offsetFor(int step, double offset, int wrap) {
    if (wrap <= 0) {
      return 0;
    }
    int s = ((step % wrap) + wrap) % wrap; // safe positive modulo
    return s * offset;
  }
}

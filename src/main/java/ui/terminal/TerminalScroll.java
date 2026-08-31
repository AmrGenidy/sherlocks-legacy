package ui.terminal;

/**
 * Pure (no-FX) decisions for the terminal's history-aware auto-scroll
 * (.scratch/ingame-terminal-polish issue 05 / DEC-7). {@link TerminalView} owns the {@code
 * ScrollPane} and its {@code vvalue}; it delegates the two judgements that caused the auto-scroll
 * regression here so they can be unit-tested without the JavaFX toolkit.
 *
 * <p>The original cut inferred "pinned to bottom" from <em>every</em> {@code vvalue} change, so a
 * programmatic or layout-induced nudge to a non-bottom value stuck the pin to {@code false} and
 * auto-scroll silently died. The fix: only a <b>genuine user scroll-up</b> (a non-programmatic
 * <i>decrease</i>) drops the pin, and reaching the bottom restores it.
 */
public final class TerminalScroll {

  private TerminalScroll() {}

  /**
   * Is the scroll bar at (or within {@code epsilon} of) the bottom? A tolerant epsilon means
   * returning "near enough" to the bottom re-pins, so float noise never leaves the view stranded.
   */
  public static boolean isAtBottom(double value, double vmax, double epsilon) {
    return value >= vmax - epsilon;
  }

  /**
   * Did the user deliberately scroll <b>up</b>? True only when the change is not one the view caused
   * itself ({@code programmatic == false}) and the value <i>decreased</i> by more than {@code
   * epsilon} (so layout changes — which preserve the normalized position — and float noise do not
   * count). This is the only thing that unpins auto-scroll.
   */
  public static boolean isUserScrollUp(
      double oldValue, double newValue, boolean programmatic, double epsilon) {
    return isUserScrollUp(oldValue, newValue, programmatic, false, epsilon);
  }

  /**
   * Growth-aware variant (.scratch/terminal-scroll-mp issue 04). When a tall multi-line block (a room
   * change, {@code look}) grows the content, the real-GUI {@code ScrollPane} keeps the pixel scrollTop
   * and so the normalized {@code vvalue} <b>drops</b> — a decrease that is NOT a user scroll-up. So a
   * decrease that coincides with content growth ({@code contentGrew == true}) must never unpin; only a
   * non-programmatic decrease while content is <b>stable</b> is a genuine user scroll-up.
   */
  public static boolean isUserScrollUp(
      double oldValue, double newValue, boolean programmatic, boolean contentGrew, double epsilon) {
    return !programmatic && !contentGrew && newValue < oldValue - epsilon;
  }
}

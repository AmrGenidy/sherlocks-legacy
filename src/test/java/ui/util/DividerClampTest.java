package ui.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Pure-maths regression tests for {@link DividerClamp#clamp} — the guard against the
 * terminal-disappears-on-resize bug (.scratch/responsive-resizing issue 01). The divider fraction
 * must always leave the leading (room) pane and the trailing (terminal) pane their minimum pixel
 * heights; when both can't fit, the terminal wins. No JavaFX needed.
 */
public class DividerClampTest {

  private static final double EPS = 1e-9;

  // The shell's vertical split: room pane min 240px, terminal min 160px (main.fxml).
  private static final double MIN_ROOM = 240;
  private static final double MIN_TERMINAL = 160;

  @Test
  public void dividerInsideValidRangeIsUntouched() {
    // total 1000 → valid range [0.24, 0.84]
    assertEquals(0.5, DividerClamp.clamp(0.5, 1000, MIN_ROOM, MIN_TERMINAL), EPS);
    assertEquals(0.24, DividerClamp.clamp(0.24, 1000, MIN_ROOM, MIN_TERMINAL), EPS);
    assertEquals(0.84, DividerClamp.clamp(0.84, 1000, MIN_ROOM, MIN_TERMINAL), EPS);
  }

  @Test
  public void dividerTooLowIsClampedToLeadingMinimum() {
    // Divider collapsed the room pane: clamp up to minLeading/total.
    assertEquals(0.24, DividerClamp.clamp(0.05, 1000, MIN_ROOM, MIN_TERMINAL), EPS);
  }

  @Test
  public void dividerTooHighIsClampedToProtectTerminal() {
    // THE headline bug: divider near 1.0 hides the terminal. Clamp to 1 - minTrailing/total.
    assertEquals(0.84, DividerClamp.clamp(0.99, 1000, MIN_ROOM, MIN_TERMINAL), EPS);
    assertEquals(0.84, DividerClamp.clamp(1.0, 1000, MIN_ROOM, MIN_TERMINAL), EPS);
  }

  @Test
  public void terminalKeepsMinimumAtEveryTotalAboveCombinedMinimums() {
    for (double total = MIN_ROOM + MIN_TERMINAL; total <= 4000; total += 37) {
      for (double divider = 0.0; divider <= 1.0; divider += 0.05) {
        double clamped = DividerClamp.clamp(divider, total, MIN_ROOM, MIN_TERMINAL);
        double terminalPx = (1.0 - clamped) * total;
        double roomPx = clamped * total;
        if (terminalPx < MIN_TERMINAL - EPS) {
          throw new AssertionError(
              "terminal collapsed: total=" + total + " divider=" + divider + " -> " + terminalPx);
        }
        if (roomPx < MIN_ROOM - EPS) {
          throw new AssertionError(
              "room collapsed: total=" + total + " divider=" + divider + " -> " + roomPx);
        }
      }
    }
  }

  @Test
  public void whenBothMinimumsCannotFitTrailingPaneWins() {
    // total 300 < 240 + 160: the terminal still gets its full 160px (divider = 1 - 160/300).
    double clamped = DividerClamp.clamp(0.9, 300, MIN_ROOM, MIN_TERMINAL);
    assertEquals(1.0 - MIN_TERMINAL / 300.0, clamped, EPS);
    assertEquals(MIN_TERMINAL, (1.0 - clamped) * 300, EPS);
  }

  @Test
  public void tinyTotalGivesEverythingToTrailingPane() {
    // total 100 < minTrailing: hi = 1 - 160/100 < 0 → clamped to 0, terminal takes the pane.
    assertEquals(0.0, DividerClamp.clamp(0.7, 100, MIN_ROOM, MIN_TERMINAL), EPS);
  }

  @Test
  public void degenerateTotalOnlyNormalizesDividerIntoUnitRange() {
    assertEquals(0.7, DividerClamp.clamp(0.7, 0, MIN_ROOM, MIN_TERMINAL), EPS);
    assertEquals(1.0, DividerClamp.clamp(1.3, -5, MIN_ROOM, MIN_TERMINAL), EPS);
    assertEquals(0.0, DividerClamp.clamp(-0.2, Double.NaN, MIN_ROOM, MIN_TERMINAL), EPS);
  }

  @Test
  public void nonFiniteDividerFallsBackToCenterThenClamps() {
    assertEquals(0.5, DividerClamp.clamp(Double.NaN, 1000, MIN_ROOM, MIN_TERMINAL), EPS);
    // Center then clamped into the valid range when minimums demand it.
    assertEquals(
        1.0 - MIN_TERMINAL / 300.0,
        DividerClamp.clamp(Double.POSITIVE_INFINITY, 300, MIN_ROOM, MIN_TERMINAL),
        EPS);
  }

  @Test
  public void zeroMinimumsLeaveFullRange() {
    assertEquals(0.0, DividerClamp.clamp(0.0, 1000, 0, 0), EPS);
    assertEquals(1.0, DividerClamp.clamp(1.0, 1000, 0, 0), EPS);
  }

  @Test
  public void horizontalSplitSidebarMinimumHolds() {
    // The shell's horizontal split: content min 480px, sidebar min 216px (main.fxml).
    // At the 1024 window minimum the divider may sit anywhere in [480/1024, 1 - 216/1024].
    double clamped = DividerClamp.clamp(0.95, 1024, 480, 216);
    assertEquals(1.0 - 216.0 / 1024.0, clamped, EPS);
    assertEquals(216.0, (1.0 - clamped) * 1024, EPS);
  }
}

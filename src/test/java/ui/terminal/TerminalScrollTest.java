package ui.terminal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins the pure auto-scroll decisions (.scratch/ingame-terminal-polish issue 05 / DEC-7) that
 * caused the "terminal never scrolls to the newest line" regression: a non-bottom {@code vvalue}
 * nudge must not unpin, only a real user scroll-up must.
 */
public class TerminalScrollTest {

  private static final double AT_BOTTOM_EPS = 0.02;
  private static final double SCROLL_EPS = 0.0005;

  @Test
  public void atBottom_withinTolerance_isTrue() {
    assertTrue(TerminalScroll.isAtBottom(1.0, 1.0, AT_BOTTOM_EPS));
    assertTrue(TerminalScroll.isAtBottom(0.99, 1.0, AT_BOTTOM_EPS));
    assertTrue(TerminalScroll.isAtBottom(0.981, 1.0, AT_BOTTOM_EPS));
  }

  @Test
  public void notAtBottom_whenScrolledUp() {
    assertFalse(TerminalScroll.isAtBottom(0.95, 1.0, AT_BOTTOM_EPS));
    assertFalse(TerminalScroll.isAtBottom(0.0, 1.0, AT_BOTTOM_EPS));
  }

  @Test
  public void userScrollUp_isDetected_whenNotProgrammatic() {
    assertTrue(TerminalScroll.isUserScrollUp(1.0, 0.8, false, SCROLL_EPS));
    assertTrue(TerminalScroll.isUserScrollUp(0.5, 0.49, false, SCROLL_EPS));
  }

  @Test
  public void programmaticDecrease_isNotAUserScrollUp() {
    // The view's own scroll-to-bottom (and anything else it drives) must never be read as a user
    // action — this is the guard that the regression lacked.
    assertFalse(TerminalScroll.isUserScrollUp(1.0, 0.8, true, SCROLL_EPS));
  }

  @Test
  public void scrollingDown_isNotAUserScrollUp() {
    assertFalse(TerminalScroll.isUserScrollUp(0.8, 1.0, false, SCROLL_EPS));
  }

  @Test
  public void tinyJitterOrNoChange_isNotAUserScrollUp() {
    // Layout-preserving / float-noise changes below the epsilon must not unpin.
    assertFalse(TerminalScroll.isUserScrollUp(0.5, 0.5, false, SCROLL_EPS));
    assertFalse(TerminalScroll.isUserScrollUp(0.5, 0.4999, false, SCROLL_EPS));
  }

  // --- issue 04: a multi-line block (room change / look) grows the content, so the real-GUI
  // ScrollPane drops vvalue. That drop must NOT be read as a user scroll-up. ---

  @Test
  public void vvalueDropFromContentGrowth_isNotAUserScrollUp() {
    // This is the bug: a big drop (1.0 -> 0.5) that coincides with content GROWTH would, under the
    // growth-blind rule, unpin and strand the new lines below the viewport. Growth-aware = NOT a
    // scroll-up.
    assertFalse(
        "a vvalue drop caused by content growth must not unpin",
        TerminalScroll.isUserScrollUp(1.0, 0.5, false, /* contentGrew= */ true, SCROLL_EPS));
  }

  @Test
  public void userScrollUp_whileContentStable_stillUnpins() {
    assertTrue(
        "a genuine scroll-up while content is stable must still unpin",
        TerminalScroll.isUserScrollUp(1.0, 0.5, false, /* contentGrew= */ false, SCROLL_EPS));
  }

  @Test
  public void growthDrop_documentsTheGrowthBlindBug() {
    // The growth-BLIND overload (no contentGrew) cannot tell the difference — it reports the growth
    // drop as a scroll-up, which is exactly the multi-line-block bug.
    assertTrue(TerminalScroll.isUserScrollUp(1.0, 0.5, false, SCROLL_EPS));
  }
}

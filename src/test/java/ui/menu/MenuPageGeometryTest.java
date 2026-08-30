package ui.menu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Regression for the clipped bottom-strip plate (GUI G6 #2): the "Leave lobby" / back plate was
 * over-painted by {@link MenuPage}'s ochre bottom-left corner flourish because the content padding
 * sat nearer the edge than the flourish reaches. These are pure geometry checks on the shared
 * frame-metric helpers — no JavaFX scene — so the clearance is guaranteed at every window size.
 */
public class MenuPageGeometryTest {

  // Representative sizes from the mandated min (1024×720) up to a large maximised window.
  private static final double[][] SIZES = {
    {1024, 720}, {1280, 800}, {1366, 768}, {1600, 900}, {1920, 1080}, {2560, 1440}
  };

  @Test
  public void bottomStripClearsTheCornerFlourishAtEverySize() {
    for (double[] wh : SIZES) {
      double w = wh[0];
      double h = wh[1];
      double minDim = Math.min(w, h);
      // The plate's left edge from the window = content padding + the strip's extra inset.
      double plateLeft = MenuPage.contentPad(minDim) + MenuPage.bottomStripInset(w, h);
      double reach = MenuPage.cornerFlourishReach(minDim);
      assertTrue(
          "Bottom strip must clear the corner flourish at "
              + w
              + "x"
              + h
              + " (plateLeft "
              + plateLeft
              + " <= reach "
              + reach
              + ")",
          plateLeft > reach);
    }
  }

  @Test
  public void flourishActuallyOverranTheRawContentPadding() {
    // The bug's premise: with no extra inset, the content padding alone does NOT clear the flourish
    // (otherwise the fix would be unnecessary). True at the mandated minimum size.
    double minDim = Math.min(1024, 720);
    assertTrue(
        "Pre-fix premise: content padding sits inside the flourish reach",
        MenuPage.contentPad(minDim) < MenuPage.cornerFlourishReach(minDim));
  }

  @Test
  public void insetIsNonNegativeAndZeroForADegenerateSize() {
    assertTrue(MenuPage.bottomStripInset(1024, 720) > 0);
    assertEquals(0.0, MenuPage.bottomStripInset(0, 0), 0.0);
    assertEquals(0.0, MenuPage.bottomStripInset(-10, 500), 0.0);
  }

  @Test
  public void topRightOverlayClearsTheCornerFlourishAtEverySize() {
    // The main-menu profile chip is pinned top-right by this inset (measured from the page edge);
    // it must sit clear of the ochre top-right flourish at the min (1024×720) and maximised.
    for (double[] wh : SIZES) {
      double w = wh[0];
      double h = wh[1];
      double reach = MenuPage.cornerFlourishReach(Math.min(w, h));
      double overlayInset = MenuPage.cornerOverlayInset(w, h);
      assertTrue(
          "Top-right overlay must clear the corner flourish at "
              + w
              + "x"
              + h
              + " (inset "
              + overlayInset
              + " > reach "
              + reach
              + ")",
          overlayInset > reach);
    }
  }

  @Test
  public void overlayInsetIsZeroForADegenerateSize() {
    assertTrue(MenuPage.cornerOverlayInset(1024, 720) > 0);
    assertEquals(0.0, MenuPage.cornerOverlayInset(0, 0), 0.0);
    assertEquals(0.0, MenuPage.cornerOverlayInset(500, -10), 0.0);
  }

  @Test
  public void cornerFlourishReachGrowsWithThePage() {
    assertTrue(MenuPage.cornerFlourishReach(1440) > MenuPage.cornerFlourishReach(720));
  }
}

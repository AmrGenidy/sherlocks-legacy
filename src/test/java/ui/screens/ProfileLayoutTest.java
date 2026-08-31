package ui.screens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The profile screen must reflow to fit a short window without a scrollbar (gui-profile-layout).
 * Pure checks on the responsive height→size maths: sizes shrink with height but stay within sane
 * bounds, and at the mandated minimum the content fits the available band even with extra gallery
 * rows.
 */
public class ProfileLayoutTest {

  @Test
  public void previewAndPortraitShrinkOnShortWindowsWithinBounds() {
    // Floors at/below MIN_H, defaults at/above REF_H, monotonic between.
    assertEquals(ProfileLayout.PREVIEW_H_FLOOR, ProfileLayout.previewHeight(720), 0.001);
    assertEquals(ProfileLayout.PREVIEW_H_DEFAULT, ProfileLayout.previewHeight(900), 0.001);
    assertEquals(ProfileLayout.PREVIEW_H_DEFAULT, ProfileLayout.previewHeight(1400), 0.001);
    assertTrue(ProfileLayout.previewHeight(810) > ProfileLayout.previewHeight(720));
    assertTrue(ProfileLayout.previewHeight(810) < ProfileLayout.previewHeight(900));

    assertEquals(ProfileLayout.PORTRAIT_H_FLOOR, ProfileLayout.portraitHeight(700), 0.001);
    assertEquals(ProfileLayout.PORTRAIT_H_DEFAULT, ProfileLayout.portraitHeight(1080), 0.001);
    assertTrue(ProfileLayout.portraitHeight(820) > ProfileLayout.portraitHeight(720));
  }

  @Test
  public void widthsPreserveAspectRatio() {
    double h = 900;
    assertEquals(132.0, ProfileLayout.previewWidth(h), 0.5);
    assertEquals(72.0, ProfileLayout.portraitWidth(h), 0.5);
  }

  @Test
  public void blockGapStepsOnThe8pxScaleAndNeverGrowsWithShrinkage() {
    assertEquals(24.0, ProfileLayout.blockGap(900), 0.001);
    assertEquals(16.0, ProfileLayout.blockGap(820), 0.001);
    assertEquals(8.0, ProfileLayout.blockGap(720), 0.001);
    assertTrue(ProfileLayout.blockGap(720) <= ProfileLayout.blockGap(900));
  }

  @Test
  public void contentFitsTheAvailableBandAtMinHeightEvenWithThreeRows() {
    // The whole point: at 1024x720 the shrunk column fits between the title block and the strip,
    // even if the gallery wraps to three rows — so it never overflows into the strip or the frame.
    double band = ProfileLayout.availableBand(720);
    assertTrue(
        "3-row content must fit at min height",
        ProfileLayout.estimatedContentHeight(720, 3) <= band);
    assertTrue(
        "2-row content fits with margin", ProfileLayout.estimatedContentHeight(720, 2) <= band);
  }
}

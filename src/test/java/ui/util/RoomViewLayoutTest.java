package ui.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pure-geometry regression tests for {@link RoomViewLayout} — the scaling/anchoring rules from
 * DESIGN.md §4. No JavaFX needed, so these run fast and deterministically and lock down the maths
 * that the old "stretch to fill" RoomView got wrong.
 */
public class RoomViewLayoutTest {

  private static final double EPS = 1e-9;

  @Test
  public void landscapeImageInSquareViewLetterboxesVertically() {
    // 200x100 image inside a 100x100 view: scale 0.5, rendered 100x50, centered (top offset 25).
    RoomViewLayout.Rect r = RoomViewLayout.renderedImageRect(100, 100, 200, 100);
    assertEquals(0.0, r.x(), EPS);
    assertEquals(25.0, r.y(), EPS);
    assertEquals(100.0, r.width(), EPS);
    assertEquals(50.0, r.height(), EPS);
  }

  @Test
  public void portraitImageInSquareViewPillarboxesHorizontally() {
    // 100x200 image inside 100x100: scale 0.5, rendered 50x100, centered (left offset 25).
    RoomViewLayout.Rect r = RoomViewLayout.renderedImageRect(100, 100, 100, 200);
    assertEquals(25.0, r.x(), EPS);
    assertEquals(0.0, r.y(), EPS);
    assertEquals(50.0, r.width(), EPS);
    assertEquals(100.0, r.height(), EPS);
  }

  @Test
  public void widePaneCentersArtworkHorizontallyWithEqualMargins() {
    // The reported bug scenario: pane wider than the rendered artwork (windowed mode).
    // 1024x720 image inside a 1600x720 pane: scale 1, rendered 1024x720,
    // offsetX = (1600 - 1024) / 2 = 288 — letterbox margins split evenly, never left-anchored.
    RoomViewLayout.Rect r = RoomViewLayout.renderedImageRect(1600, 720, 1024, 720);
    assertEquals(288.0, r.x(), EPS);
    assertEquals(0.0, r.y(), EPS);
    assertEquals(1024.0, r.width(), EPS);
    assertEquals(720.0, r.height(), EPS);
    double rightMargin = 1600 - (r.x() + r.width());
    assertEquals("left and right margins must match", r.x(), rightMargin, EPS);
  }

  @Test
  public void tallPaneCentersArtworkVerticallyWithEqualMargins() {
    // Height is the leftover dimension: offsetY = (paneH - renderedH) / 2.
    RoomViewLayout.Rect r = RoomViewLayout.renderedImageRect(1024, 1000, 1024, 720);
    assertEquals(0.0, r.x(), EPS);
    assertEquals(140.0, r.y(), EPS);
    double bottomMargin = 1000 - (r.y() + r.height());
    assertEquals("top and bottom margins must match", r.y(), bottomMargin, EPS);
  }

  @Test
  public void aspectRatioIsPreservedNotStretched() {
    RoomViewLayout.Rect r = RoomViewLayout.renderedImageRect(400, 100, 200, 100);
    // Image ratio 2:1 must be preserved: rendered 200x100, not stretched to 400x100.
    assertEquals(2.0, r.width() / r.height(), EPS);
  }

  @Test
  public void degenerateInputsFallBackToFullView() {
    RoomViewLayout.Rect r = RoomViewLayout.renderedImageRect(300, 200, 0, 0);
    assertEquals(0.0, r.x(), EPS);
    assertEquals(0.0, r.y(), EPS);
    assertEquals(300.0, r.width(), EPS);
    assertEquals(200.0, r.height(), EPS);
  }

  @Test
  public void anchorsMapWithinRenderedRectNotRawView() {
    RoomViewLayout.Rect r =
        RoomViewLayout.renderedImageRect(100, 100, 200, 100); // y offset 25, h 50
    // Normalized centre (0.5, 0.5) lands at the centre of the artwork, not the view.
    assertEquals(50.0, RoomViewLayout.anchorX(r, 0.5), EPS);
    assertEquals(50.0, RoomViewLayout.anchorY(r, 0.5), EPS); // 25 + 0.5*50
    // Top-left of the artwork.
    assertEquals(0.0, RoomViewLayout.anchorX(r, 0.0), EPS);
    assertEquals(25.0, RoomViewLayout.anchorY(r, 0.0), EPS);
    // Out-of-range normals are clamped into the rect.
    assertEquals(75.0, RoomViewLayout.anchorY(r, 2.0), EPS);
  }

  @Test
  public void suspectsRenderLargerThanObjectsAtSameScale() {
    double renderedH = 600;
    double suspect =
        RoomViewLayout.spriteHeight(renderedH, RoomViewLayout.SUSPECT_BASE_FACTOR, 1.0);
    double object = RoomViewLayout.spriteHeight(renderedH, RoomViewLayout.OBJECT_BASE_FACTOR, 1.0);
    assertTrue(
        "Suspect base factor must exceed object base factor",
        RoomViewLayout.SUSPECT_BASE_FACTOR > RoomViewLayout.OBJECT_BASE_FACTOR);
    assertTrue(
        "Suspect sprite must be visibly larger than an object sprite", suspect > object * 1.5);
  }

  @Test
  public void imageScaleMultipliesBaseSize() {
    double base = RoomViewLayout.spriteHeight(600, RoomViewLayout.OBJECT_BASE_FACTOR, 1.0);
    double doubled = RoomViewLayout.spriteHeight(600, RoomViewLayout.OBJECT_BASE_FACTOR, 2.0);
    assertEquals(2.0, doubled / base, EPS);
  }

  @Test
  public void nonPositiveOrInfiniteScaleFallsBackToOne() {
    double base = RoomViewLayout.spriteHeight(600, RoomViewLayout.OBJECT_BASE_FACTOR, 1.0);
    assertEquals(
        base, RoomViewLayout.spriteHeight(600, RoomViewLayout.OBJECT_BASE_FACTOR, 0.0), EPS);
    assertEquals(
        base, RoomViewLayout.spriteHeight(600, RoomViewLayout.OBJECT_BASE_FACTOR, -3.0), EPS);
    assertEquals(
        base,
        RoomViewLayout.spriteHeight(
            600, RoomViewLayout.OBJECT_BASE_FACTOR, Double.POSITIVE_INFINITY),
        EPS);
  }
}

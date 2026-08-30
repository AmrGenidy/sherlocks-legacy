package ui.util;

/**
 * Pure geometry for {@link RoomView}: where the room artwork actually renders inside the view,
 * where a normalized (0–1) sprite anchor lands, and how big a sprite should be. Kept free of JavaFX
 * so the scaling/anchoring rules (DESIGN.md §4) can be unit-tested without a display.
 *
 * <p>Policy: the room image is <b>contained</b> (scaled proportionally to fit, preserving aspect
 * ratio, centered with a mat around it) — never stretched. Object and suspect sprites are anchored
 * to their normalized positions <em>within the rendered artwork rectangle</em>, not the raw view,
 * so they stay glued to background features at every window size.
 */
public final class RoomViewLayout {

  /** Base sprite height as a fraction of the rendered artwork height. */
  public static final double SUSPECT_BASE_FACTOR = 0.30;

  public static final double OBJECT_BASE_FACTOR = 0.15;

  /** An axis-aligned rectangle in view pixels. */
  public record Rect(double x, double y, double width, double height) {}

  private RoomViewLayout() {}

  /**
   * The rectangle the room image occupies inside a {@code viewW × viewH} view when scaled to fit
   * while preserving aspect ratio (contain) and centered. Falls back to the whole view when image
   * or view dimensions are unknown/degenerate, so callers always get a usable rectangle.
   */
  public static Rect renderedImageRect(double viewW, double viewH, double imageW, double imageH) {
    if (viewW <= 0 || viewH <= 0) {
      return new Rect(0, 0, Math.max(0, viewW), Math.max(0, viewH));
    }
    if (imageW <= 0 || imageH <= 0) {
      return new Rect(0, 0, viewW, viewH); // No image info: treat the whole view as the canvas.
    }
    double scale = Math.min(viewW / imageW, viewH / imageH);
    double renderedW = imageW * scale;
    double renderedH = imageH * scale;
    double offsetX = (viewW - renderedW) / 2.0;
    double offsetY = (viewH - renderedH) / 2.0;
    return new Rect(offsetX, offsetY, renderedW, renderedH);
  }

  /** X pixel for a normalized horizontal position within the rendered artwork rectangle. */
  public static double anchorX(Rect rect, double normX) {
    return rect.x() + clamp01(normX) * rect.width();
  }

  /** Y pixel for a normalized vertical position within the rendered artwork rectangle. */
  public static double anchorY(Rect rect, double normY) {
    return rect.y() + clamp01(normY) * rect.height();
  }

  /**
   * Sprite height in pixels: a base fraction of the rendered artwork height, times the per-sprite
   * {@code imageScale}. A non-positive or non-finite scale is treated as 1.0.
   */
  public static double spriteHeight(double renderedHeight, double baseFactor, double imageScale) {
    double scale = (Double.isFinite(imageScale) && imageScale > 0) ? imageScale : 1.0;
    return Math.max(0, renderedHeight) * baseFactor * scale;
  }

  private static double clamp01(double v) {
    if (v < 0) return 0;
    if (v > 1) return 1;
    return v;
  }
}

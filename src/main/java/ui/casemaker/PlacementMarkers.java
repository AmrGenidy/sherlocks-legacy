package ui.casemaker;

import java.util.HashMap;
import java.util.Map;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import ui.util.RoomViewLayout;

/**
 * Shared sprite + name-caption rendering and geometry for the Case Maker placement previews
 * ({@link SuspectPlacementView}). Each element is drawn as two independently-draggable nodes — a
 * sprite (sized with the real {@link RoomViewLayout} math) and its name caption (its own handle) —
 * anchored inside the rendered-artwork rect exactly like in-game.
 *
 * <p><b>Tight hit + selection box.</b> Sprites are transparent-margined PNGs, so the full image
 * rectangle is much larger than the visible figure. The image still renders full-size (so placement
 * stays WYSIWYG with RoomView), but two things are inset to the <em>opaque</em> pixels: the selection
 * outline is a separate box hugging the opaque bounding box, and the ImageView is
 * {@code pickOnBounds=false} so only non-transparent pixels intercept clicks/drags — transparent
 * margins pass through to whatever marker is behind, so overlapping figures are each clickable.
 */
final class PlacementMarkers {

  private PlacementMarkers() {}

  /** Default gap (px) below the sprite for an unplaced caption — matches RoomView's default. */
  static final double LABEL_GAP_PX = 15;

  // Alpha (0..255) above which a pixel counts as "opaque" for the visible-bounds computation.
  private static final int ALPHA_THRESHOLD = 10;
  private static final double[] FULL_BOUNDS = {0.0, 0.0, 1.0, 1.0};
  // Opaque bounds are expensive to scan, so cache them per image source URL (images are reloaded
  // fresh each rebuild, but the same file resolves to the same URL). FX-thread only.
  private static final Map<String, double[]> OPAQUE_CACHE = new HashMap<>();

  /**
   * The nodes inside a sprite marker: the image (or a placeholder box), the outline that hugs the
   * opaque bounds, and the opaque bounding box as {@code [fx, fy, fw, fh]} fractions of the image.
   */
  record MarkerNodes(
      ImageView sprite, Region placeholder, Region outline, boolean hasImage, double[] opaque) {}

  /** A draggable sprite node: the element image, or a sized placeholder box when it has no art. */
  static StackPane buildSprite(Image image) {
    ImageView sprite = new ImageView();
    // Independent horizontal/vertical sizing (set in sizeSprite), so no aspect lock.
    sprite.setPreserveRatio(false);
    // Filtered scaling — placement markers draw the art far smaller than it is authored.
    sprite.setSmooth(true);
    sprite.setFitWidth(48);
    sprite.setFitHeight(48); // modest until the first layout pass sets the real sprite size
    // Only opaque pixels intercept the mouse; transparent margins fall through to markers behind.
    sprite.setPickOnBounds(false);
    Region placeholder = new Region();
    placeholder.getStyleClass().add("casemaker-sprite-box");
    // The selection box, hugging the opaque figure (sized in sizeSprite). Purely visual.
    Region outline = new Region();
    outline.getStyleClass().add("casemaker-sprite-outline");
    outline.setMouseTransparent(true);

    StackPane spriteBox = new StackPane();
    spriteBox.getStyleClass().add("casemaker-sprite");
    // The marker's press/drag/select handlers live on this StackPane, so it must not be a pick
    // target on its own (transparent) bounds — otherwise the whole image rectangle, transparent
    // margins included, would be grabbable and overlapping suspects/Watson would smother each
    // other. It carries no background, so pickOnBounds(false) leaves only the sprite's opaque
    // pixels (ImageView pickOnBounds(false) above) — or the placeholder box — pickable.
    spriteBox.setPickOnBounds(false);
    if (image != null) {
      sprite.setImage(image);
      spriteBox.getChildren().addAll(sprite, outline);
    } else {
      spriteBox.getChildren().addAll(placeholder, outline);
    }
    spriteBox.setUserData(
        new MarkerNodes(
            sprite, placeholder, outline, image != null, image != null ? opaqueBounds(image) : FULL_BOUNDS));
    return spriteBox;
  }

  /** A draggable name-caption node (its own small handle), positioned relative to its sprite. */
  static Label buildCaption(String name) {
    Label caption = new Label(name);
    caption.getStyleClass().addAll("casemaker-object-marker", "casemaker-label-handle");
    return caption;
  }

  /**
   * Sizes the sprite from a base (scale-1) height and independent horizontal/vertical scales,
   * mirrors it per the flip flags, and re-fits the opaque selection outline to the visible figure.
   * Width uses the image aspect so scaleX==scaleY==1 renders as the old preserveRatio path did.
   */
  static void sizeSprite(
      StackPane sprite,
      double baseHeight,
      double scaleX,
      double scaleY,
      boolean flipX,
      boolean flipY) {
    if (!(sprite.getUserData() instanceof MarkerNodes nodes)) {
      return;
    }
    double renderedW;
    double renderedH;
    if (nodes.hasImage()) {
      ImageView iv = nodes.sprite();
      Image img = iv.getImage();
      double aspect = (img != null && img.getHeight() > 0) ? img.getWidth() / img.getHeight() : 0.6;
      renderedH = baseHeight * scaleY;
      renderedW = baseHeight * aspect * scaleX;
      iv.setFitWidth(renderedW);
      iv.setFitHeight(renderedH);
      iv.setScaleX(flipX ? -1 : 1);
      iv.setScaleY(flipY ? -1 : 1);
    } else {
      // Portrait-ish placeholder so the size still reads without authored art.
      renderedW = Math.max(8, baseHeight * 0.55 * scaleX);
      renderedH = Math.max(8, baseHeight * scaleY);
      Region ph = nodes.placeholder();
      ph.setPrefSize(renderedW, renderedH);
      ph.setMinSize(renderedW, renderedH);
      ph.setScaleX(flipX ? -1 : 1);
      ph.setScaleY(flipY ? -1 : 1);
    }
    fitOutline(nodes.outline(), nodes.opaque(), renderedW, renderedH);
  }

  /** Sizes + offsets the outline to the opaque sub-rectangle of the (centered) rendered image. */
  private static void fitOutline(Region outline, double[] opaque, double renderedW, double renderedH) {
    double ow = opaque[2] * renderedW;
    double oh = opaque[3] * renderedH;
    // Offset of the opaque-box centre from the image centre (the image is centred in the StackPane).
    double tx = (opaque[0] + opaque[2] / 2 - 0.5) * renderedW;
    double ty = (opaque[1] + opaque[3] / 2 - 0.5) * renderedH;
    outline.setPrefSize(ow, oh);
    outline.setMinSize(ow, oh);
    outline.setMaxSize(ow, oh);
    outline.setTranslateX(tx);
    outline.setTranslateY(ty);
  }

  /** Toggles the tight selection outline on a sprite marker. */
  static void setSpriteSelected(StackPane sprite, boolean selected) {
    if (!(sprite.getUserData() instanceof MarkerNodes nodes)) {
      return;
    }
    nodes.outline().getStyleClass().remove("selected");
    if (selected) {
      nodes.outline().getStyleClass().add("selected");
    }
  }

  /** Centers a node's box on {@code (cx, cy)} in its parent. */
  static void centerNode(Region node, double cx, double cy) {
    double w = node.getWidth() > 0 ? node.getWidth() : node.prefWidth(-1);
    double h = node.getHeight() > 0 ? node.getHeight() : node.prefHeight(-1);
    node.setLayoutX(cx - w / 2);
    node.setLayoutY(cy - h / 2);
  }

  /**
   * Places a caption relative to its sprite: at {@code (dx, dy)} fractions of sprite height from the
   * sprite centre when authored, else the default just-below-the-sprite spot (matches RoomView).
   */
  static void layoutCaption(
      Label caption, double anchorX, double anchorY, double spriteH, Double dx, Double dy) {
    double cx = dx != null ? anchorX + dx * spriteH : anchorX;
    double cy = dy != null ? anchorY + dy * spriteH : anchorY + spriteH / 2 + LABEL_GAP_PX;
    centerNode(caption, cx, cy);
  }

  /** The rendered-artwork rectangle inside a preview layer, per the real RoomViewLayout math. */
  static RoomViewLayout.Rect rect(Pane layer, Image background) {
    double w = layer.getWidth();
    double h = layer.getHeight();
    double iw = background != null ? background.getWidth() : 0;
    double ih = background != null ? background.getHeight() : 0;
    return RoomViewLayout.renderedImageRect(w, h, iw, ih);
  }

  /**
   * The opaque bounding box of {@code image} as {@code [fx, fy, fw, fh]} fractions of its size
   * (pixels with alpha above a small threshold). Computed once per image source and cached; a fully
   * transparent/unreadable image returns the whole rectangle.
   */
  static double[] opaqueBounds(Image image) {
    if (image == null) {
      return FULL_BOUNDS;
    }
    // Not fully decoded (or errored): fall back to the whole rendered image so the selection box is
    // never empty. Don't cache this — recompute the tight box once the image is available.
    if (image.isError() || image.getProgress() < 1.0 || image.getPixelReader() == null) {
      return FULL_BOUNDS;
    }
    String url = image.getUrl();
    if (url != null && !url.isEmpty()) {
      double[] cached = OPAQUE_CACHE.get(url);
      if (cached != null) {
        return cached;
      }
    }
    double[] bounds = computeOpaqueBounds(image);
    if (url != null && !url.isEmpty()) {
      OPAQUE_CACHE.put(url, bounds);
    }
    return bounds;
  }

  private static double[] computeOpaqueBounds(Image image) {
    int w = (int) image.getWidth();
    int h = (int) image.getHeight();
    PixelReader reader = image.getPixelReader();
    if (w <= 0 || h <= 0 || reader == null) {
      return FULL_BOUNDS;
    }
    int minX = w;
    int minY = h;
    int maxX = -1;
    int maxY = -1;
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int alpha = (reader.getArgb(x, y) >>> 24) & 0xff;
        if (alpha > ALPHA_THRESHOLD) {
          if (x < minX) minX = x;
          if (x > maxX) maxX = x;
          if (y < minY) minY = y;
          if (y > maxY) maxY = y;
        }
      }
    }
    if (maxX < minX || maxY < minY) {
      return FULL_BOUNDS; // fully transparent — nothing to hug
    }
    return new double[] {
      minX / (double) w, minY / (double) h, (maxX - minX + 1) / (double) w, (maxY - minY + 1) / (double) h
    };
  }
}

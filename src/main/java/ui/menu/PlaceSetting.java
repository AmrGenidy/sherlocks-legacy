package ui.menu;

import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import ui.util.Palette;

/**
 * A detective's place-setting at the lobby table (MENU_DESIGN #4): a framed portrait, a nameplate
 * and an optional wax-stud badge. Two readings share one engraved frame so the two seats read as a
 * matched pair:
 *
 * <ul>
 *   <li>{@link #seated(String, String, String, boolean)} — a player whose identity is known: their
 *       chosen preset avatar (with the engraved bust as the fallback for an unknown/blank id) and
 *       display name; {@code partnerAccent} petrol-frames the joined player's seat;
 *   <li>{@link #awaiting(String)} — an empty seat: the faint engraved bust behind a faded-vellum
 *       frame and a warm "awaiting" caption, so a half-filled lobby never reads as broken.
 * </ul>
 *
 * <p>Text is passed in already localized; the node draws only linework, so it carries no language
 * of its own.
 */
public class PlaceSetting extends VBox {

  private static final double PORTRAIT_W = 150;
  private static final double PORTRAIT_H = 164;

  private PlaceSetting() {
    setAlignment(Pos.TOP_CENTER);
    setSpacing(10);
    setFillWidth(false);
    setMaxWidth(VBox.USE_PREF_SIZE);
  }

  /**
   * A seated player whose real identity is known (player-profile feature): their chosen preset
   * avatar (resolved via {@link AvatarImages}, with the engraved bust as the fallback for an
   * unknown/blank id) and their display name. {@code partnerAccent} petrol-frames the joined
   * player's seat; the host seat uses the plain Ink/ochre frame.
   */
  public static PlaceSetting seated(
      String name, String avatarId, String badge, boolean partnerAccent) {
    Image image = AvatarImages.image(avatarId);
    // The joining player with no avatar of their own gets the petrol-framed partner preset
    // (docs/PRESET_ART_WIRING.md); the host seat keeps the faint engraved bust.
    if (image == null && partnerAccent) {
      image = AvatarImages.image(common.PlayerAvatars.DEFAULT_ID);
    }
    javafx.scene.Node art = (image != null) ? portraitImage(image) : engravedBust(1.0);
    PlaceSetting seat = new PlaceSetting();
    StackPane portrait =
        partnerAccent
            ? framedPortrait(art, "mp-seat", "mp-seat--partner")
            : framedPortrait(art, "mp-seat");
    seat.getChildren().add(portrait);
    seat.addNameAndBadge(name, badge);
    return seat;
  }

  /** An empty seat — a faint bust in a faded frame, with a warm caption in place of a name. */
  public static PlaceSetting awaiting(String caption) {
    PlaceSetting seat = new PlaceSetting();
    StackPane portrait = framedPortrait(engravedBust(0.28), "mp-seat", "mp-seat--awaiting");
    seat.getChildren().add(portrait);
    Label captionLabel = new Label(caption);
    captionLabel.getStyleClass().add("mp-seat-awaiting-name");
    captionLabel.setWrapText(true);
    captionLabel.setMaxWidth(PORTRAIT_W + 20);
    captionLabel.setAlignment(Pos.CENTER);
    seat.getChildren().add(captionLabel);
    return seat;
  }

  private void addNameAndBadge(String name, String badge) {
    Label nameLabel = new Label(name);
    nameLabel.getStyleClass().add("mp-seat-name");
    getChildren().add(nameLabel);
    if (badge != null && !badge.isBlank()) {
      Label badgeLabel = new Label(badge);
      badgeLabel.getStyleClass().add("mp-seat-badge");
      getChildren().add(badgeLabel);
    }
  }

  private static StackPane framedPortrait(javafx.scene.Node art, String... styleClasses) {
    StackPane frame = new StackPane(art);
    frame.getStyleClass().addAll(styleClasses);
    frame.setMinSize(PORTRAIT_W, PORTRAIT_H);
    frame.setPrefSize(PORTRAIT_W, PORTRAIT_H);
    frame.setMaxSize(PORTRAIT_W, PORTRAIT_H);
    frame.setAlignment(Pos.CENTER);
    return frame;
  }

  private static ImageView portraitImage(Image image) {
    ImageView view = new ImageView(image);
    view.setPreserveRatio(true);
    view.setSmooth(true);
    view.setFitWidth(PORTRAIT_W - 16);
    view.setFitHeight(PORTRAIT_H - 16);
    return view;
  }

  /**
   * A ligne-claire detective bust drawn on a canvas (consistent with {@link Frontispiece}): a
   * deerstalker silhouette in Ink on vellum, dimmed to {@code alpha} for the empty seat.
   */
  private static Canvas engravedBust(double alpha) {
    double w = PORTRAIT_W - 16;
    double h = PORTRAIT_H - 16;
    Canvas canvas = new Canvas(w, h);
    GraphicsContext g = canvas.getGraphicsContext2D();

    Color ink = withAlpha(Palette.INK, alpha);
    Color sepia = withAlpha(Palette.SEPIA, alpha * 0.9);

    double cx = w / 2.0;

    // Shoulders — a coat rising into the collar.
    g.setFill(sepia);
    g.beginPath();
    g.moveTo(w * 0.10, h);
    g.bezierCurveTo(w * 0.16, h * 0.66, w * 0.34, h * 0.58, cx, h * 0.58);
    g.bezierCurveTo(w * 0.66, h * 0.58, w * 0.84, h * 0.66, w * 0.90, h);
    g.closePath();
    g.fill();

    // Head.
    g.setFill(withAlpha(Palette.FADED_VELLUM, alpha));
    g.fillOval(cx - w * 0.17, h * 0.24, w * 0.34, h * 0.34);

    // Deerstalker cap with its centre seam and side flaps.
    g.setFill(ink);
    g.beginPath();
    g.moveTo(cx - w * 0.22, h * 0.30);
    g.quadraticCurveTo(cx, h * 0.08, cx + w * 0.22, h * 0.30);
    g.quadraticCurveTo(cx, h * 0.24, cx - w * 0.22, h * 0.30);
    g.closePath();
    g.fill();
    g.fillOval(cx - w * 0.27, h * 0.27, w * 0.12, h * 0.10); // left ear flap
    g.fillOval(cx + w * 0.15, h * 0.27, w * 0.12, h * 0.10); // right ear flap

    // Contour lines — collar + a magnifier hint, the engraving's clean outline.
    g.setStroke(ink);
    g.setLineWidth(Math.max(1, w * 0.012));
    g.strokeOval(cx - w * 0.17, h * 0.24, w * 0.34, h * 0.34);
    g.strokeLine(cx - w * 0.08, h * 0.60, cx, h * 0.70);
    g.strokeLine(cx + w * 0.08, h * 0.60, cx, h * 0.70);

    return canvas;
  }

  private static Color withAlpha(Color base, double alpha) {
    return new Color(base.getRed(), base.getGreen(), base.getBlue(), clamp(alpha));
  }

  private static double clamp(double value) {
    return Math.max(0, Math.min(1, value));
  }
}

package ui.menu;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import ui.util.Palette;

/**
 * The shared chrome behind every menu screen (MENU_DESIGN.md, DESIGN.md §1/§4/§6): an
 * aged-parchment page, framed like a leather-bound casebook plate, that <b>always fits the window
 * and never scrolls</b>.
 *
 * <p>It is built entirely on percentage/grow constraints (DESIGN.md §4): every fixed measure is a
 * fraction of the live page size recomputed on resize, so the page reflows cleanly from 1024×720 to
 * full-screen. It provides:
 *
 * <ul>
 *   <li>the parchment surface, the single soft radial <b>lamplight</b> and a ≤6% paper grain
 *       (DESIGN.md §6) — the grain is faint horizontal "laid-paper" lines drawn behind the content;
 *   <li>an ornamental <b>double ink border</b> inset proportionally from the edge, with an ochre
 *       corner flourish + oxblood dot in each corner;
 *   <li>a <b>title block</b> — a Playfair Display title, a short ochre rule with an oxblood centre
 *       dot, and a small-caps Spectral subtitle.
 * </ul>
 *
 * <p>Screens drop their body into {@link #setContent(Node)} (grows to fill) and an optional {@link
 * #setBottomStrip(Node)} (language selector, version, corner controls). The chrome carries no fixed
 * pixel positions, so it mirrors correctly and reflows under every UI language.
 */
public class MenuPage extends StackPane {

  /** Distance of the outer border from the window edge, as a fraction of the smaller dimension. */
  private static final double FRAME_INSET_FRAC = 0.018;

  /**
   * Padding from the inner border to the content, as a fraction of the page size (8px-scale feel).
   */
  private static final double CONTENT_PAD_FRAC = 0.05;

  /**
   * Grain line opacity — kept at the low end of the ≤6% §6 budget so text contrast is untouched.
   */
  private static final double GRAIN_ALPHA = 0.04;

  private final Canvas grain = new Canvas();
  private final Canvas frame = new Canvas();
  private final BorderPane contentRoot = new BorderPane();
  private final Node titleBlock;
  private Node bottomStripNode;
  private Node topRightOverlay;

  public MenuPage(String title, String subtitle) {
    getStyleClass().add("menu-page");
    // Never demand more than the window gives us — the page shrinks to fit and reflows, it never
    // forces a scrollbar (MENU_DESIGN: a scrollbar on a menu is a defect).
    setMinSize(0, 0);

    grain.setMouseTransparent(true);
    frame.setMouseTransparent(true);

    titleBlock = buildTitleBlock(title, subtitle);
    contentRoot.setTop(titleBlock);

    getChildren().addAll(grain, contentRoot, frame);

    widthProperty().addListener((obs, a, b) -> relayout());
    heightProperty().addListener((obs, a, b) -> relayout());
  }

  /**
   * The title block (Playfair title + ochre rule + subtitle), exposed so the main menu can play the
   * one-time "ink drying" entrance on it (MENU_DESIGN Motion).
   */
  public Node titleBlock() {
    return titleBlock;
  }

  /** The body of the page; grows to fill the space between the title block and the bottom strip. */
  public void setContent(Node node) {
    BorderPane.setAlignment(node, Pos.CENTER);
    contentRoot.setCenter(node);
  }

  /** The bottom strip (language selector, version, corner controls). */
  public void setBottomStrip(Node node) {
    bottomStripNode = node;
    contentRoot.setBottom(node);
    applyBottomStripInset();
  }

  /**
   * A small control pinned high in the <b>top-right</b> corner of the page, clear of the corner
   * flourish and the centred title block (the main-menu profile chip lives here — player-profile
   * feature). It overlays the page above the frame so the ornamental border never clips it, and its
   * inset tracks the live page size so it stays pinned at every window size. Pass {@code null} to
   * remove it.
   */
  public void setTopRightOverlay(Node node) {
    if (topRightOverlay != null) {
      getChildren().remove(topRightOverlay);
    }
    topRightOverlay = node;
    if (node != null) {
      StackPane.setAlignment(node, Pos.TOP_RIGHT);
      // Above the frame canvas (the last child) so the engraved border never paints over it.
      getChildren().add(node);
      applyTopRightOverlayInset();
    }
  }

  private Node buildTitleBlock(String title, String subtitle) {
    Label titleLabel = new Label(title);
    titleLabel.getStyleClass().add("menu-page-title");

    // Short ochre rule with a centred oxblood dot (DESIGN.md §5, reused under the menu title).
    Region rule = new Region();
    rule.getStyleClass().add("menu-title-rule");
    Circle dot = new Circle(3.2, Palette.OXBLOOD);
    StackPane ruleBlock = new StackPane(rule, dot);
    ruleBlock.setMaxWidth(Region.USE_PREF_SIZE);

    Label subtitleLabel = new Label(subtitle);
    subtitleLabel.getStyleClass().add("menu-subtitle");

    javafx.scene.layout.VBox block =
        new javafx.scene.layout.VBox(8, titleLabel, ruleBlock, subtitleLabel);
    block.getStyleClass().add("menu-title-block");
    block.setAlignment(Pos.CENTER);
    BorderPane.setAlignment(block, Pos.CENTER);
    return block;
  }

  private void relayout() {
    double w = getWidth();
    double h = getHeight();
    if (w <= 0 || h <= 0) {
      return;
    }
    grain.setWidth(w);
    grain.setHeight(h);
    frame.setWidth(w);
    frame.setHeight(h);

    double pad = contentPad(Math.min(w, h));
    contentRoot.setPadding(new Insets(pad, pad, pad, pad));

    drawGrain(w, h);
    drawFrame(w, h);
    applyBottomStripInset();
    applyTopRightOverlayInset();
  }

  // --- Frame geometry (shared by drawFrame and the corner-clearance maths) ---------------------

  private static double frameInset(double minDim) {
    return Math.round(minDim * FRAME_INSET_FRAC);
  }

  private static double frameOuterWidth(double minDim) {
    return Math.max(3, Math.round(minDim * 0.005)); // ≈4px outer
  }

  private static double frameGap(double minDim) {
    return Math.max(4, frameOuterWidth(minDim) * 1.6);
  }

  private static double flourishLen(double minDim) {
    return minDim * 0.055;
  }

  /** Content inset from the page edge (the BorderPane padding). */
  public static double contentPad(double minDim) {
    return Math.round(minDim * CONTENT_PAD_FRAC);
  }

  /**
   * How far the ochre corner flourish reaches inward from the page edge, along each arm — measured
   * from the same constants {@link #drawFrame} draws with. Content anchored nearer the edge than
   * this would be over-painted by the flourish (the frame is drawn on top of the content).
   */
  static double cornerFlourishReach(double minDim) {
    return frameInset(minDim) + frameGap(minDim) + flourishLen(minDim);
  }

  /**
   * The extra left/right margin the bottom strip needs <i>beyond</i> the content padding so a
   * corner-anchored plate (e.g. the "Leave lobby" / back plate, or the bottom-right corner icons)
   * clears the corner flourish at this size. Zero when the content padding already clears it.
   */
  static double bottomStripInset(double w, double h) {
    if (w <= 0 || h <= 0) {
      return 0;
    }
    double minDim = Math.min(w, h);
    double breath = Math.max(6, minDim * 0.01);
    return Math.max(0, cornerFlourishReach(minDim) + breath - contentPad(minDim));
  }

  /** Pushes the bottom strip clear of the corner flourishes, proportional to the live page size. */
  private void applyBottomStripInset() {
    if (bottomStripNode == null) {
      return;
    }
    double inset = bottomStripInset(getWidth(), getHeight());
    BorderPane.setMargin(bottomStripNode, new Insets(0, inset, 0, inset));
  }

  /**
   * The top/right inset a top-right-anchored overlay needs from the page edge so it sits high in
   * the corner yet clear of the ochre corner flourish — measured from the same frame constants
   * {@link #drawFrame} draws with, plus a little breath. Proportional to the live page size.
   */
  static double cornerOverlayInset(double w, double h) {
    if (w <= 0 || h <= 0) {
      return 0;
    }
    double minDim = Math.min(w, h);
    double breath = Math.max(8, minDim * 0.012);
    return cornerFlourishReach(minDim) + breath;
  }

  /**
   * Pins the top-right overlay clear of the corner flourish, proportional to the live page size.
   */
  private void applyTopRightOverlayInset() {
    if (topRightOverlay == null) {
      return;
    }
    double inset = cornerOverlayInset(getWidth(), getHeight());
    StackPane.setMargin(topRightOverlay, new Insets(inset, inset, 0, 0));
  }

  /** Faint horizontal laid-paper lines behind the content — the ≤6% §6 grain. */
  private void drawGrain(double w, double h) {
    GraphicsContext g = grain.getGraphicsContext2D();
    g.clearRect(0, 0, w, h);
    g.setStroke(
        Color.color(
            Palette.INK.getRed(), Palette.INK.getGreen(), Palette.INK.getBlue(), GRAIN_ALPHA));
    g.setLineWidth(1);
    double step = Math.max(12, h * 0.022);
    for (double y = step; y < h; y += step) {
      double py = Math.round(y) + 0.5; // crisp 1px hairline
      g.strokeLine(0, py, w, py);
    }
  }

  /** The ornamental double ink border + ochre corner flourishes. */
  private void drawFrame(double w, double h) {
    GraphicsContext g = frame.getGraphicsContext2D();
    g.clearRect(0, 0, w, h);

    double minDim = Math.min(w, h);
    double inset = frameInset(minDim);
    double outerWidth = frameOuterWidth(minDim);
    double gap = frameGap(minDim);
    double innerWidth = Math.max(1, outerWidth * 0.4); // ≈1.5px inner

    g.setStroke(Palette.INK);
    g.setLineWidth(outerWidth);
    g.strokeRect(inset, inset, w - 2 * inset, h - 2 * inset);
    g.setLineWidth(innerWidth);
    g.strokeRect(inset + gap, inset + gap, w - 2 * (inset + gap), h - 2 * (inset + gap));

    double cx = inset + gap;
    double cy = inset + gap;
    double len = flourishLen(minDim);
    drawCornerFlourish(g, cx, cy, len, 0); // top-left
    drawCornerFlourish(g, w - cx, cy, len, 90); // top-right
    drawCornerFlourish(g, w - cx, h - cy, len, 180); // bottom-right
    drawCornerFlourish(g, cx, h - cy, len, 270); // bottom-left
  }

  /**
   * One ochre flourish hugging an inner corner, with a small oxblood dot — drawn in a local frame
   * (running +x along one edge, +y along the other) and rotated into each corner. A concave ochre
   * wing fills the corner; the dot sits just inside it.
   */
  private void drawCornerFlourish(
      GraphicsContext g, double originX, double originY, double len, double rotateDeg) {
    g.save();
    g.translate(originX, originY);
    g.rotate(rotateDeg);

    g.setFill(Palette.OCHRE);
    g.beginPath();
    g.moveTo(0, 0);
    g.lineTo(len, 0);
    // Concave inner edge curving back to the other arm of the corner.
    g.quadraticCurveTo(len * 0.28, len * 0.28, 0, len);
    g.closePath();
    g.fill();

    g.setFill(Palette.OXBLOOD);
    double d = Math.max(2.2, len * 0.085);
    g.fillOval(len * 0.5 - d / 2, len * 0.5 - d / 2, d, d);

    g.restore();
  }
}

package ui.menu;

import java.util.List;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import ui.util.Motion;
import ui.util.Palette;

/**
 * One engraved casebook cover in the case-selection gallery (MENU_DESIGN #2): a vellum cover plate
 * with a bound spine, the case title (Playfair) and author (Spectral), small engraved language
 * tags, and a wax-seal "Solved" stamp when the case has been completed. Hovering (or
 * keyboard-focusing) the book <b>lifts it</b> and fades in a short invitation excerpt in the
 * typewriter face.
 *
 * <p>It extends {@link Button} so it joins the menu's keyboard focus ring, fires on Enter, and
 * shows the shared ochre focus treatment. The engraving is drawn in <b>ligne claire</b> on a {@link
 * Canvas} (flat palette fills, clean Ink contours — DESIGN.md §1/§6) bound to the cover size, while
 * the title/author/tags are real {@code Label}s so the per-language fonts (Amiri/PT&nbsp;Serif)
 * apply to Arabic and Russian titles. No shadow — the border and the lift are the affordance.
 */
public class CasebookCover extends Button {

  /** How far the book lifts on hover/focus (px), and the reveal/lift timing. */
  private static final double LIFT = 8;

  private final Canvas engraving = new Canvas();
  private final StackPane excerptBand = new StackPane();
  private final boolean solved;

  public CasebookCover(
      String title, String author, List<String> languageCodes, boolean solved, String excerpt) {
    this(title, author, languageCodes, solved, null, excerpt);
  }

  public CasebookCover(
      String title,
      String author,
      List<String> languageCodes,
      boolean solved,
      String solvedRank,
      String excerpt) {
    this.solved = solved;
    getStyleClass().add("casebook-cover");
    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    setPadding(Insets.EMPTY);
    setMinSize(0, 0);

    engraving.setMouseTransparent(true);

    // Title / author / language tags, laid out to the right of the spine. Real Labels so localized
    // faces apply; the canvas paints the cartouche they sit on.
    Label titleLabel = new Label(title);
    titleLabel.getStyleClass().add("casebook-title");
    titleLabel.setWrapText(true);
    titleLabel.setTextAlignment(TextAlignment.CENTER);
    titleLabel.setAlignment(Pos.CENTER);
    titleLabel.setMaxWidth(Double.MAX_VALUE);

    Label authorLabel = new Label(author == null || author.isBlank() ? "" : author);
    authorLabel.getStyleClass().add("casebook-author");
    authorLabel.setWrapText(true);
    authorLabel.setTextAlignment(TextAlignment.CENTER);
    authorLabel.setAlignment(Pos.CENTER);
    authorLabel.setMaxWidth(Double.MAX_VALUE);

    // A wrapping row of language tags. A FlowPane (not an HBox) so a case that ships many
    // localizations flows its tags onto a second row instead of squeezing each tag Label until its
    // text truncates to nothing — the fixed-width cover cannot hold 7+ tags on one line.
    FlowPane tags = new FlowPane(6, 4);
    tags.setAlignment(Pos.CENTER);
    tags.setMaxWidth(Double.MAX_VALUE);
    tags.setPrefWrapLength(150);
    if (languageCodes != null) {
      for (String code : languageCodes) {
        Label tag = new Label(code.toUpperCase());
        tag.getStyleClass().add("casebook-lang-tag");
        tag.setMinWidth(Region.USE_PREF_SIZE); // never shrink a tag below its own text
        tags.getChildren().add(tag);
      }
    }

    Region topSpacer = new Region();
    Region midSpacer = new Region();
    VBox.setVgrow(topSpacer, javafx.scene.layout.Priority.ALWAYS);
    VBox.setVgrow(midSpacer, javafx.scene.layout.Priority.ALWAYS);

    VBox content = new VBox(6, topSpacer, titleLabel, authorLabel, midSpacer, tags);
    content.setAlignment(Pos.CENTER);
    content.getStyleClass().add("casebook-content");

    // The best Rank Tier earned, beneath the language tags on a solved cover. A migrated solve has
    // no rank, so the seal shows but this line does not.
    if (solved && solvedRank != null && !solvedRank.isBlank()) {
      Label rankLabel = new Label(solvedRank);
      rankLabel.getStyleClass().add("casebook-rank");
      rankLabel.setWrapText(true);
      rankLabel.setTextAlignment(TextAlignment.CENTER);
      rankLabel.setAlignment(Pos.CENTER);
      rankLabel.setMaxWidth(Double.MAX_VALUE);
      content.getChildren().add(rankLabel);
    }

    // The typewriter excerpt band: a vellum panel over the lower cover, hidden until hover/focus.
    Label excerptLabel = new Label(excerpt == null ? "" : excerpt);
    excerptLabel.getStyleClass().add("casebook-excerpt");
    excerptLabel.setWrapText(true);
    excerptLabel.setTextAlignment(TextAlignment.CENTER);
    excerptBand.getChildren().add(excerptLabel);
    excerptBand.getStyleClass().add("casebook-excerpt-band");
    excerptBand.setMouseTransparent(true);
    excerptBand.setOpacity(0);
    StackPane.setAlignment(excerptBand, Pos.BOTTOM_CENTER);

    StackPane face = new StackPane(engraving, content, excerptBand);
    face.setMinSize(0, 0);
    // A Button centres its graphic at the graphic's own preferred size; bind the face to the
    // control size so the engraved cover fills the whole plate (the caller sets the plate size).
    face.prefWidthProperty().bind(widthProperty());
    face.prefHeightProperty().bind(heightProperty());
    setGraphic(face);

    engraving.widthProperty().bind(face.widthProperty());
    engraving.heightProperty().bind(face.heightProperty());
    engraving.widthProperty().addListener((obs, a, b) -> draw());
    engraving.heightProperty().addListener((obs, a, b) -> draw());

    boolean hasExcerpt = excerpt != null && !excerpt.isBlank();
    setOnMouseEntered(e -> setLifted(true, hasExcerpt));
    setOnMouseExited(e -> setLifted(false, hasExcerpt));
    focusedProperty().addListener((obs, was, now) -> setLifted(now, hasExcerpt));
  }

  /** Lifts (or settles) the book and fades the excerpt band in/out — gentle, paper-like (§6). */
  private void setLifted(boolean lifted, boolean hasExcerpt) {
    TranslateTransition lift = new TranslateTransition(Motion.ELEMENT, this);
    lift.setToY(lifted ? -LIFT : 0);
    lift.setInterpolator(Motion.EASE);
    lift.play();
    if (hasExcerpt) {
      FadeTransition fade = new FadeTransition(Duration.millis(160), excerptBand);
      fade.setToValue(lifted ? 1 : 0);
      fade.setInterpolator(Motion.EASE);
      fade.play();
    }
  }

  private void draw() {
    double w = engraving.getWidth();
    double h = engraving.getHeight();
    GraphicsContext g = engraving.getGraphicsContext2D();
    g.clearRect(0, 0, w, h);
    if (w <= 8 || h <= 8) {
      return;
    }

    double pad = Math.max(1.5, Math.min(w, h) * 0.02);
    double l = pad;
    double t = pad;
    double cw = w - pad * 2;
    double ch = h - pad * 2;
    double stroke = Math.max(2, Math.min(w, h) * 0.014);

    // Cover plate (vellum) + double ink border — the engraved casebook plate.
    g.setFill(Palette.VELLUM);
    g.fillRect(l, t, cw, ch);
    g.setStroke(Palette.INK);
    g.setLineWidth(stroke);
    g.strokeRect(l, t, cw, ch);
    double gap = stroke * 1.7;
    g.setLineWidth(Math.max(1, stroke * 0.4));
    g.strokeRect(l + gap, t + gap, cw - gap * 2, ch - gap * 2);

    // Bound spine down the left edge: a faded-vellum band with sepia binding bands.
    double spineW = cw * 0.12;
    g.setFill(Palette.FADED_VELLUM);
    g.fillRect(l, t, spineW, ch);
    g.setStroke(Palette.INK);
    g.setLineWidth(Math.max(1.5, stroke * 0.7));
    g.strokeLine(l + spineW, t, l + spineW, t + ch);
    g.setStroke(
        Color.color(
            Palette.SEPIA.getRed(), Palette.SEPIA.getGreen(), Palette.SEPIA.getBlue(), 0.7));
    g.setLineWidth(Math.max(1, stroke * 0.5));
    for (double f : new double[] {0.18, 0.30, 0.70, 0.82}) {
      double y = t + ch * f;
      g.strokeLine(l + spineW * 0.18, y, l + spineW * 0.82, y);
    }

    // An ochre rule across the cover face, a little above centre — the title sits just above it.
    double faceL = l + spineW + gap * 1.5;
    double faceR = l + cw - gap * 1.5;
    double ruleY = t + ch * 0.56;
    g.setStroke(Palette.OCHRE);
    g.setLineWidth(Math.max(1.5, stroke * 0.6));
    g.strokeLine(faceL + (faceR - faceL) * 0.18, ruleY, faceR - (faceR - faceL) * 0.18, ruleY);
    double dot = Math.max(2, stroke * 0.9);
    g.setFill(Palette.OXBLOOD);
    g.fillOval((faceL + faceR) / 2 - dot / 2, ruleY - dot / 2, dot, dot);

    // Quiet corner filigree on the two right corners of the face.
    double fl = Math.min(w, h) * 0.07;
    drawCornerFlourish(g, faceR - gap, t + gap * 1.6, fl, 90);
    drawCornerFlourish(g, faceR - gap, t + ch - gap * 1.6, fl, 180);

    if (solved) {
      drawWaxSeal(g, faceR - fl * 0.9, t + ch - fl * 0.9, fl * 0.78, stroke);
    }
  }

  /** A small ochre corner flourish with an oxblood dot, matching {@link MenuPage}'s frame motif. */
  private void drawCornerFlourish(GraphicsContext g, double ox, double oy, double len, double rot) {
    g.save();
    g.translate(ox, oy);
    g.rotate(rot);
    g.setFill(Palette.OCHRE);
    g.beginPath();
    g.moveTo(0, 0);
    g.lineTo(len, 0);
    g.quadraticCurveTo(len * 0.28, len * 0.28, 0, len);
    g.closePath();
    g.fill();
    g.restore();
  }

  /**
   * A wax-seal "Solved" stamp (MENU_DESIGN #2): an oxblood roundel with a scalloped impressed rim
   * and a small engraved magnifier monogram — a wax seal, not a flat check.
   */
  private void drawWaxSeal(GraphicsContext g, double cx, double cy, double r, double stroke) {
    // Scalloped wax blob.
    g.setFill(Palette.OXBLOOD);
    int scallops = 12;
    g.beginPath();
    for (int i = 0; i <= scallops * 2; i++) {
      double ang = Math.PI * i / scallops;
      double rad = (i % 2 == 0) ? r : r * 0.86;
      double x = cx + Math.cos(ang) * rad;
      double y = cy + Math.sin(ang) * rad;
      if (i == 0) {
        g.moveTo(x, y);
      } else {
        g.lineTo(x, y);
      }
    }
    g.closePath();
    g.fill();

    // Impressed inner ring.
    g.setStroke(Color.color(0, 0, 0, 0.28));
    g.setLineWidth(Math.max(1, stroke * 0.5));
    g.strokeOval(cx - r * 0.66, cy - r * 0.66, r * 1.32, r * 1.32);

    // Magnifier monogram, lightly embossed.
    double lensR = r * 0.30;
    double lcx = cx - r * 0.08;
    double lcy = cy - r * 0.08;
    g.setStroke(Color.color(1, 1, 1, 0.55));
    g.setLineWidth(Math.max(1.2, stroke * 0.6));
    g.strokeOval(lcx - lensR, lcy - lensR, lensR * 2, lensR * 2);
    g.strokeLine(lcx + lensR * 0.7, lcy + lensR * 0.7, lcx + lensR * 1.7, lcy + lensR * 1.7);
  }
}

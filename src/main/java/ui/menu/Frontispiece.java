package ui.menu;

import java.io.InputStream;
import javafx.animation.FadeTransition;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import ui.util.Motion;
import ui.util.Palette;

/**
 * The main menu's frontispiece (MENU_DESIGN.md #1): the produced engraving plate — the detective
 * seated at his lamplit desk in front of a clue/evidence wall — a self-framed book-plate, with a
 * small italic caption ribbon beneath.
 *
 * <p>The plate ships in two baked theme variants ({@code frontispiece_plate.png} = light "study by
 * daylight", {@code frontispiece_plate_dark.png} = candlelit dark). The one matching the active
 * theme is shown in a preserve-ratio {@link ImageView}, so it renders <b>fit</b> (fully visible,
 * centred) — the baked frame never crops or bleeds and the parchment surface shows through the
 * letterbox margins (DESIGN.md §4). {@link #onThemeChanged()} swaps the variant so the frontispiece
 * recolours with the rest of the app. If the active variant fails to load it falls back to the
 * original procedural ligne-claire drawing (DESIGN.md §1/§6).
 *
 * <p>The plate has <b>no glow baked in</b>: while the lamp is on, the {@link #glowCanvas} overlay
 * (above the image) paints a single soft circular warm glow centred on the lamp — and nothing on the
 * figure — on a layer whose opacity can flicker like a candle (DESIGN.md §6 ambient motion) without
 * ever touching the text. It is the single permitted warm gradient (§6) — themed via {@link
 * Palette#OCHRE} (a touch stronger in dark). Clicking the lamp toggles it on/off; the glow defaults
 * on each launch (it is ambient decoration, not a saved preference).
 *
 * <p>The desk magnifier is a separate {@link ImageView} node on the plate. Clicking it lifts the
 * node and activates a real {@link MagnifierLens} at the Scene root: a circular glass that magnifies
 * and barrel-warps a snapshot of the whole menu under the cursor and follows the mouse (the OS
 * cursor is hidden while active). The next click anywhere drops the magnifier back and restores the
 * cursor.
 */
public class Frontispiece extends VBox {

  // The two baked theme variants, loaded once from the classpath; either may be {@code null} (→
  // procedural fallback for that theme).
  private static final Image PLATE_LIGHT = loadPlate("/images/menu/frontispiece_plate.png");
  private static final Image PLATE_DARK = loadPlate("/images/menu/frontispiece_plate_dark.png");

  /** The desk-magnifier / lens art (64×64), loaded once; null if the asset is missing. */
  private static final Image MAG_IMAGE = loadPlate("/images/menu/cursor_magnifier.png");

  /** Diameter (px) of the magnifier glass and its magnification factor. */
  private static final double LENS_DIAMETER = 72;
  private static final double LENS_MAGNIFICATION = 2.0;

  // Normalized regions of the plate (0–1), mapped to the displayed image rect at draw time so they
  // scale with the window. The banker's lamp sits on the desk's right.
  // Clickable lamp hotspot (a hand cursor shows over it; a click toggles the glow).
  private static final double LAMP_X0 = 0.66;
  private static final double LAMP_X1 = 0.74;
  private static final double LAMP_Y0 = 0.64;
  private static final double LAMP_Y1 = 0.76;
  // Centre of the single warm circular lamplight glow over the lamp.
  private static final double GLOW_CX = 0.70;
  private static final double GLOW_CY = 0.70;
  // The desk magnifier node: normalized centre on the desk and its width as a fraction of the plate.
  private static final double MAG_CX = 0.33;
  private static final double MAG_CY = 0.71;
  private static final double MAG_W = 0.07;

  // A hidden note tucked into the photo frame: a faint, tiny inscription that reads as an illegible
  // mark to the naked eye but becomes legible under the desk magnifier (the lens magnifies a snapshot
  // that includes this glowCanvas layer). MSG_X/MSG_Y are the note's top-left as a fraction of the
  // plate — nudge them so it sits inside the frame in your plate art.
  private static final double MSG_X = 0.5; // horizontal centre of the note (fraction of the plate)
  private static final double MSG_Y = 0.255; // higher up — over the pinboard on the evidence wall
  private static final String MSG_TEXT = "Shall we finish the game?";
  private static final String MSG_SIGN = "Amr";

  // The plate art sits in an ImageView; the lamplight glow sits on its own layer ABOVE it (carrying
  // only the glow, so its opacity can flicker like a candle, DESIGN.md §6, without redrawing the
  // scene); the procedural fallback draws on the canvas BELOW it (used only when an image is
  // missing). glowCanvas is the topmost, pickable layer — it carries the lamp click + cursor. The
  // ribbon text is a separate node outside this StackPane, so the flicker can never touch contrast.
  private final ImageView plateView = new ImageView();
  // The desk magnifier — a separate node on the plate; clicking it lifts it and opens the lens.
  private final ImageView magnifierView = new ImageView();
  private final Canvas glowCanvas = new Canvas();
  private final Canvas canvas = new Canvas();
  // The hidden note layer. It is ALWAYS invisible in the live scene; resnapshot() flips it visible
  // only for the instant the lens snapshot is taken, so the note exists ONLY inside the magnifier.
  private final Canvas msgCanvas = new Canvas();
  // Red glow around the note (radius set per-draw to the text size). A deliberate exception to
  // DESIGN.md's no-glow/no-pure-red bans: this is an easter egg only ever seen through the lens.
  private final javafx.scene.effect.DropShadow msgGlow = new javafx.scene.effect.DropShadow();
  private final Label ribbon = new Label();
  private Timeline flicker;

  // The lamp is lit by default each launch (not persisted — ambient decoration, not a preference).
  private boolean lampOn = true;
  private Rectangle2D lampHotspot;
  private Rectangle2D magnifierHotspot;

  // The working magnifier lens (at the Scene root) while active: a snapshot of the menu is magnified
  // + warped under the cursor and follows the mouse; the next click anywhere deactivates.
  private boolean lensActive;
  private MagnifierLens lens;
  private Pane lensHost;
  private EventHandler<MouseEvent> lensMoveHandler;
  private EventHandler<MouseEvent> lensPressHandler;
  private ChangeListener<Number> lensResizeListener;
  // Set when a deactivating press lands on the magnifier hotspot, so the release-click that follows
  // does not immediately reactivate the lens.
  private boolean ignoreNextMagnifierClick;

  public Frontispiece(String caption) {
    super(10);
    setAlignment(Pos.CENTER);
    getStyleClass().add("menu-frontispiece");
    setMinSize(0, 0);

    // Bottom→top: procedural fallback canvas, the plate image, the desk magnifier, the click overlay,
    // and the hidden-note layer (invisible in the live scene; shown only for the lens snapshot).
    StackPane plate = new StackPane(canvas, plateView, magnifierView, glowCanvas, msgCanvas);
    plate.setMinSize(0, 0);
    VBox.setVgrow(plate, Priority.ALWAYS);

    // The plate image scales to FIT (preserve aspect, fully visible); StackPane CENTERs it, so the
    // baked frame never crops or bleeds and the surface shows through the letterbox margins.
    plateView.setPreserveRatio(true);
    plateView.setSmooth(true);
    plateView.setMouseTransparent(true);
    plateView.fitWidthProperty().bind(plate.widthProperty());
    plateView.fitHeightProperty().bind(plate.heightProperty());

    // The desk magnifier: a visual node positioned/sized to the plate in draw(); the click + hand
    // cursor come from the glowCanvas hotspot above it, so it stays mouse-transparent + unmanaged.
    magnifierView.setImage(MAG_IMAGE);
    magnifierView.setPreserveRatio(true);
    magnifierView.setSmooth(true);
    magnifierView.setMouseTransparent(true);
    magnifierView.setManaged(false);
    magnifierView.setVisible(MAG_IMAGE != null);

    canvas.setMouseTransparent(true);
    canvas.widthProperty().bind(plate.widthProperty());
    canvas.heightProperty().bind(plate.heightProperty());
    glowCanvas.widthProperty().bind(plate.widthProperty());
    glowCanvas.heightProperty().bind(plate.heightProperty());
    glowCanvas.widthProperty().addListener((obs, a, b) -> draw());
    glowCanvas.heightProperty().addListener((obs, a, b) -> draw());

    // The hidden-note layer shares the plate's coordinate space, never takes the mouse, and stays
    // invisible except during the lens snapshot (toggled in resnapshot()). The red glow is a
    // DropShadow on the node; the text itself is painted red in drawHiddenMessage().
    msgCanvas.setMouseTransparent(true);
    msgCanvas.setVisible(false);
    msgCanvas.widthProperty().bind(plate.widthProperty());
    msgCanvas.heightProperty().bind(plate.heightProperty());
    msgGlow.setColor(javafx.scene.paint.Color.web("#FF3B30"));
    msgGlow.setSpread(0.5);
    msgCanvas.setEffect(msgGlow);

    // The glow overlay is the topmost, pickable layer: it carries the lamp + magnifier clicks and a
    // hand cursor over either hotspot. While the lens is active, leave the node cursor unset so the
    // Scene's hidden-cursor (Cursor.NONE) shows through.
    glowCanvas.setOnMouseMoved(
        e -> {
          if (lensActive) {
            glowCanvas.setCursor(null);
            return;
          }
          glowCanvas.setCursor(
              overHotspot(lampHotspot, e) || overHotspot(magnifierHotspot, e)
                  ? Cursor.HAND
                  : Cursor.DEFAULT);
        });
    glowCanvas.setOnMouseClicked(
        e -> {
          if (overHotspot(lampHotspot, e)) {
            lampOn = !lampOn;
            draw(); // repaint the glow on/off
            updateFlicker();
          } else if (overHotspot(magnifierHotspot, e)) {
            if (ignoreNextMagnifierClick) {
              ignoreNextMagnifierClick = false; // this click just deactivated the lens
            } else if (!lensActive) {
              activateLens(e);
            }
          }
        });

    ribbon.setText(caption);
    ribbon.getStyleClass().add("menu-caption-ribbon");
    // The ribbon hugs its text (compact, content-sized) and is centred; long epigraphs (German + the
    // longer translations are the stress case) wrap within a width cap and grow taller rather than
    // stretch edge-to-edge or truncate. The text itself is centred too. A non-stretching HBox keeps
    // the compact ribbon centred under the frontispiece (the VBox would otherwise fill its width).
    ribbon.setWrapText(true);
    ribbon.setTextAlignment(TextAlignment.CENTER);
    ribbon.setAlignment(Pos.CENTER);
    // HBox lays the label at min(its content pref, this cap): short quotes hug; long quotes wrap.
    ribbon.maxWidthProperty().bind(widthProperty().multiply(0.92));
    HBox ribbonRow = new HBox(ribbon);
    ribbonRow.setAlignment(Pos.CENTER);

    getChildren().addAll(plate, ribbonRow);

    // Show the variant matching the current theme up front (procedural fallback if it's missing).
    applyVariant();

    // Run the candle flicker only while the frontispiece is on screen AND the lamp is lit — start it
    // when those hold, stop it otherwise, so no timeline ever runs behind a hidden or unlit menu.
    sceneProperty().addListener((obs, was, now) -> updateFlicker());
  }

  /** Loads an engraving-plate variant from the classpath, or {@code null} if missing/unreadable. */
  private static Image loadPlate(String resourcePath) {
    try (InputStream in = Frontispiece.class.getResourceAsStream(resourcePath)) {
      if (in == null) {
        return null;
      }
      Image image = new Image(in);
      return image.isError() ? null : image;
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Cross-fades the caption ribbon to a new epigraph (MENU_DESIGN rotating epigraph): a gentle ink-
   * style fade-out, swap, fade-in over {@code fadeMs} total — no pop (DESIGN.md §6). A blank/no-op
   * text leaves the current caption untouched.
   */
  public void crossfadeCaption(String text, int fadeMs) {
    if (text == null || text.isBlank() || text.equals(ribbon.getText())) {
      return;
    }
    Duration half = Duration.millis(Math.max(1, fadeMs) / 2.0);
    FadeTransition out = new FadeTransition(half, ribbon);
    out.setFromValue(1.0);
    out.setToValue(0.0);
    out.setInterpolator(Motion.EASE);
    out.setOnFinished(
        event -> {
          ribbon.setText(text);
          FadeTransition in = new FadeTransition(half, ribbon);
          in.setFromValue(0.0);
          in.setToValue(1.0);
          in.setInterpolator(Motion.EASE);
          in.play();
        });
    out.play();
  }

  /** The plate variant matching the active theme, or {@code null} → procedural fallback. */
  private static Image activeVariant() {
    return isDark() ? PLATE_DARK : PLATE_LIGHT;
  }

  /** Shows the theme-matching variant in the ImageView (or the procedural canvas if it's missing). */
  private void applyVariant() {
    Image img = activeVariant();
    plateView.setImage(img);
    plateView.setVisible(img != null);
    canvas.setVisible(img == null);
  }

  /** Swap to the variant for the new theme and redraw the glow in the active palette (DESIGN.md §8). */
  public void onThemeChanged() {
    applyVariant();
    draw();
    if (lensActive) {
      resnapshot(); // the magnified menu recolours with the theme
    }
  }

  private void draw() {
    double w = glowCanvas.getWidth();
    double h = glowCanvas.getHeight();
    GraphicsContext g = canvas.getGraphicsContext2D();
    GraphicsContext glowGc = glowCanvas.getGraphicsContext2D();
    g.clearRect(0, 0, w, h);
    glowGc.clearRect(0, 0, w, h);
    if (w <= 8 || h <= 8) {
      return;
    }
    Image img = plateView.getImage();
    if (img != null) {
      // Image mode: the ImageView draws the plate (fit + centred); we only map the hotspots into the
      // displayed-image rect — the same min-scale + centre the ImageView uses — and, when the lamp
      // is on, paint the glow over it.
      double scale = Math.min(w / img.getWidth(), h / img.getHeight());
      double dw = img.getWidth() * scale;
      double dh = img.getHeight() * scale;
      double dx = (w - dw) / 2;
      double dy = (h - dh) / 2;
      lampHotspot = mapRegion(dx, dy, dw, dh, LAMP_X0, LAMP_Y0, LAMP_X1, LAMP_Y1);
      layoutMagnifier(dx, dy, dw, dh);
      if (lampOn) {
        drawLampGlow(glowGc, dx, dy, dw, dh);
      }
      drawHiddenMessage(dx, dy, dw, dh);
    } else {
      magnifierHotspot = null; // the procedural fallback has no magnifier hotspot
      magnifierView.setVisible(false);
      drawProcedural(g, glowGc, w, h);
    }
  }

  /** A normalized (0–1) sub-rect of the plate mapped into the displayed-image rect. */
  private static Rectangle2D mapRegion(
      double dx, double dy, double dw, double dh, double x0, double y0, double x1, double y1) {
    return new Rectangle2D(dx + x0 * dw, dy + y0 * dh, (x1 - x0) * dw, (y1 - y0) * dh);
  }

  /** True when pointer event {@code e} falls inside {@code hotspot} (null-safe). */
  private static boolean overHotspot(Rectangle2D hotspot, MouseEvent e) {
    return hotspot != null && hotspot.contains(e.getX(), e.getY());
  }

  /**
   * Positions/sizes the desk-magnifier node (a square ~{@link #MAG_W} of the plate, centred at
   * {@link #MAG_CX}/{@link #MAG_CY}) on the displayed-image rect and sets the matching hotspot. The
   * node stays hidden while the lens is active (it has been "lifted") or when the asset is missing.
   */
  private void layoutMagnifier(double dx, double dy, double dw, double dh) {
    if (MAG_IMAGE == null) {
      magnifierHotspot = null;
      magnifierView.setVisible(false);
      return;
    }
    double side = MAG_W * dw;
    double x = dx + MAG_CX * dw - side / 2;
    double y = dy + MAG_CY * dh - side / 2;
    magnifierHotspot = new Rectangle2D(x, y, side, side);
    magnifierView.setFitWidth(side);
    magnifierView.setLayoutX(x);
    magnifierView.setLayoutY(y);
    magnifierView.setVisible(!lensActive);
  }

  /**
   * Paints the hidden note onto {@link #msgCanvas} (which is invisible in the live scene and only
   * shown during the lens snapshot in {@link #resnapshot()}): a two-line inscription — a quote and an
   * italic signature offset down-right like a hand — in glowing red at a normal, readable size. The
   * naked eye never sees it; the desk magnifier reveals it. The red halo is the node's {@link
   * #msgGlow} DropShadow.
   */
  private void drawHiddenMessage(double dx, double dy, double dw, double dh) {
    GraphicsContext gc = msgCanvas.getGraphicsContext2D();
    gc.clearRect(0, 0, msgCanvas.getWidth(), msgCanvas.getHeight());

    double size = Math.max(6.0, dw * 0.018); // smaller (tune to taste)
    double cx = dx + MSG_X * dw; // horizontal centre
    double y = dy + MSG_Y * dh;
    msgGlow.setRadius(size * 0.9); // scale the red halo with the text

    gc.save();
    gc.setFill(javafx.scene.paint.Color.web("#FF3B30")); // bright red
    gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
    gc.setTextBaseline(javafx.geometry.VPos.TOP);
    gc.setFont(javafx.scene.text.Font.font("Special Elite", size));
    gc.fillText(MSG_TEXT, cx, y); // centred on the middle of the plate
    // The signature sits a little lower and to the right, italic, like a written hand.
    gc.setFont(
        javafx.scene.text.Font.font(
            "Special Elite", javafx.scene.text.FontPosture.ITALIC, size * 0.95));
    gc.fillText("— " + MSG_SIGN, cx + dw * 0.06, y + size * 1.7);
    gc.restore();
  }

  /**
   * The §6 lamplight motif, painted only while the lamp is on: a single soft <b>circular</b> warm
   * glow centred on the lamp — {@link Palette#OCHRE} fading to transparent, a touch stronger in dark
   * ("the study by candlelight"). Nothing is drawn over the figure. On the glow overlay only, so it
   * never touches text contrast.
   */
  private void drawLampGlow(GraphicsContext glowGc, double dx, double dy, double dw, double dh) {
    boolean dark = isDark();
    double cx = dx + GLOW_CX * dw;
    double cy = dy + GLOW_CY * dh;
    double r = dw * (dark ? 0.165 : 0.135); // a modest fraction of the plate
    // Light mode rides on bright parchment, so the ochre needs more opacity to read; dark already
    // pops against the candlelit ground.
    fillGlow(glowGc, cx, cy, r, r, dark ? 0.55 : 0.52);
  }

  /**
   * A soft warm ochre blob (centre alpha → transparent edge) — radii {@code rx,ry} drawn via a
   * transform around a unit radial gradient (here always circular, {@code rx == ry}).
   */
  private void fillGlow(
      GraphicsContext glowGc, double cx, double cy, double rx, double ry, double alpha) {
    Color c = Palette.OCHRE;
    RadialGradient gradient =
        new RadialGradient(
            0,
            0,
            0,
            0,
            1,
            false,
            CycleMethod.NO_CYCLE,
            new Stop(0, Color.color(c.getRed(), c.getGreen(), c.getBlue(), alpha)),
            new Stop(1, Color.color(c.getRed(), c.getGreen(), c.getBlue(), 0.0)));
    glowGc.save();
    glowGc.translate(cx, cy);
    glowGc.scale(rx, ry);
    glowGc.setFill(gradient);
    glowGc.fillOval(-1, -1, 2, 2);
    glowGc.restore();
  }

  /** True while the active palette is the dark "study by candlelight" theme. */
  private static boolean isDark() {
    return Palette.PARCHMENT.getBrightness() < 0.5;
  }

  /**
   * Runs the candle flicker only while the lamp is lit and the frontispiece is on screen; otherwise
   * stops it and restores full opacity (the glow itself is painted/cleared by {@link #draw()}, so
   * when the lamp is off the overlay carries nothing to flicker).
   */
  private void updateFlicker() {
    boolean shouldFlicker = lampOn && getScene() != null;
    if (shouldFlicker && flicker == null) {
      flicker = Motion.candleFlicker(glowCanvas);
    } else if (!shouldFlicker && flicker != null) {
      flicker.stop();
      flicker = null;
      glowCanvas.setOpacity(1.0);
    }
  }

  /**
   * Lifts the desk magnifier and activates the lens at the Scene root: snapshot the whole menu once,
   * add a {@link MagnifierLens} overlay that follows the mouse, and hide the OS cursor. A scene-level
   * one-shot press handler deactivates on the next click anywhere; the menu is re-snapshotted on
   * resize. {@code origin} is the activating click, used as the lens's initial position.
   */
  private void activateLens(MouseEvent origin) {
    Scene scene = getScene();
    if (scene == null || lensActive || MAG_IMAGE == null || !(scene.getRoot() instanceof Pane)) {
      return;
    }
    lensActive = true;
    lensHost = (Pane) scene.getRoot();
    magnifierView.setVisible(false); // lift the desk magnifier

    lens = new MagnifierLens(LENS_DIAMETER, LENS_MAGNIFICATION);
    lensHost.getChildren().add(lens.node());

    scene.setCursor(Cursor.NONE); // hide the OS cursor while the lens is the pointer
    resnapshot();
    lens.showAt(origin.getSceneX(), origin.getSceneY());

    lensMoveHandler = e -> lens.showAt(e.getSceneX(), e.getSceneY());
    scene.addEventFilter(MouseEvent.MOUSE_MOVED, lensMoveHandler);

    // Next press anywhere deactivates (without consuming, so that click behaves normally). If it
    // lands on the magnifier spot, flag the following release-click so it doesn't reactivate.
    lensPressHandler =
        e -> {
          Point2D local = glowCanvas.sceneToLocal(e.getSceneX(), e.getSceneY());
          if (magnifierHotspot != null && local != null && magnifierHotspot.contains(local)) {
            ignoreNextMagnifierClick = true;
          }
          deactivateLens();
        };
    scene.addEventFilter(MouseEvent.MOUSE_PRESSED, lensPressHandler);

    lensResizeListener = (obs, a, b) -> resnapshot();
    scene.widthProperty().addListener(lensResizeListener);
    scene.heightProperty().addListener(lensResizeListener);
  }

  /** Removes the lens overlay, drops the desk magnifier back into place and restores the cursor. */
  private void deactivateLens() {
    if (!lensActive) {
      return;
    }
    lensActive = false;
    Scene scene = getScene();
    if (scene != null) {
      scene.removeEventFilter(MouseEvent.MOUSE_MOVED, lensMoveHandler);
      scene.removeEventFilter(MouseEvent.MOUSE_PRESSED, lensPressHandler);
      scene.widthProperty().removeListener(lensResizeListener);
      scene.heightProperty().removeListener(lensResizeListener);
      scene.setCursor(Cursor.DEFAULT);
    }
    if (lensHost != null && lens != null) {
      lensHost.getChildren().remove(lens.node());
    }
    lens = null;
    lensHost = null;
    lensMoveHandler = null;
    lensPressHandler = null;
    lensResizeListener = null;
    magnifierView.setVisible(MAG_IMAGE != null); // drop the desk magnifier back
  }

  /**
   * Snapshots the whole menu (the Scene root, with the lens overlay hidden so it is excluded) and
   * hands it to the lens. Cheap-enough: done once per activation and on resize/theme change.
   */
  private void resnapshot() {
    Scene scene = getScene();
    if (scene == null || lens == null) {
      return;
    }
    Parent root = scene.getRoot();
    boolean wasVisible = lens.node().isVisible();
    lens.node().setVisible(false); // exclude the lens itself from its own snapshot
    msgCanvas.setVisible(true); // reveal the hidden note ONLY into the snapshot the lens magnifies
    WritableImage snap = root.snapshot(new SnapshotParameters(), null);
    msgCanvas.setVisible(false); // hide it again — it is never rendered in the live scene
    lens.node().setVisible(wasVisible);
    lens.setSnapshot(snap);
  }

  // --- Procedural fallback (used only when the plate asset is missing) -----------------------

  private void drawProcedural(GraphicsContext g, GraphicsContext glowGc, double w, double h) {
    // Arched plate, centred, leaving a small margin so the border isn't clipped.
    double margin = Math.min(w, h) * 0.03;
    double l = margin;
    double t = margin;
    double r = w - margin;
    double b = h - margin;
    double pw = r - l;
    double archH = pw * 0.34;

    // Plate fill (the "wall" inside the arch) then the double border: thick Ink outer, thin ochre
    // inner — the engraved-plate look of the reference.
    g.setFill(Palette.FADED_VELLUM);
    archPath(g, l, t, r, b, archH);
    g.fill();

    double stroke = Math.max(2, Math.min(w, h) * 0.012);
    g.setStroke(Palette.INK);
    g.setLineWidth(stroke);
    archPath(g, l, t, r, b, archH);
    g.stroke();

    double gap = stroke * 1.8;
    g.setStroke(Palette.OCHRE);
    g.setLineWidth(Math.max(1, stroke * 0.45));
    archPath(g, l + gap, t + gap, r - gap, b - gap, archH - gap);
    g.stroke();

    // Interior content box (below the arch curve, inside the inner border).
    double ix = l + gap * 2.2;
    double iw = (r - gap * 2.2) - ix;
    double iy = t + archH * 0.7;
    double ib = b - gap * 2.2;
    double ih = ib - iy;

    double line = Math.max(1.5, stroke * 0.5);
    drawWindow(g, ix + iw * 0.04, iy + ih * 0.04, iw * 0.42, ih * 0.42, line);
    drawBookshelf(g, ix + iw * 0.58, iy + ih * 0.02, iw * 0.38, ih * 0.46, line);
    drawDeskAndDetective(g, glowGc, ix, iy, iw, ih, ib, line);
  }

  /**
   * A plate outline: square at the bottom, arching over the top. Straight sides up to the spring
   * line at {@code top + archH}, then two quadratic curves meeting at the apex.
   */
  private void archPath(GraphicsContext g, double l, double t, double r, double b, double archH) {
    double springY = t + archH;
    double cx = (l + r) / 2;
    g.beginPath();
    g.moveTo(l, b);
    g.lineTo(l, springY);
    g.quadraticCurveTo(l, t, cx, t);
    g.quadraticCurveTo(r, t, r, springY);
    g.lineTo(r, b);
    g.closePath();
  }

  private void drawWindow(GraphicsContext g, double x, double y, double w, double h, double line) {
    g.setFill(Palette.VELLUM);
    g.fillRect(x, y, w, h);
    g.setStroke(Palette.INK);
    g.setLineWidth(line);
    g.strokeRect(x, y, w, h);
    // Mullions: 2×2 panes.
    g.strokeLine(x + w / 2, y, x + w / 2, y + h);
    g.strokeLine(x, y + h / 2, x + w, y + h / 2);

    // Moon in the upper-left pane.
    double moonR = Math.min(w, h) * 0.16;
    g.setFill(Palette.PARCHMENT);
    g.fillOval(x + w * 0.22, y + h * 0.18, moonR, moonR);
    g.setLineWidth(Math.max(1, line * 0.7));
    g.strokeOval(x + w * 0.22, y + h * 0.18, moonR, moonR);

    // Diagonal "glass" hatching across the lower-left pane only (quiet engraving texture).
    g.setStroke(
        Color.color(
            Palette.SEPIA.getRed(), Palette.SEPIA.getGreen(), Palette.SEPIA.getBlue(), 0.5));
    g.setLineWidth(1);
    double px = x;
    double py = y + h / 2;
    double pw = w / 2;
    double ph = h / 2;
    for (double d = pw * 0.2; d < pw + ph; d += pw * 0.22) {
      double x1 = px + Math.max(0, d - ph);
      double y1 = py + Math.min(d, ph);
      double x2 = px + Math.min(d, pw);
      double y2 = py + Math.max(0, d - pw);
      g.strokeLine(x1, y1, x2, y2);
    }
  }

  private void drawBookshelf(
      GraphicsContext g, double x, double y, double w, double h, double line) {
    g.setFill(Palette.VELLUM);
    g.fillRect(x, y, w, h);
    g.setStroke(Palette.INK);
    g.setLineWidth(line);
    g.strokeRect(x, y, w, h);

    int shelves = 3;
    double shelfH = h / shelves;
    for (int s = 0; s < shelves; s++) {
      double sy = y + s * shelfH;
      if (s > 0) {
        g.strokeLine(x, sy, x + w, sy);
      }
      // A row of books of varying width/height standing on the shelf.
      double bookBottom = sy + shelfH - line;
      double bx = x + w * 0.06;
      double maxBookH = shelfH * 0.78;
      double[] widths = {0.12, 0.09, 0.14, 0.08, 0.11, 0.1};
      double[] heights = {0.95, 0.8, 1.0, 0.7, 0.9, 0.85};
      for (int i = 0; i < widths.length && bx < x + w * 0.9; i++) {
        double bw = w * widths[i];
        double bh = maxBookH * heights[(i + s) % heights.length];
        g.setStroke(Palette.INK);
        g.setLineWidth(Math.max(1, line * 0.7));
        g.strokeRect(bx, bookBottom - bh, bw, bh);
        bx += bw + w * 0.012;
      }
    }
  }

  private void drawDeskAndDetective(
      GraphicsContext g,
      GraphicsContext glowGc,
      double ix,
      double iy,
      double iw,
      double ih,
      double ib,
      double line) {
    double deskTopY = iy + ih * 0.74;
    double deskH = ib - deskTopY;
    double cx = ix + iw / 2;

    // Detective silhouette behind the desk (drawn before the desk so the desk overlaps the body).
    double bodyW = iw * 0.30;
    double bodyTop = deskTopY - ih * 0.30;
    Color coat = Palette.SEPIA.darker();
    g.setFill(coat);
    g.beginPath();
    g.moveTo(cx - bodyW / 2, deskTopY);
    g.lineTo(cx - bodyW * 0.32, bodyTop);
    g.lineTo(cx + bodyW * 0.32, bodyTop);
    g.lineTo(cx + bodyW / 2, deskTopY);
    g.closePath();
    g.fill();
    g.setStroke(Palette.INK);
    g.setLineWidth(line);
    g.stroke();

    // Head + hat.
    double headR = bodyW * 0.30;
    double headCy = bodyTop - headR * 0.7;
    g.setFill(Palette.PARCHMENT);
    g.fillOval(cx - headR, headCy - headR, headR * 2, headR * 2);
    g.strokeOval(cx - headR, headCy - headR, headR * 2, headR * 2);
    // Hat: brim + crown.
    g.setFill(Palette.INK);
    double brimW = headR * 3.0;
    double brimY = headCy - headR * 0.9;
    g.fillRect(cx - brimW / 2, brimY, brimW, line * 1.6);
    double crownW = headR * 1.9;
    g.fillRect(cx - crownW / 2, brimY - headR * 1.0, crownW, headR * 1.0);

    // Desk top + front.
    g.setFill(Palette.SEPIA.darker().darker());
    g.fillRect(ix + iw * 0.02, deskTopY, iw * 0.96, deskH);
    g.setStroke(Palette.INK);
    g.setLineWidth(line);
    g.strokeRect(ix + iw * 0.02, deskTopY, iw * 0.96, deskH);
    g.strokeLine(ix + iw * 0.02, deskTopY + deskH * 0.28, ix + iw * 0.98, deskTopY + deskH * 0.28);

    drawLamp(g, glowGc, ix + iw * 0.16, deskTopY, ih, line);
    drawPapersAndMagnifier(g, cx + iw * 0.16, deskTopY, iw, ih, line);
  }

  private void drawLamp(
      GraphicsContext g, GraphicsContext glowGc, double baseX, double deskTopY, double ih,
      double line) {
    double shadeW = ih * 0.16;
    double shadeH = ih * 0.085;
    double stemH = ih * 0.13;
    double shadeBottomY = deskTopY - stemH;

    // Soft warm glow pool on the desk — the §6 lamplight motif (MENU_DESIGN: "a soft glow pool"),
    // painted on the dedicated glow layer only while the lamp is on, so its opacity can flicker like
    // a candle independently of the engraving (see updateFlicker wiring in the constructor).
    double glowR = shadeW * 1.6;
    double glowCy = deskTopY + ih * 0.02;
    if (lampOn) {
      fillGlow(glowGc, baseX, glowCy, glowR, glowR, 0.45);
    }

    // The clickable lamp region for the fallback drawing (shade + stem).
    double hotTop = shadeBottomY - shadeH;
    lampHotspot = new Rectangle2D(baseX - shadeW * 0.8, hotTop, shadeW * 1.6, deskTopY - hotTop);

    g.setStroke(Palette.INK);
    g.setLineWidth(line);
    // Stem + base.
    g.strokeLine(baseX, deskTopY, baseX, shadeBottomY);
    g.strokeLine(baseX - shadeW * 0.18, deskTopY, baseX + shadeW * 0.18, deskTopY);
    // Shade (trapezoid), ochre fill.
    g.setFill(Palette.OCHRE);
    g.beginPath();
    g.moveTo(baseX - shadeW / 2, shadeBottomY);
    g.lineTo(baseX - shadeW * 0.28, shadeBottomY - shadeH);
    g.lineTo(baseX + shadeW * 0.28, shadeBottomY - shadeH);
    g.lineTo(baseX + shadeW / 2, shadeBottomY);
    g.closePath();
    g.fill();
    g.stroke();
  }

  private void drawPapersAndMagnifier(
      GraphicsContext g, double x, double deskTopY, double iw, double ih, double line) {
    // A sheet of paper lying on the desk.
    double pw = iw * 0.16;
    double ph = ih * 0.05;
    double py = deskTopY - ph - ih * 0.005;
    g.setFill(Palette.VELLUM);
    g.fillRect(x, py, pw, ph);
    g.setStroke(Palette.INK);
    g.setLineWidth(Math.max(1, line * 0.7));
    g.strokeRect(x, py, pw, ph);

    // Magnifier: lens + handle.
    double lensR = ih * 0.045;
    double lcx = x + pw + iw * 0.06;
    double lcy = py + ph * 0.2;
    g.setFill(Palette.VELLUM);
    g.fillOval(lcx - lensR, lcy - lensR, lensR * 2, lensR * 2);
    g.setStroke(Palette.INK);
    g.setLineWidth(line);
    g.strokeOval(lcx - lensR, lcy - lensR, lensR * 2, lensR * 2);
    g.strokeLine(lcx + lensR * 0.7, lcy + lensR * 0.7, lcx + lensR * 1.7, lcy + lensR * 1.7);
  }
}

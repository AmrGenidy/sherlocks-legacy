package ui.util;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

/**
 * Generates Victorian-<b>engraving</b> placeholder plates for rooms, suspects, and objects when the
 * real engraved art is missing (GUI G3 art direction).
 *
 * <p>The locked art direction is a hand-engraved book-plate: a confident Ink contour plus
 * <em>cross-hatch tone</em> — never a flat fill, never a gradient (DESIGN.md §1 hard-bans). This
 * evolves the old flat ligne-claire placeholder (box / silhouette / diamond) into line art so a
 * missing asset still reads as part of the leather-bound detective world (DESIGN.md §1/§6).
 *
 * <p><b>Theme-aware (DESIGN.md §8):</b> all ink and ground tones are read live from {@link
 * Palette}, so a plate generated in dark mode is <em>inverted line art</em> — light hatch ({@code
 * INK} is the lamp-lit ochre in dark) on a night ground — rather than a daylight patch. Callers
 * must not cache placeholders across a theme switch (see {@code ImageManager}).
 *
 * <p><b>Suspect reads larger than object:</b> the suspect plate draws a near-full-height figure
 * while the object plate draws a small centred motif, so a suspect reads as a person and an object
 * as a small thing even before the layout's base-size rule ({@link RoomViewLayout}) is applied.
 */
public class PlaceholderImageGenerator {

  private PlaceholderImageGenerator() {}

  /**
   * Room plate: an opaque engraving inside a thick Ink book-plate frame (DESIGN.md §5 "Room view
   * frame"). A horizon rule splits a hatched floor from a lighter wall with a suggested doorway —
   * enough to read as "an engraved room" without claiming any specific scene.
   */
  public static Image createRoomPlaceholder(String roomName, int width, int height) {
    WritableImage image = new WritableImage(width, height);
    PixelWriter writer = image.getPixelWriter();

    // Night/paper ground (theme-aware) — the plate is opaque; it sits behind the sprites.
    fill(writer, width, height, Palette.FADED_VELLUM);

    int frame = Math.max(4, Math.min(width, height) / 60);
    int mat = Math.max(2, frame / 2);
    // Book-plate: thick Ink frame, a faint parchment mat, then a thin inner Ink rule.
    strokeFrame(writer, width, height, 0, 0, width, height, frame, Palette.INK);
    strokeFrame(
        writer,
        width,
        height,
        frame,
        frame,
        width - 2 * frame,
        height - 2 * frame,
        mat,
        Palette.PARCHMENT);
    int inset = frame + mat;
    int innerThin = Math.max(1, mat / 2);
    strokeFrame(
        writer,
        width,
        height,
        inset,
        inset,
        width - 2 * inset,
        height - 2 * inset,
        innerThin,
        Palette.INK);

    int x0 = inset + innerThin + 2;
    int y0 = inset + innerThin + 2;
    int x1 = width - x0;
    int y1 = height - y0;
    int horizon = y0 + (int) ((y1 - y0) * 0.55);

    // Floor: cross-hatch tone (denser) below the horizon.
    crossHatch(writer, width, height, x0, horizon, x1, y1, 7, Palette.INK);
    // Wall: a single light diagonal hatch above the horizon — quieter tone.
    diagonalHatch(writer, width, height, x0, y0, x1, horizon, 14, true, Palette.SEPIA);
    // Horizon rule.
    hLine(writer, width, height, x0, x1, horizon, 2, Palette.INK);

    // A suggested doorway centred on the wall, outlined with vertical hatch inside.
    int doorW = (x1 - x0) / 5;
    int doorH = (horizon - y0) * 2 / 3;
    int doorX = (x0 + x1) / 2 - doorW / 2;
    int doorY = horizon - doorH;
    strokeFrame(writer, width, height, doorX, doorY, doorW, doorH, 2, Palette.INK);
    verticalHatch(
        writer, width, height, doorX + 2, doorY + 2, doorX + doorW - 2, horizon, 8, Palette.SEPIA);
    return image;
  }

  /**
   * Suspect plate: a near-full-height engraved figure (head + coat) in Ink contour with hatched
   * coat tone, on a transparent ground so it composites over the room. Drawn tall on purpose so it
   * reads clearly larger than an object motif.
   */
  public static Image createSuspectPlaceholder(String suspectName, int size) {
    // Portrait canvas (not square): the room view sizes a sprite's WIDTH as height × image-aspect,
    // so a square plate rendered next to tall cut-out suspect art comes out 2–3× too wide and covers
    // the scene. A ~0.5 aspect that tightly frames the figure makes the placeholder read as a normal
    // narrow person, the same footprint as authored art (.scratch/gui-placeholder-size).
    int h = size;
    int w = Math.max(1, Math.round(size * 0.5f));
    WritableImage image = new WritableImage(w, h);
    PixelWriter writer = image.getPixelWriter();
    fill(writer, w, h, Color.TRANSPARENT);

    int cx = w / 2;
    int headR = Math.round(w * 0.22f);
    int headCy = Math.round(h * 0.15f);
    strokeCircle(writer, w, h, cx, headCy, headR, 2, Palette.INK);
    // Light hatch inside the head for face tone.
    diagonalHatch(
        writer,
        w,
        h,
        cx - headR,
        headCy - headR,
        cx + headR,
        headCy + headR,
        9,
        false,
        Palette.SEPIA,
        cx,
        headCy,
        headR);

    int shoulderY = headCy + headR + Math.round(h * 0.02f);
    int baseY = Math.round(h * 0.97f);
    int topHalf = Math.round(w * 0.30f); // half-width at shoulders
    int baseHalf = Math.round(w * 0.46f); // half-width at the hem (nearly the full plate width)

    // Coat contour: two sides + hem + shoulder line, plus a centre lapel line.
    line(writer, w, h, cx - topHalf, shoulderY, cx - baseHalf, baseY, 2, Palette.INK);
    line(writer, w, h, cx + topHalf, shoulderY, cx + baseHalf, baseY, 2, Palette.INK);
    hLine(writer, w, h, cx - topHalf, cx + topHalf, shoulderY, 2, Palette.INK);
    hLine(writer, w, h, cx - baseHalf, cx + baseHalf, baseY, 2, Palette.INK);
    line(writer, w, h, cx, shoulderY, cx, baseY, 2, Palette.INK);

    // Coat tone: diagonal hatch bounded by the (linearly widening) trapezoid each row.
    for (int y = shoulderY + 1; y < baseY; y++) {
      double t = (double) (y - shoulderY) / (baseY - shoulderY);
      int half = (int) Math.round(topHalf + t * (baseHalf - topHalf));
      hatchRow(writer, w, h, cx - half + 1, cx + half - 1, y, 7, Palette.SEPIA);
    }
    return image;
  }

  /**
   * Object plate: a small, centred engraved motif (a magnifying glass) in Ink contour with hatched
   * lens tone, on a transparent ground. Deliberately occupies far less of the plate than the
   * suspect figure.
   */
  public static Image createObjectPlaceholder(String objectName, int size) {
    WritableImage image = new WritableImage(size, size);
    PixelWriter writer = image.getPixelWriter();
    fill(writer, size, size, Color.TRANSPARENT);

    int lensCx = Math.round(size * 0.42f);
    int lensCy = Math.round(size * 0.42f);
    int lensR = Math.round(size * 0.16f);
    // Lens ring (double contour for a brass-rim read).
    strokeCircle(writer, size, size, lensCx, lensCy, lensR, 2, Palette.INK);
    strokeCircle(writer, size, size, lensCx, lensCy, lensR - 3, 1, Palette.SEPIA);
    // Glass tone: light cross-hatch inside the lens.
    crossHatch(
        writer,
        size,
        size,
        lensCx - lensR,
        lensCy - lensR,
        lensCx + lensR,
        lensCy + lensR,
        9,
        Palette.SEPIA,
        lensCx,
        lensCy,
        lensR - 4);

    // Handle: a thick diagonal from the lower-right of the rim down to ~0.78,0.78.
    int hx0 = lensCx + (int) (lensR * 0.7);
    int hy0 = lensCy + (int) (lensR * 0.7);
    int hx1 = Math.round(size * 0.78f);
    int hy1 = Math.round(size * 0.78f);
    line(writer, size, size, hx0, hy0, hx1, hy1, 5, Palette.INK);
    return image;
  }

  // --- primitives ----------------------------------------------------------

  private static void fill(PixelWriter writer, int width, int height, Color color) {
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        writer.setColor(x, y, color);
      }
    }
  }

  private static void put(PixelWriter writer, int w, int h, int x, int y, Color color) {
    if (x >= 0 && y >= 0 && x < w && y < h) writer.setColor(x, y, color);
  }

  /** Stroke a rectangle outline of the given thickness (drawn inward). */
  private static void strokeFrame(
      PixelWriter writer, int w, int h, int x, int y, int rw, int rh, int t, Color color) {
    for (int py = y; py < y + rh; py++) {
      for (int px = x; px < x + rw; px++) {
        boolean onFrame = px < x + t || py < y + t || px >= x + rw - t || py >= y + rh - t;
        if (onFrame) put(writer, w, h, px, py, color);
      }
    }
  }

  private static void hLine(
      PixelWriter writer, int w, int h, int x0, int x1, int y, int t, Color color) {
    for (int dy = 0; dy < t; dy++) {
      for (int x = x0; x <= x1; x++) put(writer, w, h, x, y + dy, color);
    }
  }

  /** Bresenham line with square thickness. */
  private static void line(
      PixelWriter writer, int w, int h, int x0, int y0, int x1, int y1, int t, Color color) {
    int dx = Math.abs(x1 - x0);
    int dy = -Math.abs(y1 - y0);
    int sx = x0 < x1 ? 1 : -1;
    int sy = y0 < y1 ? 1 : -1;
    int err = dx + dy;
    int half = t / 2;
    while (true) {
      for (int oy = -half; oy <= half; oy++) {
        for (int ox = -half; ox <= half; ox++) put(writer, w, h, x0 + ox, y0 + oy, color);
      }
      if (x0 == x1 && y0 == y1) break;
      int e2 = 2 * err;
      if (e2 >= dy) {
        err += dy;
        x0 += sx;
      }
      if (e2 <= dx) {
        err += dx;
        y0 += sy;
      }
    }
  }

  /** Stroke a circle outline (ring) of the given thickness. */
  private static void strokeCircle(
      PixelWriter writer, int w, int h, int cx, int cy, int radius, int t, Color color) {
    int outer = radius * radius;
    int inner = (radius - t) * (radius - t);
    for (int y = cy - radius; y <= cy + radius; y++) {
      for (int x = cx - radius; x <= cx + radius; x++) {
        int d = (x - cx) * (x - cx) + (y - cy) * (y - cy);
        if (d <= outer && d >= inner) put(writer, w, h, x, y, color);
      }
    }
  }

  /** Diagonal hatch over a rectangle: every {@code spacing}-th \-diagonal inked. */
  private static void diagonalHatch(
      PixelWriter writer,
      int w,
      int h,
      int x0,
      int y0,
      int x1,
      int y1,
      int spacing,
      boolean cross,
      Color color) {
    diagonalHatch(writer, w, h, x0, y0, x1, y1, spacing, cross, color, 0, 0, -1);
  }

  /**
   * Diagonal hatch, optionally clipped to a circle (radius &gt; 0). The two diagonals are keyed off
   * {@code (x+y)} and {@code (x-y)} so the pattern is deterministic and leaves bare ground between
   * lines — that gap is what makes it read as engraving tone rather than a flat fill.
   */
  private static void diagonalHatch(
      PixelWriter writer,
      int w,
      int h,
      int x0,
      int y0,
      int x1,
      int y1,
      int spacing,
      boolean cross,
      Color color,
      int clipCx,
      int clipCy,
      int clipR) {
    for (int y = Math.max(0, y0); y < Math.min(h, y1); y++) {
      for (int x = Math.max(0, x0); x < Math.min(w, x1); x++) {
        if (clipR > 0) {
          int d = (x - clipCx) * (x - clipCx) + (y - clipCy) * (y - clipCy);
          if (d > clipR * clipR) continue;
        }
        boolean on = ((x + y) % spacing == 0) || (cross && (x - y) % spacing == 0);
        if (on) writer.setColor(x, y, color);
      }
    }
  }

  private static void crossHatch(
      PixelWriter writer, int w, int h, int x0, int y0, int x1, int y1, int spacing, Color color) {
    diagonalHatch(writer, w, h, x0, y0, x1, y1, spacing, true, color);
  }

  private static void crossHatch(
      PixelWriter writer,
      int w,
      int h,
      int x0,
      int y0,
      int x1,
      int y1,
      int spacing,
      Color color,
      int clipCx,
      int clipCy,
      int clipR) {
    diagonalHatch(writer, w, h, x0, y0, x1, y1, spacing, true, color, clipCx, clipCy, clipR);
  }

  private static void verticalHatch(
      PixelWriter writer, int w, int h, int x0, int y0, int x1, int y1, int spacing, Color color) {
    for (int x = Math.max(0, x0); x < Math.min(w, x1); x += spacing) {
      for (int y = Math.max(0, y0); y < Math.min(h, y1); y++) put(writer, w, h, x, y, color);
    }
  }

  /** A single hatched row segment (used for the coat's row-bounded trapezoid tone). */
  private static void hatchRow(
      PixelWriter writer, int w, int h, int x0, int x1, int y, int spacing, Color color) {
    for (int x = Math.max(0, x0); x <= Math.min(w - 1, x1); x++) {
      if ((x + y) % spacing == 0) put(writer, w, h, x, y, color);
    }
  }
}

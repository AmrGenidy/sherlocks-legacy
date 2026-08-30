package ui.screens;

/**
 * Responsive sizing for the Detective profile screen (gui-profile-layout): the avatar preview, the
 * gallery portraits, and the inter-block gaps shrink as the window gets short so the whole column
 * reflows to fit a 1024×720 window without a scrollbar and without colliding with the {@link
 * ui.menu.MenuPage} bottom strip or frame. Pure height→size maths — no JavaFX — so the fit is
 * unit-testable and tunable in one place.
 *
 * <p>Comfortable defaults apply at/above {@link #REF_H}; floors apply at/below {@link #MIN_H};
 * values interpolate linearly between. Gaps step on the 8px scale.
 */
public final class ProfileLayout {

  private ProfileLayout() {}

  /** Below this height everything is at its floor (the mandated minimum window height). */
  public static final double MIN_H = 720;

  /** At/above this height everything is at its comfortable default. */
  public static final double REF_H = 900;

  public static final double PREVIEW_H_DEFAULT = 148;
  public static final double PREVIEW_H_FLOOR = 104;
  public static final double PORTRAIT_H_DEFAULT = 80;
  public static final double PORTRAIT_H_FLOOR = 58;

  // Aspect ratios preserved from the original fixed sizes (w/h).
  private static final double PREVIEW_RATIO = 132.0 / 148.0;
  private static final double PORTRAIT_RATIO = 72.0 / 80.0;

  /** Linear ramp: {@code floor} at/below {@link #MIN_H}, {@code def} at/above {@link #REF_H}. */
  static double ramp(double pageHeight, double floor, double def) {
    if (pageHeight <= MIN_H) {
      return floor;
    }
    if (pageHeight >= REF_H) {
      return def;
    }
    double t = (pageHeight - MIN_H) / (REF_H - MIN_H);
    return floor + t * (def - floor);
  }

  public static double previewHeight(double pageHeight) {
    return ramp(pageHeight, PREVIEW_H_FLOOR, PREVIEW_H_DEFAULT);
  }

  public static double previewWidth(double pageHeight) {
    return previewHeight(pageHeight) * PREVIEW_RATIO;
  }

  public static double portraitHeight(double pageHeight) {
    return ramp(pageHeight, PORTRAIT_H_FLOOR, PORTRAIT_H_DEFAULT);
  }

  public static double portraitWidth(double pageHeight) {
    return portraitHeight(pageHeight) * PORTRAIT_RATIO;
  }

  /** Inter-block gap on the 8px scale: 24 comfortable → 16 → 8 tight. */
  public static double blockGap(double pageHeight) {
    if (pageHeight >= REF_H) {
      return 24;
    }
    if (pageHeight >= 800) {
      return 16;
    }
    return 8;
  }

  // --- Fit estimation (so a test can prove the floors fit a short window) -----------------------

  // Conservative fixed-element estimates (px): field label, name field, gallery vgap, the title
  // block, and the bottom strip. Used only to validate the reflow fits; the live layout measures
  // these for real.
  private static final double FIELD_LABEL = 24;
  private static final double NAME_FIELD = 36;
  private static final double GALLERY_VGAP = 14;
  private static final double TITLE_BLOCK = 132;
  private static final double BOTTOM_STRIP = 56;

  /**
   * Estimated preferred height of the profile content column at {@code pageHeight} for {@code
   * rows}.
   */
  public static double estimatedContentHeight(double pageHeight, int rows) {
    double gap = blockGap(pageHeight);
    double nameBox = FIELD_LABEL + 8 + NAME_FIELD; // VBox(8, label, field)
    double shelf = rows * portraitHeight(pageHeight) + Math.max(0, rows - 1) * GALLERY_VGAP;
    double galleryBox = FIELD_LABEL + 10 + shelf; // VBox(10, label, shelf)
    return previewHeight(pageHeight) + gap + nameBox + gap + galleryBox;
  }

  /** The height available for the content between the title block and the bottom strip. */
  public static double availableBand(double pageHeight) {
    double pad = ui.menu.MenuPage.contentPad(Math.min(1024, pageHeight)); // BorderPane padding
    return pageHeight - 2 * pad - TITLE_BLOCK - BOTTOM_STRIP;
  }
}

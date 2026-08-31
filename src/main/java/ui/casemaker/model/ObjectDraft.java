package ui.casemaker.model;

/**
 * A mutable, in-editor representation of an authored {@code Object} (CONTEXT.md): a placeable,
 * ID-bearing item that sits in a {@link RoomDraft} at a normalized position and reveals a Clue when
 * examined. Render fields (id, image, position, sprite scale) are universal; the examine/deduce/
 * description text is working-language for now and is generalised to per-language in slice 5.
 *
 * <p>Position is normalized to {@code [0,1]} (the {@code RoomView} contract) and is {@code null}
 * while the object is unplaced. Id derivation mirrors {@code GameObjectExtractor}: an explicit id
 * wins (trimmed); otherwise the lowercased name with spaces turned into underscores.
 */
public final class ObjectDraft {

  private String name;
  private String explicitId;
  private String imagePath;
  private Double posX;
  private Double posY;
  // Independent horizontal/vertical sprite scale (multiplies the base render size in RoomView).
  // A uniform scale sets both; the placement handles resize them separately. Default 1.0.
  private double imageScaleX = 1.0;
  private double imageScaleY = 1.0;
  // Mirror the sprite horizontally / vertically (RoomView negates scaleX/scaleY). Default false.
  private boolean flipX;
  private boolean flipY;
  // Clockwise sprite rotation in degrees about the centre (Case Maker placement rotation grips).
  // Default 0 (upright).
  private double rotation;
  // Authored name-label offset from the sprite centre, as a fraction of sprite height (labelDX
  // across, labelDY down). Null = the default "just below the sprite" position (RoomView fallback).
  private Double labelDX;
  private Double labelDY;
  // Per-language object Display Name (.scratch/gui-localized-case-names); shown in the GUI click
  // popup, falls back to the Universal name. Universal Name stays {@link #name}.
  private final LocalizedText displayName = new LocalizedText();
  private final LocalizedText description = new LocalizedText();
  private final LocalizedText examine = new LocalizedText();
  private final LocalizedText deduce = new LocalizedText();

  ObjectDraft(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  /** The effective id: an explicit id when set, otherwise derived from the name. */
  public String getId() {
    if (explicitId != null && !explicitId.trim().isEmpty()) {
      return explicitId.trim();
    }
    return name == null ? "" : name.toLowerCase().replace(" ", "_");
  }

  public void setId(String id) {
    this.explicitId = id;
  }

  public String getImagePath() {
    return imagePath;
  }

  public void setImagePath(String imagePath) {
    this.imagePath = imagePath;
  }

  public Double getPosX() {
    return posX;
  }

  public Double getPosY() {
    return posY;
  }

  /** Places the object at a normalized position, clamping each coordinate into {@code [0,1]}. */
  public void setPosition(double x, double y) {
    this.posX = clampUnit(x);
    this.posY = clampUnit(y);
  }

  /** The horizontal sprite scale (representative uniform value for legacy callers). */
  public double getImageScale() {
    return imageScaleX;
  }

  /** Sets a uniform sprite scale (both axes); non-positive/non-finite values are ignored. */
  public void setImageScale(double scale) {
    setImageScaleX(scale);
    setImageScaleY(scale);
  }

  public double getImageScaleX() {
    return imageScaleX;
  }

  public double getImageScaleY() {
    return imageScaleY;
  }

  /** Sets the horizontal sprite scale; non-positive or non-finite values are ignored. */
  public void setImageScaleX(double scale) {
    if (scale > 0 && Double.isFinite(scale)) {
      this.imageScaleX = scale;
    }
  }

  /** Sets the vertical sprite scale; non-positive or non-finite values are ignored. */
  public void setImageScaleY(double scale) {
    if (scale > 0 && Double.isFinite(scale)) {
      this.imageScaleY = scale;
    }
  }

  public boolean isFlipX() {
    return flipX;
  }

  public boolean isFlipY() {
    return flipY;
  }

  public void setFlipX(boolean flipX) {
    this.flipX = flipX;
  }

  public void setFlipY(boolean flipY) {
    this.flipY = flipY;
  }

  /** Clockwise sprite rotation in degrees about the centre (0 = upright). */
  public double getRotation() {
    return rotation;
  }

  /** Sets the sprite rotation in degrees; non-finite values are ignored. */
  public void setRotation(double rotation) {
    if (Double.isFinite(rotation)) {
      this.rotation = rotation;
    }
  }

  private static double clampUnit(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }

  public Double getLabelDX() {
    return labelDX;
  }

  public Double getLabelDY() {
    return labelDY;
  }

  /**
   * Sets the name-label offset from the sprite centre (fraction of sprite height), clamping each
   * axis to a sane range so a stray drag can't fling the caption off-screen.
   */
  public void setLabelOffset(double dx, double dy) {
    this.labelDX = clampOffset(dx);
    this.labelDY = clampOffset(dy);
  }

  /** Clears the authored label offset (back to the default "just below the sprite"). */
  public void clearLabelOffset() {
    this.labelDX = null;
    this.labelDY = null;
  }

  private static double clampOffset(double value) {
    return Math.max(-4.0, Math.min(4.0, value));
  }

  // Primary-language convenience accessors (used by the single-language editors). The …Text()
  // methods expose the full per-language text for the Localization tab.

  public String getDescription() {
    return description.get();
  }

  public void setDescription(String description) {
    this.description.set(description);
  }

  public LocalizedText descriptionText() {
    return description;
  }

  /**
   * Per-language object Display Name, for the Localization tab (falls back to the Universal name).
   */
  public LocalizedText displayNameText() {
    return displayName;
  }

  public String getExamine() {
    return examine.get();
  }

  public void setExamine(String examine) {
    this.examine.set(examine);
  }

  public LocalizedText examineText() {
    return examine;
  }

  public String getDeduce() {
    return deduce.get();
  }

  public void setDeduce(String deduce) {
    this.deduce.set(deduce);
  }

  public LocalizedText deduceText() {
    return deduce;
  }
}

package Core;

public class GameObject {
  private final String id; // <-- Added ID
  private String name;
  // Optional per-language Display Name (.scratch/gui-localized-case-names). Shown only in the GUI
  // click popup; commands/terminal/autocomplete keep using the Universal name. Null/blank ->
  // getDisplayName() falls back to name.
  private String displayName;
  private String description;
  private String examine; // Detailed examination text
  private String deduce; // Deduction text
  private String imagePath; // NEW: Image path support
  private final Double normalizedPosX; // 0..1 or null
  private final Double normalizedPosY;
  // Optional per-object sprite scale (multiplies the base render size in RoomView). Defaults to
  // 1.0.
  private double imageScaleX = 1.0;
  private double imageScaleY = 1.0;
  private boolean flipX;
  private boolean flipY;
  // Clockwise sprite rotation in degrees about the sprite centre (RoomView applies setRotate).
  // Default 0 (upright).
  private double rotation;
  // Authored name-label offset from the sprite centre, as a fraction of sprite height (RoomView
  // positions the caption there). Null = the default "just below the sprite" position.
  private Double labelDX;
  private Double labelDY;
  private String spriteImage;
  private int x;
  private int y;

  // Updated Constructor with ID and Positions
  public GameObject(
      String id,
      String name,
      String description,
      String examine,
      String deduce,
      String imagePath,
      Double normalizedPosX,
      Double normalizedPosY) {
    if (id == null || id.trim().isEmpty()) {
      throw new IllegalArgumentException("GameObject ID cannot be null or empty.");
    }
    this.id = id;
    this.name = name;
    this.description = description;
    this.examine = examine;
    this.deduce = deduce;
    this.imagePath = imagePath;
    this.normalizedPosX = normalizedPosX;
    this.normalizedPosY = normalizedPosY;
  }

  // Legacy constructor
  public GameObject(
      String id, String name, String description, String examine, String deduce, String imagePath) {
    this(id, name, description, examine, deduce, imagePath, null, null);
  }

  // Legacy constructor
  public GameObject(String id, String name, String description, String examine, String deduce) {
    this(id, name, description, examine, deduce, null, null, null);
  }

  // Getters
  public String getId() { // <-- Added Getter
    return id;
  }

  public String getName() {
    return name;
  }

  /**
   * The per-language Display Name shown in the GUI click popup, falling back to the Universal
   * {@link #getName() name} when none is set. Never used to resolve a command.
   */
  public String getDisplayName() {
    return (displayName != null && !displayName.trim().isEmpty()) ? displayName : name;
  }

  /** Sets the per-language Display Name; null/blank clears it (so the Universal Name shows). */
  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getDescription() {
    return description;
  }

  public String getExamine() {
    return examine;
  }

  public String getDeduce() {
    return deduce;
  }

  public String getImagePath() {
    return imagePath;
  }

  public Double getNormalizedPosX() {
    return normalizedPosX;
  }

  public Double getNormalizedPosY() {
    return normalizedPosY;
  }

  /**
   * Per-object sprite scale; multiplies the base render size in RoomView. Always &gt; 0 (default
   * 1.0).
   */
  public double getImageScale() {
    return imageScaleX;
  }

  /** Sets a uniform sprite scale (both axes). Non-positive/non-finite values are ignored. */
  public void setImageScale(double scale) {
    setImageScaleX(scale);
    setImageScaleY(scale);
  }

  /** Independent horizontal/vertical sprite scale (RoomView sizes width/height from these). */
  public double getImageScaleX() {
    return imageScaleX;
  }

  public double getImageScaleY() {
    return imageScaleY;
  }

  public void setImageScaleX(double scale) {
    if (scale > 0 && Double.isFinite(scale)) {
      this.imageScaleX = scale;
    }
  }

  public void setImageScaleY(double scale) {
    if (scale > 0 && Double.isFinite(scale)) {
      this.imageScaleY = scale;
    }
  }

  /** Mirror the sprite horizontally / vertically (RoomView negates scaleX/scaleY). */
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

  /** Clockwise sprite rotation in degrees about the sprite centre (0 = upright). */
  public double getRotation() {
    return rotation;
  }

  public void setRotation(double rotation) {
    if (Double.isFinite(rotation)) {
      this.rotation = rotation;
    }
  }

  /** Authored name-label offset (fraction of sprite height); null when unset (RoomView default). */
  public Double getLabelDX() {
    return labelDX;
  }

  public Double getLabelDY() {
    return labelDY;
  }

  /** Sets the name-label offset from the sprite centre; either coordinate null clears it. */
  public void setLabelOffset(Double labelDX, Double labelDY) {
    this.labelDX = labelDX;
    this.labelDY = labelDY;
  }

  // Deduce method (no longer abstract)
  public String deduce() {
    return deduce;
  }
}

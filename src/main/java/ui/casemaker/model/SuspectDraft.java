package ui.casemaker.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A mutable, in-editor representation of a Suspect (CONTEXT.md). Identity and render fields (id,
 * image, sprite scale) plus authored placement (home room, normalized position, stationary flag —
 * DEC-5) and the LIE/TRUTH/PANIC state machine.
 *
 * <p>The home room is held as a {@link RoomDraft} reference so a room rename propagates for free.
 * Position is normalized to {@code [0,1]} and is {@code null} while unplaced. Id derivation mirrors
 * the engine: explicit id (trimmed) wins, else the lowercased name with spaces as underscores.
 */
public final class SuspectDraft {

  /** The canonical suspect states, in display order. */
  public static final String[] STATES = {"LIE", "TRUTH", "PANIC"};

  private String name;
  private String explicitId;
  private String imagePath;
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
  // Per-language suspect Display Name (.scratch/gui-localized-case-names); shown in the GUI click
  // popup, falls back to the Universal name. Universal Name stays {@link #name}.
  private final LocalizedText displayName = new LocalizedText();

  private RoomDraft homeRoom;
  private Double posX;
  private Double posY;
  private boolean stationary;
  // Authored name-label offset from the sprite centre, as a fraction of sprite height (labelDX
  // across, labelDY down). Null = the default "just below the sprite" position (RoomView fallback).
  private Double labelDX;
  private Double labelDY;

  private String initialState = "LIE";
  private final Map<String, SuspectStateDraft> states = new LinkedHashMap<>();

  // A "simple" suspect (no LIE/TRUTH/PANIC states) gives a single per-language statement plus an
  // optional clue. These are preserved through the load/export round-trip so a suspect that is not
  // contradictable never loses its dialogue (a state-based suspect leaves these empty and speaks
  // through its states instead).
  private final LocalizedText statement = new LocalizedText();
  private final LocalizedText clue = new LocalizedText();

  SuspectDraft(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  /** Per-language suspect Display Name, for the Localization tab (falls back to Universal name). */
  public LocalizedText displayNameText() {
    return displayName;
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

  public RoomDraft getHomeRoom() {
    return homeRoom;
  }

  public void setHomeRoom(RoomDraft homeRoom) {
    this.homeRoom = homeRoom;
  }

  public Double getPosX() {
    return posX;
  }

  public Double getPosY() {
    return posY;
  }

  /** Places the suspect at a normalized position, clamping each coordinate into {@code [0,1]}. */
  public void setPosition(double x, double y) {
    this.posX = Math.max(0.0, Math.min(1.0, x));
    this.posY = Math.max(0.0, Math.min(1.0, y));
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
    this.labelDX = Math.max(-4.0, Math.min(4.0, dx));
    this.labelDY = Math.max(-4.0, Math.min(4.0, dy));
  }

  /** Clears the authored label offset (back to the default "just below the sprite"). */
  public void clearLabelOffset() {
    this.labelDX = null;
    this.labelDY = null;
  }

  public boolean isStationary() {
    return stationary;
  }

  public void setStationary(boolean stationary) {
    this.stationary = stationary;
  }

  public String getInitialState() {
    return initialState;
  }

  public void setInitialState(String initialState) {
    if (initialState != null && !initialState.isBlank()) {
      this.initialState = initialState.toUpperCase();
    }
  }

  /** The per-language single statement of a simple (state-less) suspect. */
  public LocalizedText statementText() {
    return statement;
  }

  /** The per-language clue of a simple (state-less) suspect. */
  public LocalizedText clueText() {
    return clue;
  }

  /** Returns the named state, creating it on first access (key normalized to upper case). */
  public SuspectStateDraft state(String name) {
    return states.computeIfAbsent(name.toUpperCase(), k -> new SuspectStateDraft());
  }

  /**
   * Returns the named state only if it already exists, else {@code null} — without creating it. The
   * editor uses this to render a state card for browsing without silently materialising an empty
   * state (which would make merely viewing a case look "modified" on close).
   */
  public SuspectStateDraft getState(String name) {
    return states.get(name.toUpperCase());
  }

  /** The states that have been created, keyed by upper-case state name (read-only). */
  public Map<String, SuspectStateDraft> getStates() {
    return Collections.unmodifiableMap(states);
  }
}

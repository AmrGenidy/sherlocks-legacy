package Core;

import JsonDTO.CaseFile.ContradictionRule;
import JsonDTO.CaseFile.SuspectStateData;
import java.util.HashMap;
import java.util.Map;

public class Suspect extends MovableCharacter {
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Suspect.class);

  private final String id;
  private String name;
  // Optional per-language Display Name (.scratch/gui-localized-case-names). Shown only in the GUI
  // click popup; commands/terminal/autocomplete keep using the Universal name. Null/blank ->
  // getDisplayName() falls back to name.
  private String displayName;
  private String statement; // Legacy/Fallback
  private String clue;
  private String imagePath; // NEW: Image path support
  // Optional per-suspect sprite scale (multiplies the base render size in RoomView). Defaults to
  // 1.0.
  private double imageScaleX = 1.0;
  private double imageScaleY = 1.0;
  private boolean flipX;
  private boolean flipY;
  // Clockwise sprite rotation in degrees about the sprite centre (RoomView applies setRotate).
  // Default 0 (upright).
  private double rotation;

  // Authored placement (Case Maker slice 3, DEC-5). homeRoom is the room the suspect is placed in
  // at case start; posX/posY are the normalized [0,1] render position in RoomView; stationary, when
  // true, keeps the suspect in its home room (false preserves the historical random wander).
  private String homeRoom;
  private Double posX;
  private Double posY;
  private boolean stationary;
  // Authored name-label offset from the sprite centre, as a fraction of sprite height (RoomView
  // positions the caption there). Null = the default "just below the sprite" position.
  private Double labelDX;
  private Double labelDY;

  public enum SuspectState {
    LIE,
    TRUTH,
    PANIC
  }

  private SuspectState currentState = SuspectState.LIE;
  private Map<SuspectState, SuspectStateData> stateData = new HashMap<>();

  // Updated Constructor with ID
  public Suspect(String id, String name, String statement, String clue, String imagePath) {
    if (id == null || id.trim().isEmpty()) {
      throw new IllegalArgumentException("Suspect ID cannot be null or empty.");
    }
    this.id = id;
    this.name = name;
    this.statement = statement;
    this.clue = clue;
    this.imagePath = imagePath;

    // Initialize default LIE state with the legacy statement if no state data is
    // added later
    // The extractor will override this if 'states' are present in JSON.
  }

  // Legacy constructor
  public Suspect(String id, String name, String statement, String clue) {
    this(id, name, statement, clue, null);
  }

  public String getId() {
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

  public String getStatement() {
    if (stateData.containsKey(currentState)) {
      String stateStmt = stateData.get(currentState).getStatement();
      if (stateStmt != null && !stateStmt.isEmpty()) {
        return stateStmt;
      }
    }
    return statement; // Fallback
  }

  public String getClue() {
    return clue;
  }

  public String getImagePath() {
    return imagePath;
  }

  /**
   * Per-suspect sprite scale; multiplies the base render size in RoomView. Always > 0 (default
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

  /** The authored home room name (where the suspect is placed at case start); may be null. */
  public String getHomeRoom() {
    return homeRoom;
  }

  public void setHomeRoom(String homeRoom) {
    this.homeRoom = homeRoom;
  }

  /** Normalized [0,1] render position in RoomView; null when unplaced. */
  public Double getPosX() {
    return posX;
  }

  public Double getPosY() {
    return posY;
  }

  public void setPosition(Double posX, Double posY) {
    this.posX = posX;
    this.posY = posY;
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

  /** When true the suspect stays in its home room; when false it wanders (historical behaviour). */
  public boolean isStationary() {
    return stationary;
  }

  public void setStationary(boolean stationary) {
    this.stationary = stationary;
  }

  // --- State Machine Methods ---

  public void setStateData(Map<String, SuspectStateData> rawStates) {
    if (rawStates == null) return;

    for (Map.Entry<String, SuspectStateData> entry : rawStates.entrySet()) {
      try {
        SuspectState stateKey = SuspectState.valueOf(entry.getKey().toUpperCase());
        stateData.put(stateKey, entry.getValue());
      } catch (IllegalArgumentException e) {
        // Ignore unknown states
        logger.warn("Warning: Unknown suspect state in JSON: " + entry.getKey());
      }
    }
  }

  public void setInitialState(String stateName) {
    if (stateName != null) {
      try {
        this.currentState = SuspectState.valueOf(stateName.toUpperCase());
      } catch (IllegalArgumentException e) {
        // Ignore
      }
    }
  }

  public ContradictionRule checkContradiction(String evidenceId) {
    SuspectStateData data = stateData.get(currentState);
    if (data == null || data.getContradictions() == null) return null;

    for (ContradictionRule rule : data.getContradictions()) {
      if (rule.getEvidenceId().equalsIgnoreCase(evidenceId)) {
        return rule;
      }
    }
    return null;
  }

  public void transitionState(String nextStateName) {
    try {
      SuspectState next = SuspectState.valueOf(nextStateName.toUpperCase());
      this.currentState = next;
    } catch (IllegalArgumentException e) {
      // Stay in current state if invalid transition
    }
  }

  public SuspectState getCurrentState() {
    return currentState;
  }

  public SuspectStateData getCurrentStateData() {
    return stateData.get(currentState);
  }
}

package Core;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Room implements Serializable {
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Room.class);

  // Name is final, set once. Good. Description can change if needed.
  private final String name;
  private String description;
  private final String imagePath; // NEW: Image path support
  // Optional per-language Display Name (.scratch/gui-localized-case-names). The room graph keys on
  // the Universal Name; this is GUI-facing only. Null/blank -> getDisplayName() falls back to name.
  private String displayName;
  // Per-room Dr. Watson sprite position (normalized 0–1), authored in the Case Maker placement tab.
  // Watson follows the player, so each room stores its own spot; null means "use RoomView's
  // default". Flows into the room DTO so RoomView can anchor Watson where the author placed him.
  private Double watsonPosX;
  private Double watsonPosY;
  // Per-room Dr. Watson size/orientation (Case Maker placement tab). Each is optional; null means
  // "fall back to the case's global metadata.watson* value". Lets Watson be scaled/flipped/rotated
  // per room to match each room's perspective.
  private Double watsonImageScaleX;
  private Double watsonImageScaleY;
  private Boolean watsonFlipX;
  private Boolean watsonFlipY;
  private Double watsonRotation;
  private Double watsonLabelDX;
  private Double watsonLabelDY;
  // Using protected so subclasses (if I ever make them for special rooms) can directly access,
  // but generally, I'll use the public methods.
  protected Map<String, Room> neighbors = new HashMap<>(); // Direction (lowercase) -> Neighbor Room
  protected Map<String, GameObject> objects =
      new HashMap<>(); // Object Name (lowercase) -> GameObject

  /**
   * My main constructor for a Room.
   *
   * @param name The unique name of this room. Can't be null or empty, obviously.
   * @param description The text description players see when they enter or look.
   * @param imagePath The optional path to the room's image.
   */
  public Room(String name, String description, String imagePath) {
    if (name == null || name.trim().isEmpty()) {
      // Don't want rooms without names, that'd be a mess.
      throw new IllegalArgumentException("Room name cannot be null or empty.");
    }
    this.name = name.trim(); // Trim it just in case.
    this.description = description;
    this.imagePath = imagePath;
  }

  /**
   * Legacy constructor for backward compatibility.
   */
  public Room(String name, String description) {
    this(name, description, null);
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getImagePath() {
    return imagePath;
  }

  /**
   * The per-language Display Name shown in the GUI/terminal, falling back to the Universal {@link
   * #getName() name} when none is set. Never used to resolve a command or key the room graph.
   */
  public String getDisplayName() {
    return (displayName != null && !displayName.trim().isEmpty()) ? displayName : name;
  }

  /** Sets the per-language Display Name; null/blank clears it (so the Universal Name shows). */
  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  /** The authored per-room Dr. Watson X position (normalized 0–1), or null when unset. */
  public Double getWatsonPosX() {
    return watsonPosX;
  }

  /** The authored per-room Dr. Watson Y position (normalized 0–1), or null when unset. */
  public Double getWatsonPosY() {
    return watsonPosY;
  }

  /** Sets Watson's authored position for this room; either coordinate null clears the placement. */
  public void setWatsonPosition(Double x, Double y) {
    this.watsonPosX = x;
    this.watsonPosY = y;
  }

  // Per-room Watson size/orientation (null = fall back to the case's global metadata.watson* value).
  public Double getWatsonImageScaleX() {
    return watsonImageScaleX;
  }

  public Double getWatsonImageScaleY() {
    return watsonImageScaleY;
  }

  public Boolean getWatsonFlipX() {
    return watsonFlipX;
  }

  public Boolean getWatsonFlipY() {
    return watsonFlipY;
  }

  public Double getWatsonRotation() {
    return watsonRotation;
  }

  public Double getWatsonLabelDX() {
    return watsonLabelDX;
  }

  public Double getWatsonLabelDY() {
    return watsonLabelDY;
  }

  /** Sets Watson's per-room size/orientation overrides (any null leaves the global fallback). */
  public void setWatsonPlacement(
      Double scaleX,
      Double scaleY,
      Boolean flipX,
      Boolean flipY,
      Double rotation,
      Double labelDX,
      Double labelDY) {
    this.watsonImageScaleX = scaleX;
    this.watsonImageScaleY = scaleY;
    this.watsonFlipX = flipX;
    this.watsonFlipY = flipY;
    this.watsonRotation = rotation;
    this.watsonLabelDX = labelDX;
    this.watsonLabelDY = labelDY;
  }

  /**
   * Allows changing the room's description dynamically if the game needs it.
   *
   * @param description The new description text.
   */
  public void setDescription(String description) {
    this.description = description;
  }

  /**
   * Gets a map of neighboring rooms.
   *
   * @return A new Map instance to prevent external modification of my internal neighbors map.
   */
  public Map<String, Room> getNeighbors() {
    // Always return a copy. Don't want anyone messing with my internal map directly.
    return new HashMap<>(neighbors);
  }

  /**
   * Sets a neighbor in a specific direction. Direction is stored as lowercase.
   *
   * @param direction The direction (e.g., "north", "south").
   * @param neighbor The Room object that is the neighbor.
   */
  public void setNeighbor(String direction, Room neighbor) {
    if (direction == null || direction.trim().isEmpty() || neighbor == null) {
      // Basic check, probably log this error instead of just System.err if it was a bigger app.
      logger.warn(
          "ROOM_ERROR: Invalid direction or null neighbor for setNeighbor in room '"
              + this.name
              + "'.");
      return;
    }
    neighbors.put(direction.trim().toLowerCase(), neighbor);
  }

  /**
   * Gets the neighboring room in a given direction.
   *
   * @param direction The direction to check (case-insensitive).
   * @return The neighboring Room, or null if no exit in that direction.
   */
  public Room getNeighbor(String direction) {
    if (direction == null) return null;
    return neighbors.get(direction.trim().toLowerCase());
  }

  /**
   * Retrieves a specific GameObject from this room by its name.
   *
   * @param objectName The name of the object (case-insensitive).
   * @return The GameObject, or null if not found.
   */
  public GameObject getObject(String objectName) {
    if (objectName == null) return null;
    return objects.get(objectName.trim().toLowerCase());
  }

  /**
   * Adds a GameObject to this room. The object's name (converted to lowercase) is used as the key.
   *
   * @param object The GameObject to add.
   */
  public void addObject(GameObject object) {
    if (object == null || object.getName() == null || object.getName().trim().isEmpty()) {
      logger.warn(
          "ROOM_ERROR: Invalid object (null or no name) for addObject in room '"
              + this.name
              + "'.");
      return;
    }
    // Store object by its name, lowercase, for easy lookup.
    objects.put(object.getName().trim().toLowerCase(), object);
  }

  /**
   * Gets all GameObjects present in this room.
   *
   * @return A new Map instance to prevent external modification of my internal objects map.
   */
  public Map<String, GameObject> getObjects() {
    // Defensive copy again. Good habit.
    return new HashMap<>(objects);
  }

  // Standard equals and hashCode based on the room's 'name'.
  // This is important if I store Room objects in Sets or use them as keys in Maps
  // where uniqueness is determined by name.
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Room room = (Room) o;
    return name.equals(room.name); // Names are unique identifiers for rooms.
  }

  @Override
  public int hashCode() {
    return name.hashCode(); // Consistent with equals.
  }

  /**
   * Simple toString for debugging or logging Room objects.
   *
   * @return String representation of the room.
   */
  @Override
  public String toString() {
    // Just name and description, don't need full neighbors/objects here for a quick look.
    return "Room{name='"
        + name
        + "', description_preview='"
        + (description != null
            ? description.substring(0, Math.min(description.length(), 30)) + "..."
            : "N/A")
        + "'}";
  }
}

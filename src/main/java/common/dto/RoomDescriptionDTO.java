package common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RoomDescriptionDTO implements Serializable {
  @Serial private static final long serialVersionUID = 1L;
  private final String name;
  private final String description;
  private final List<String> objectNames;
  private final List<String> occupantNames;
  private final Map<String, String> exits;
  private final String imagePath; // NEW: Image path support
  private final Map<String, VisualPositionDTO> objectPositions; // NEW: Object positions
  // NEW: per-sprite render scale (element display name -> imageScale). Covers both objects and
  // suspects; RoomView multiplies the base sprite size by this. Absent entries default to 1.0.
  private final Map<String, Double> spriteScales;
  // Per-language Display Names (.scratch/gui-localized-case-names). The room's own Display Name
  // (header/label/sidebar) and the side maps Universal-Name -> Display-Name for objects/occupants
  // (the GUI click popup). The objectNames/occupantNames lists above and the room `name` stay
  // Universal so commands, autocomplete and the terminal are unaffected. Absent map entries / a
  // null displayName mean "fall back to the Universal Name". Exit map VALUES already carry the
  // neighbour's Display Name (exits are display-only; movement is by the direction key).
  private final String displayName;
  private final Map<String, String> objectDisplayNames;
  private final Map<String, String> occupantDisplayNames;
  // Authored name-label offsets keyed by element name (x=labelDX, y=labelDY as a fraction of sprite
  // height). RoomView positions each caption at this offset from its sprite; absent = default spot.
  private final Map<String, VisualPositionDTO> labelOffsets;
  // Per-element VERTICAL sprite scale (the base spriteScales map carries the HORIZONTAL scale);
  // absent means "uniform" (Y follows X). Mirror flags per element (x/y in {0,1}); absent = none.
  private final Map<String, Double> spriteScalesY;
  private final Map<String, VisualPositionDTO> flips;

  @JsonCreator
  public RoomDescriptionDTO(
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("objectNames") List<String> objectNames,
      @JsonProperty("occupantNames") List<String> occupantNames,
      @JsonProperty("exits") Map<String, String> exits,
      @JsonProperty("imagePath") String imagePath,
      @JsonProperty("objectPositions") Map<String, VisualPositionDTO> objectPositions,
      @JsonProperty("spriteScales") Map<String, Double> spriteScales,
      @JsonProperty("displayName") String displayName,
      @JsonProperty("objectDisplayNames") Map<String, String> objectDisplayNames,
      @JsonProperty("occupantDisplayNames") Map<String, String> occupantDisplayNames,
      @JsonProperty("labelOffsets") Map<String, VisualPositionDTO> labelOffsets,
      @JsonProperty("spriteScalesY") Map<String, Double> spriteScalesY,
      @JsonProperty("flips") Map<String, VisualPositionDTO> flips) {
    this.name = name;
    this.description = description;
    this.objectNames = objectNames != null ? new ArrayList<>(objectNames) : new ArrayList<>();
    this.occupantNames = occupantNames != null ? new ArrayList<>(occupantNames) : new ArrayList<>();
    this.exits = exits != null ? new HashMap<>(exits) : new HashMap<>();
    this.imagePath = imagePath;
    this.objectPositions =
        objectPositions != null ? new HashMap<>(objectPositions) : new HashMap<>();
    this.spriteScales = spriteScales != null ? new HashMap<>(spriteScales) : new HashMap<>();
    this.displayName = displayName;
    this.objectDisplayNames =
        objectDisplayNames != null ? new HashMap<>(objectDisplayNames) : new HashMap<>();
    this.occupantDisplayNames =
        occupantDisplayNames != null ? new HashMap<>(occupantDisplayNames) : new HashMap<>();
    this.labelOffsets = labelOffsets != null ? new HashMap<>(labelOffsets) : new HashMap<>();
    this.spriteScalesY = spriteScalesY != null ? new HashMap<>(spriteScalesY) : new HashMap<>();
    this.flips = flips != null ? new HashMap<>(flips) : new HashMap<>();
  }

  // Overloaded constructor for backward compatibility (no label offsets / independent scale / flip).
  public RoomDescriptionDTO(
      String name,
      String description,
      List<String> objectNames,
      List<String> occupantNames,
      Map<String, String> exits,
      String imagePath,
      Map<String, VisualPositionDTO> objectPositions,
      Map<String, Double> spriteScales,
      String displayName,
      Map<String, String> objectDisplayNames,
      Map<String, String> occupantDisplayNames) {
    this(
        name,
        description,
        objectNames,
        occupantNames,
        exits,
        imagePath,
        objectPositions,
        spriteScales,
        displayName,
        objectDisplayNames,
        occupantDisplayNames,
        null,
        null,
        null);
  }

  // Overloaded constructor for backward compatibility
  public RoomDescriptionDTO(
      String name,
      String description,
      List<String> objectNames,
      List<String> occupantNames,
      Map<String, String> exits,
      String imagePath,
      Map<String, VisualPositionDTO> objectPositions,
      Map<String, Double> spriteScales) {
    this(
        name,
        description,
        objectNames,
        occupantNames,
        exits,
        imagePath,
        objectPositions,
        spriteScales,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  // Overloaded constructor for backward compatibility
  public RoomDescriptionDTO(
      String name,
      String description,
      List<String> objectNames,
      List<String> occupantNames,
      Map<String, String> exits,
      String imagePath,
      Map<String, VisualPositionDTO> objectPositions) {
    this(name, description, objectNames, occupantNames, exits, imagePath, objectPositions, null);
  }

  // Overloaded constructor for backward compatibility
  public RoomDescriptionDTO(
      String name,
      String description,
      List<String> objectNames,
      List<String> occupantNames,
      Map<String, String> exits,
      String imagePath) {
    this(name, description, objectNames, occupantNames, exits, imagePath, null, null);
  }

  // Overloaded constructor for backward compatibility
  public RoomDescriptionDTO(
      String name,
      String description,
      List<String> objectNames,
      List<String> occupantNames,
      Map<String, String> exits) {
    this(name, description, objectNames, occupantNames, exits, null, null, null);
  }

  public String getName() {
    return name;
  }

  /**
   * The room's per-language Display Name (header/label/sidebar), falling back to the Universal
   * {@link #getName() name} when none was authored.
   */
  public String getDisplayName() {
    return (displayName != null && !displayName.isBlank()) ? displayName : name;
  }

  /** Universal-Name -> Display-Name for objects; absent entries mean "use the Universal Name". */
  public Map<String, String> getObjectDisplayNames() {
    return new HashMap<>(objectDisplayNames);
  }

  /** Universal-Name -> Display-Name for occupants; absent entries mean "use the Universal Name". */
  public Map<String, String> getOccupantDisplayNames() {
    return new HashMap<>(occupantDisplayNames);
  }

  public String getDescription() {
    return description;
  }

  public List<String> getObjectNames() {
    return new ArrayList<>(objectNames);
  }

  public List<String> getOccupantNames() {
    return new ArrayList<>(occupantNames);
  }

  public Map<String, String> getExits() {
    return new HashMap<>(exits);
  }

  public String getImagePath() {
    return imagePath;
  }

  public Map<String, VisualPositionDTO> getObjectPositions() {
    return new HashMap<>(objectPositions);
  }

  /**
   * Per-sprite render scales keyed by element display name; absent entries mean the default 1.0.
   */
  public Map<String, Double> getSpriteScales() {
    return new HashMap<>(spriteScales);
  }

  /**
   * Authored name-label offsets keyed by element name (x=labelDX, y=labelDY, a fraction of sprite
   * height); absent entries mean RoomView's default "just below the sprite" position.
   */
  public Map<String, VisualPositionDTO> getLabelOffsets() {
    return new HashMap<>(labelOffsets);
  }

  /**
   * Per-element vertical sprite scale (the {@link #getSpriteScales()} map holds the horizontal
   * scale); an absent entry means the sprite is scaled uniformly (Y follows X).
   */
  public Map<String, Double> getSpriteScalesY() {
    return new HashMap<>(spriteScalesY);
  }

  /**
   * Per-element mirror flags as a {@link VisualPositionDTO} where {@code x/y} in {@code {0,1}} are
   * flipX/flipY; an absent entry means no flip.
   */
  public Map<String, VisualPositionDTO> getFlips() {
    return new HashMap<>(flips);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Room: ").append(name).append("\n");
    sb.append(description).append("\n");
    sb.append("Objects: ")
        .append(objectNames.isEmpty() ? "None" : String.join(", ", objectNames))
        .append("\n");
    sb.append("Occupants: ")
        .append(occupantNames.isEmpty() ? "None" : String.join(", ", occupantNames))
        .append("\n");
    sb.append("Exits: ");
    if (exits.isEmpty()) {
      sb.append("None");
    } else {
      exits.forEach((dir, room) -> sb.append(dir).append(" (").append(room).append("), "));
      sb.setLength(sb.length() - 2);
    }
    return sb.toString();
  }
}

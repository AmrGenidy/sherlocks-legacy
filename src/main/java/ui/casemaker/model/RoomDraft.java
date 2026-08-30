package ui.casemaker.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A mutable, in-editor representation of a single {@code Room} while a case is being authored in
 * the Case Maker.
 *
 * <p>Neighbours are held as references to other {@link RoomDraft}s keyed by direction, not by room
 * name. This makes two invariants free: a link can only ever point at a room that exists (no
 * dangling neighbour targets are representable), and renaming a room is automatically reflected
 * everywhere it is referenced. {@link CaseDraft} owns the linking operations so that reciprocity
 * (the reverse direction) is maintained.
 */
public final class RoomDraft {

  private String name;
  private String imagePath;
  // Per-room Dr. Watson sprite position (normalized 0–1). Watson follows the player, so each room
  // he can appear in stores its own spot; null while unplaced (RoomView then uses its default).
  private Double watsonPosX;
  private Double watsonPosY;
  // Per-room Dr. Watson size/orientation. Each is null until the author overrides it in this room;
  // null means "use the case's global metadata.watson* value". Lets Watson be sized/flipped/rotated
  // per room to match each room's perspective.
  private Double watsonImageScaleX;
  private Double watsonImageScaleY;
  private Boolean watsonFlipX;
  private Boolean watsonFlipY;
  private Double watsonRotation;
  private Double watsonLabelDX;
  private Double watsonLabelDY;
  // Per-language room Display Name (.scratch/gui-localized-case-names); shown in the GUI, falls back
  // to the Universal name. Universal Name stays {@link #name}.
  private final LocalizedText displayName = new LocalizedText();
  private final LocalizedText description = new LocalizedText();
  private final Map<String, RoomDraft> neighbors = new LinkedHashMap<>();
  private final List<ObjectDraft> objects = new ArrayList<>();

  RoomDraft(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  void setName(String name) {
    this.name = name;
  }

  public String getImagePath() {
    return imagePath;
  }

  public void setImagePath(String imagePath) {
    this.imagePath = imagePath;
  }

  public Double getWatsonPosX() {
    return watsonPosX;
  }

  public Double getWatsonPosY() {
    return watsonPosY;
  }

  /** Places Dr. Watson at a normalized position for this room, clamping each into {@code [0,1]}. */
  public void setWatsonPosition(double x, double y) {
    this.watsonPosX = clampUnit(x);
    this.watsonPosY = clampUnit(y);
  }

  // Per-room Watson size/orientation overrides (null = fall back to the case's global value).
  public Double getWatsonImageScaleX() {
    return watsonImageScaleX;
  }

  public Double getWatsonImageScaleY() {
    return watsonImageScaleY;
  }

  public void setWatsonImageScaleX(Double scale) {
    this.watsonImageScaleX = scale;
  }

  public void setWatsonImageScaleY(Double scale) {
    this.watsonImageScaleY = scale;
  }

  public Boolean getWatsonFlipX() {
    return watsonFlipX;
  }

  public Boolean getWatsonFlipY() {
    return watsonFlipY;
  }

  public void setWatsonFlipX(Boolean flip) {
    this.watsonFlipX = flip;
  }

  public void setWatsonFlipY(Boolean flip) {
    this.watsonFlipY = flip;
  }

  public Double getWatsonRotation() {
    return watsonRotation;
  }

  public void setWatsonRotation(Double degrees) {
    this.watsonRotation = degrees;
  }

  public Double getWatsonLabelDX() {
    return watsonLabelDX;
  }

  public Double getWatsonLabelDY() {
    return watsonLabelDY;
  }

  public void setWatsonLabelOffset(Double dx, Double dy) {
    this.watsonLabelDX = dx;
    this.watsonLabelDY = dy;
  }

  private static double clampUnit(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }

  /** Primary-language room description (becomes a {@code roomDetails} entry on export). */
  public String getDescription() {
    return description.get();
  }

  public void setDescription(String description) {
    this.description.set(description);
  }

  /** Full per-language room description, for the Localization tab. */
  public LocalizedText descriptionText() {
    return description;
  }

  /** Per-language room Display Name, for the Localization tab (falls back to the Universal name). */
  public LocalizedText displayNameText() {
    return displayName;
  }

  /** Direction → neighbouring room, read-only. Mutated only through {@link CaseDraft}. */
  public Map<String, RoomDraft> getNeighbors() {
    return Collections.unmodifiableMap(neighbors);
  }

  /** Creates an object with the given name, places it in this room, and returns it. */
  public ObjectDraft addObject(String name) {
    ObjectDraft object = new ObjectDraft(name);
    objects.add(object);
    return object;
  }

  /** The objects placed in this room, in declaration order (read-only). */
  public List<ObjectDraft> getObjects() {
    return Collections.unmodifiableList(objects);
  }

  public void removeObject(ObjectDraft object) {
    objects.remove(object);
  }

  void putNeighbor(String direction, RoomDraft room) {
    neighbors.put(direction.toLowerCase(), room);
  }

  /** Removes every direction that currently points at {@code room}. */
  void removeNeighborsTo(RoomDraft room) {
    neighbors.values().removeIf(neighbor -> neighbor == room);
  }
}

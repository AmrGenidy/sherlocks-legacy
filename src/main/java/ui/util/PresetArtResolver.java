package ui.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps a missing-image entity (room, object, suspect, Watson, MP partner) to a deterministic
 * hand-made Victorian-engraving <em>preset</em> on the classpath, per {@code
 * docs/PRESET_ART_WIRING.md}.
 *
 * <p>Presets live under {@code /images/presets/{rooms,objects,characters}/} and exist so any case —
 * including community cases authored without art — looks intentional and on-theme rather than
 * showing the procedural {@link PlaceholderImageGenerator} output.
 *
 * <p><b>Deterministic and stable.</b> The same id/name always maps to the same preset across
 * launches and across both players in multiplayer: selection is a keyword match (objects, rooms) or
 * {@link Math#floorMod(int, int)} of the JVM-specified {@link String#hashCode()} — never random.
 *
 * <p>This class is pure (no JavaFX) so the selection rules can be unit-tested directly; the actual
 * image load + the authored-image-wins resolution order live in {@link ImageManager}.
 */
public final class PresetArtResolver {

  private PresetArtResolver() {}

  private static final String ROOMS = "/images/presets/rooms/";
  private static final String OBJECTS = "/images/presets/objects/";
  private static final String CHARACTERS = "/images/presets/characters/";

  /** The room preset ids, in canonical order — also the Case Maker picker's source of truth. */
  public static final List<String> ROOM_PRESET_IDS =
      List.of(
          "room_study",
          "room_parlour",
          "room_hallway",
          "room_bedroom",
          "room_dining",
          "room_kitchen");

  /** The object preset ids, in the order used by the no-keyword hash fallback. */
  private static final List<String> OBJECT_PRESET_IDS =
      List.of(
          "obj_letter",
          "obj_key",
          "obj_bottle",
          "obj_candlestick",
          "obj_book",
          "obj_pocket_watch",
          "obj_glove",
          "obj_dagger",
          "obj_magnifying_glass",
          "obj_pistol",
          "obj_ring",
          "obj_photograph",
          "obj_vial",
          "obj_rope",
          "obj_quill_inkwell",
          "obj_pipe");

  private static final int SUSPECT_COUNT = 12;

  // Room keyword → preset id, most-specific first so compound names resolve sensibly:
  // "Dining Hall" hits dining before the generic hall*, "Great Hall" still falls through to
  // hallway.
  private static final Map<String, String> ROOM_KEYWORDS = new LinkedHashMap<>();

  static {
    putAll(ROOM_KEYWORDS, "room_bedroom", "bedroom", "bed", "chamber", "dormitory");
    putAll(ROOM_KEYWORDS, "room_dining", "dining", "dinner", "banquet");
    putAll(ROOM_KEYWORDS, "room_kitchen", "kitchen", "scullery", "pantry", "galley");
    putAll(ROOM_KEYWORDS, "room_study", "library", "study", "office");
    putAll(ROOM_KEYWORDS, "room_parlour", "parlour", "parlor", "drawing", "lounge", "sitting");
    putAll(ROOM_KEYWORDS, "room_hallway", "hall", "corridor", "landing");
  }

  // Object keyword → preset id (first hit wins), per docs/PRESET_ART_WIRING.md.
  private static final Map<String, String> OBJECT_KEYWORDS = new LinkedHashMap<>();

  static {
    putAll(OBJECT_KEYWORDS, "obj_letter", "letter", "note", "envelope");
    putAll(OBJECT_KEYWORDS, "obj_key", "key");
    putAll(OBJECT_KEYWORDS, "obj_bottle", "bottle", "poison");
    putAll(OBJECT_KEYWORDS, "obj_candlestick", "candle", "candlestick");
    putAll(OBJECT_KEYWORDS, "obj_book", "book", "journal", "diary", "ledger");
    putAll(OBJECT_KEYWORDS, "obj_pocket_watch", "watch", "clock");
    putAll(OBJECT_KEYWORDS, "obj_glove", "glove");
    putAll(OBJECT_KEYWORDS, "obj_dagger", "knife", "dagger", "blade");
    putAll(OBJECT_KEYWORDS, "obj_magnifying_glass", "magnif", "lens");
    putAll(OBJECT_KEYWORDS, "obj_pistol", "pistol", "gun", "revolver");
    putAll(OBJECT_KEYWORDS, "obj_ring", "ring");
    putAll(OBJECT_KEYWORDS, "obj_photograph", "photo", "photograph", "picture");
    putAll(OBJECT_KEYWORDS, "obj_vial", "vial", "flask");
    putAll(OBJECT_KEYWORDS, "obj_rope", "rope", "cord");
    putAll(OBJECT_KEYWORDS, "obj_quill_inkwell", "pen", "quill", "ink");
    putAll(OBJECT_KEYWORDS, "obj_pipe", "pipe");
  }

  private static void putAll(Map<String, String> map, String presetId, String... keywords) {
    for (String kw : keywords) {
      map.put(kw, presetId);
    }
  }

  /** Classpath path of the room preset for {@code roomName} (keyword, else deterministic hash). */
  public static String roomPreset(String roomName) {
    String hit = keyword(ROOM_KEYWORDS, roomName);
    String id =
        hit != null ? hit : ROOM_PRESET_IDS.get(stableIndex(roomName, ROOM_PRESET_IDS.size()));
    return ROOMS + id + ".png";
  }

  /** Classpath path for a room preset id (Case Maker picker). */
  public static String roomPresetPath(String roomPresetId) {
    return ROOMS + roomPresetId + ".png";
  }

  /** Classpath path of the object preset for {@code objectId}/{@code objectName}. */
  public static String objectPreset(String objectId, String objectName) {
    String combined = ((nullToEmpty(objectId) + " " + nullToEmpty(objectName))).trim();
    String hit = keyword(OBJECT_KEYWORDS, combined);
    String key = objectId != null && !objectId.isBlank() ? objectId : objectName;
    String id =
        hit != null ? hit : OBJECT_PRESET_IDS.get(stableIndex(key, OBJECT_PRESET_IDS.size()));
    return OBJECTS + id + ".png";
  }

  /** Classpath path of the suspect portrait preset for {@code suspectId} (char_suspect_01..12). */
  public static String suspectPreset(String suspectId) {
    int n = stableIndex(suspectId, SUSPECT_COUNT) + 1;
    return CHARACTERS + String.format("char_suspect_%02d", n) + ".png";
  }

  /** Watson's portrait preset (no per-case Watson art). */
  public static String watsonPreset() {
    return CHARACTERS + "char_watson.png";
  }

  /** The multiplayer partner / guest avatar preset (petrol-framed). */
  public static String partnerPreset() {
    return CHARACTERS + "char_partner.png";
  }

  /** First keyword found as a substring of the lowercased text, or null. */
  private static String keyword(Map<String, String> keywords, String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    String lower = text.toLowerCase();
    for (Map.Entry<String, String> e : keywords.entrySet()) {
      if (lower.contains(e.getKey())) {
        return e.getValue();
      }
    }
    return null;
  }

  /** A stable index in {@code [0, modulus)} from a key's hash; empty/null keys map to 0. */
  private static int stableIndex(String key, int modulus) {
    if (key == null || key.isBlank()) {
      return 0;
    }
    return Math.floorMod(key.hashCode(), modulus);
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}

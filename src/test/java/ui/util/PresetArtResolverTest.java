package ui.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import extractors.ResourceResolver;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/**
 * Validator-style + unit checks for {@link PresetArtResolver}: every returned preset path must
 * resolve on the classpath (the same {@link ResourceResolver} the runtime loader uses), selection
 * must be deterministic/stable, and the keyword maps must hit the documented presets
 * (docs/PRESET_ART_WIRING.md).
 */
public class PresetArtResolverTest {

  private static void assertResolves(String path) {
    assertTrue(path + " must resolve on the classpath", ResourceResolver.resolves(path, null));
  }

  @Test
  public void everyRoomPresetIdResolves() {
    for (String id : PresetArtResolver.ROOM_PRESET_IDS) {
      assertResolves(PresetArtResolver.roomPresetPath(id));
    }
    assertEquals("expected the full 6-room set", 6, PresetArtResolver.ROOM_PRESET_IDS.size());
  }

  @Test
  public void watsonAndPartnerResolve() {
    assertResolves(PresetArtResolver.watsonPreset());
    assertResolves(PresetArtResolver.partnerPreset());
  }

  @Test
  public void allTwelveSuspectPresetsResolveAndStayInRange() {
    Set<String> seen = new HashSet<>();
    // A spread of ids should cover the 1..12 range; every produced path must resolve.
    for (int i = 0; i < 200; i++) {
      String path = PresetArtResolver.suspectPreset("suspect-" + i);
      assertResolves(path);
      assertTrue("suspect preset should be char_suspect_NN", path.contains("/char_suspect_"));
      seen.add(path);
    }
    assertTrue("a spread of ids should reach several of the 12 portraits", seen.size() >= 8);
  }

  @Test
  public void everyObjectKeywordResolves() {
    String[] names = {
      "torn letter",
      "brass key",
      "wine bottle",
      "silver candlestick",
      "leather book",
      "pocket watch",
      "kid glove",
      "bone dagger",
      "magnifying glass",
      "duelling pistol",
      "signet ring",
      "framed photograph",
      "glass vial",
      "coil of rope",
      "quill pen",
      "briar pipe"
    };
    for (String name : names) {
      assertResolves(PresetArtResolver.objectPreset(null, name));
    }
  }

  @Test
  public void roomKeywordMapsHitExpectedPresets() {
    assertEquals(
        "/images/presets/rooms/room_study.png", PresetArtResolver.roomPreset("The Library"));
    assertEquals(
        "/images/presets/rooms/room_parlour.png", PresetArtResolver.roomPreset("Drawing Room"));
    assertEquals(
        "/images/presets/rooms/room_hallway.png", PresetArtResolver.roomPreset("Great Hall"));
    assertEquals(
        "/images/presets/rooms/room_bedroom.png", PresetArtResolver.roomPreset("Master Bedroom"));
    assertEquals(
        "/images/presets/rooms/room_dining.png", PresetArtResolver.roomPreset("Dining Room"));
    assertEquals(
        "/images/presets/rooms/room_kitchen.png", PresetArtResolver.roomPreset("Scullery"));
  }

  @Test
  public void diningHallPrefersDiningOverGenericHall() {
    // Most-specific-first ordering: a "Dining Hall" is a dining room, not a corridor.
    assertEquals(
        "/images/presets/rooms/room_dining.png", PresetArtResolver.roomPreset("Dining Hall"));
  }

  @Test
  public void objectKeywordMapsHitExpectedPresets() {
    assertEquals(
        "/images/presets/objects/obj_letter.png",
        PresetArtResolver.objectPreset("note01", "A Note"));
    assertEquals(
        "/images/presets/objects/obj_dagger.png",
        PresetArtResolver.objectPreset("weapon", "bloodied knife"));
    assertEquals(
        "/images/presets/objects/obj_pocket_watch.png",
        PresetArtResolver.objectPreset(null, "grandfather clock"));
  }

  @Test
  public void selectionIsDeterministicAndStable() {
    assertEquals(
        PresetArtResolver.roomPreset("Conservatory"), PresetArtResolver.roomPreset("Conservatory"));
    assertEquals(PresetArtResolver.suspectPreset("xyz"), PresetArtResolver.suspectPreset("xyz"));
    assertEquals(
        PresetArtResolver.objectPreset("o1", "trinket"),
        PresetArtResolver.objectPreset("o1", "trinket"));
  }

  @Test
  public void noKeywordRoomsSpreadAcrossAllSixAndResolve() {
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 100; i++) {
      String path = PresetArtResolver.roomPreset("Annex " + i); // no room keyword in the name
      assertResolves(path);
      seen.add(path);
    }
    assertTrue("no-keyword rooms should spread across multiple presets", seen.size() >= 4);
  }
}

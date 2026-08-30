package ui.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

/**
 * Local player-profile persistence: the display name + avatar id survive a "restart" (write with
 * one store, reload with a fresh store on the same path), parent dirs are created, and the store is
 * best-effort — a missing or corrupt file reads back as defaults and never throws.
 */
public class PlayerProfileStoreTest {

  @Test
  public void persistsDisplayNameAndAvatarAcrossRestart() throws Exception {
    Path file = Files.createTempDirectory("profile").resolve("nested").resolve("profile.json");
    new PlayerProfileStore(file).save(new PlayerProfile("Irene Adler", "char_suspect_03"));

    // Fresh store instance on the same path == a relaunch reading the persisted file.
    PlayerProfile reloaded = new PlayerProfileStore(file).load();
    assertEquals("Irene Adler", reloaded.displayName());
    assertEquals("char_suspect_03", reloaded.avatarId());
    assertTrue(reloaded.hasDisplayName());
  }

  @Test
  public void missingFileLoadsDefaultsAndNeverThrows() {
    PlayerProfile loaded =
        new PlayerProfileStore(Paths.get("Z:/definitely/not/here/profile.json")).load();
    assertEquals(PlayerProfile.DEFAULT_AVATAR_ID, loaded.avatarId());
    assertFalse(loaded.hasDisplayName());
  }

  @Test
  public void corruptFileLoadsDefaults() throws Exception {
    Path file = Files.createTempDirectory("profile").resolve("profile.json");
    Files.writeString(file, "{ not valid json ");
    PlayerProfile loaded = new PlayerProfileStore(file).load();
    assertEquals(PlayerProfile.DEFAULT_AVATAR_ID, loaded.avatarId());
    assertFalse(loaded.hasDisplayName());
  }

  @Test
  public void blankOrMissingAvatarFallsBackToDefault() throws Exception {
    Path file = Files.createTempDirectory("profile").resolve("profile.json");
    // Hand-write a profile with no avatar — loading must fall back to the default preset.
    Files.writeString(file, "{\"displayName\":\"Watson\"}");
    PlayerProfile loaded = new PlayerProfileStore(file).load();
    assertEquals("Watson", loaded.displayName());
    assertEquals(PlayerProfile.DEFAULT_AVATAR_ID, loaded.avatarId());
  }

  @Test
  public void copyWithersPreserveTheOtherField() {
    PlayerProfile base = new PlayerProfile("Sherlock", "char_suspect_01");
    assertEquals("char_suspect_01", base.withDisplayName("Mycroft").avatarId());
    assertEquals("Sherlock", base.withAvatarId("char_watson").displayName());
  }
}

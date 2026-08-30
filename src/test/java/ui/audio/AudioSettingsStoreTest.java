package ui.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

/**
 * Local audio-settings persistence: round-trips volume + mute, creates parent dirs, and is
 * best-effort — a missing or corrupt file reads back as defaults and never throws.
 */
public class AudioSettingsStoreTest {

  private static final double EPS = 1e-9;

  @Test
  public void roundTripsVolumeAndMuteAcrossInstances() throws Exception {
    Path file = Files.createTempDirectory("audio").resolve("nested").resolve("audio.json");
    AudioSettingsStore store = new AudioSettingsStore(file);

    store.save(new AudioSettings(0.42, true));

    AudioSettings reloaded = new AudioSettingsStore(file).load();
    assertEquals(0.42, reloaded.volume(), EPS);
    assertTrue(reloaded.muted());
  }

  @Test
  public void missingFileLoadsDefaultsAndNeverThrows() {
    AudioSettingsStore store =
        new AudioSettingsStore(Paths.get("Z:/definitely/not/here/audio.json"));
    AudioSettings loaded = store.load();
    assertEquals(AudioSettings.DEFAULT_VOLUME, loaded.volume(), EPS);
    assertFalse(loaded.muted());
  }

  @Test
  public void corruptFileLoadsDefaults() throws Exception {
    Path file = Files.createTempDirectory("audio").resolve("audio.json");
    Files.writeString(file, "{ this is not valid json ");
    AudioSettings loaded = new AudioSettingsStore(file).load();
    assertEquals(AudioSettings.DEFAULT_VOLUME, loaded.volume(), EPS);
  }

  @Test
  public void persistedVolumeIsClampedOnReload() throws Exception {
    Path file = Files.createTempDirectory("audio").resolve("audio.json");
    // Hand-write an out-of-range value; loading must clamp it via AudioSettings.
    Files.writeString(file, "{\"volume\":5.0,\"muted\":false}");
    assertEquals(1.0, new AudioSettingsStore(file).load().volume(), EPS);
  }
}

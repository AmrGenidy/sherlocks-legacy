package ui.audio;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local, best-effort persistence of {@link AudioSettings} (.scratch/per-case-soundtrack issue 03).
 * Like the tutorial progress store, this is the "optional local profile file" allowed for offline
 * single-player (roadmap Hard Constraint 1): it lives under the user's home directory, all IO is
 * swallowed, and a read/write failure never blocks play. A missing or corrupt file reads back as
 * {@link AudioSettings#defaults()}.
 */
public class AudioSettingsStore {

  private static final Logger logger = LoggerFactory.getLogger(AudioSettingsStore.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final Path file;

  public AudioSettingsStore() {
    this(defaultPath());
  }

  /** Test seam: point the store at an arbitrary file (e.g. a temp dir). */
  public AudioSettingsStore(Path file) {
    this.file = file;
  }

  private static Path defaultPath() {
    return Paths.get(System.getProperty("user.home"), ".sherlocks-legacy", "audio-settings.json");
  }

  /** Loads the settings, returning defaults on any read failure (best-effort). */
  public AudioSettings load() {
    if (file == null || !Files.exists(file)) {
      return AudioSettings.defaults();
    }
    try {
      AudioSettings settings = MAPPER.readValue(file.toFile(), AudioSettings.class);
      return settings == null ? AudioSettings.defaults() : settings;
    } catch (IOException | RuntimeException e) {
      logger.warn("Could not read audio settings from {}: {}", file, e.toString());
      return AudioSettings.defaults();
    }
  }

  /** Persists the settings. All IO is best-effort: a failure is logged and swallowed. */
  public void save(AudioSettings settings) {
    if (file == null || settings == null) {
      return;
    }
    try {
      if (file.getParent() != null) {
        Files.createDirectories(file.getParent());
      }
      MAPPER.writeValue(file.toFile(), settings);
    } catch (IOException | RuntimeException e) {
      logger.warn("Could not persist audio settings to {}: {}", file, e.toString());
    }
  }
}

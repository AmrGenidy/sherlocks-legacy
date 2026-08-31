package ui.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local, best-effort persistence of {@link AppSettings} (MENU_DESIGN #6), mirroring {@code
 * AudioSettingsStore} / {@code TutorialProgressStore}: an optional file under the user's home
 * directory (roadmap Hard Constraint 1). All IO is swallowed — a read/write failure never blocks
 * play, and a missing or corrupt file reads back as {@link AppSettings#defaults()}.
 */
public class AppSettingsStore {

  private static final Logger logger = LoggerFactory.getLogger(AppSettingsStore.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final Path file;

  public AppSettingsStore() {
    this(defaultPath());
  }

  /** Test seam: point the store at an arbitrary file (e.g. a temp dir). */
  public AppSettingsStore(Path file) {
    this.file = file;
  }

  private static Path defaultPath() {
    return Paths.get(System.getProperty("user.home"), ".sherlocks-legacy", "app-settings.json");
  }

  /** Loads the settings, returning defaults on any read failure (best-effort). */
  public AppSettings load() {
    if (file == null || !Files.exists(file)) {
      return AppSettings.defaults();
    }
    try {
      AppSettings settings = MAPPER.readValue(file.toFile(), AppSettings.class);
      return settings == null ? AppSettings.defaults() : settings;
    } catch (IOException | RuntimeException e) {
      logger.warn("Could not read app settings from {}: {}", file, e.toString());
      return AppSettings.defaults();
    }
  }

  /** Persists the settings. All IO is best-effort: a failure is logged and swallowed. */
  public void save(AppSettings settings) {
    if (file == null || settings == null) {
      return;
    }
    try {
      if (file.getParent() != null) {
        Files.createDirectories(file.getParent());
      }
      MAPPER.writeValue(file.toFile(), settings);
    } catch (IOException | RuntimeException e) {
      logger.warn("Could not persist app settings to {}: {}", file, e.toString());
    }
  }
}

package ui.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local, best-effort persistence of the single {@link PlayerProfile} (see {@code
 * docs/SAVE_AND_PROFILE.md}), mirroring {@link AppSettingsStore} / {@code AudioSettingsStore}: an
 * optional file under the user's home directory (roadmap Hard Constraint 1). All IO is swallowed —
 * a read/write failure never blocks play, and a missing or corrupt file reads back as {@link
 * PlayerProfile#defaults()}.
 *
 * <p>This is local persistence, not the wire protocol, so it uses a plain {@link ObjectMapper} with
 * no polymorphic default typing.
 */
public class PlayerProfileStore {

  private static final Logger logger = LoggerFactory.getLogger(PlayerProfileStore.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final Path file;

  public PlayerProfileStore() {
    this(defaultPath());
  }

  /** Test seam: point the store at an arbitrary file (e.g. a temp dir). */
  public PlayerProfileStore(Path file) {
    this.file = file;
  }

  private static Path defaultPath() {
    return Paths.get(System.getProperty("user.home"), ".sherlocks-legacy", "profile.json");
  }

  /** Loads the profile, returning defaults on any read failure (best-effort). */
  public PlayerProfile load() {
    if (file == null || !Files.exists(file)) {
      return PlayerProfile.defaults();
    }
    try {
      PlayerProfile profile = MAPPER.readValue(file.toFile(), PlayerProfile.class);
      return profile == null ? PlayerProfile.defaults() : profile;
    } catch (IOException | RuntimeException e) {
      logger.warn("Could not read player profile from {}: {}", file, e.toString());
      return PlayerProfile.defaults();
    }
  }

  /** Persists the profile. All IO is best-effort: a failure is logged and swallowed. */
  public void save(PlayerProfile profile) {
    if (file == null || profile == null) {
      return;
    }
    try {
      if (file.getParent() != null) {
        Files.createDirectories(file.getParent());
      }
      MAPPER.writeValue(file.toFile(), profile);
    } catch (IOException | RuntimeException e) {
      logger.warn("Could not persist player profile to {}: {}", file, e.toString());
    }
  }
}

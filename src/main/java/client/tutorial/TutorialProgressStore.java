package client.tutorial;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local, best-effort record of which tutorials a player has completed, so returning players are not
 * re-prompted. This is the "optional local profile file" allowed for offline single-player (see
 * roadmap Hard Constraint 1): it lives under the user's home directory, all IO is swallowed, and a
 * read/write failure never blocks play.
 *
 * <p>Format is a plain JSON array of completed tutorial ids — trivial to inspect or delete by hand.
 */
public class TutorialProgressStore {

  private static final Logger logger = LoggerFactory.getLogger(TutorialProgressStore.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final Path file;

  public TutorialProgressStore() {
    this(defaultPath());
  }

  /** Test seam: point the store at an arbitrary file (e.g. a temp dir). */
  public TutorialProgressStore(Path file) {
    this.file = file;
  }

  private static Path defaultPath() {
    return Paths.get(
        System.getProperty("user.home"), ".sherlocks-legacy", "tutorial-progress.json");
  }

  /** All completed tutorial ids. Returns an empty set on any read failure (best-effort). */
  public Set<String> completed() {
    if (file == null || !Files.exists(file)) {
      return new LinkedHashSet<>();
    }
    try {
      Set<String> ids =
          MAPPER.readValue(file.toFile(), new TypeReference<LinkedHashSet<String>>() {});
      return ids == null ? new LinkedHashSet<>() : ids;
    } catch (IOException | RuntimeException e) {
      logger.warn("Could not read tutorial progress from {}: {}", file, e.toString());
      return new LinkedHashSet<>();
    }
  }

  public boolean isCompleted(String tutorialId) {
    return tutorialId != null && completed().contains(tutorialId);
  }

  /**
   * Records {@code tutorialId} as completed. No-op if already recorded. All IO is best-effort: a
   * failure is logged and swallowed so it can never interrupt the game.
   */
  public void markCompleted(String tutorialId) {
    if (tutorialId == null || tutorialId.isBlank() || file == null) {
      return;
    }
    Set<String> ids = completed();
    if (!ids.add(tutorialId)) {
      return; // already present
    }
    try {
      if (file.getParent() != null) {
        Files.createDirectories(file.getParent());
      }
      MAPPER.writeValue(file.toFile(), ids);
    } catch (IOException | RuntimeException e) {
      logger.warn("Could not persist tutorial progress to {}: {}", file, e.toString());
    }
  }
}

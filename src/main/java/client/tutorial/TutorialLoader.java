package client.tutorial;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads and parses {@code tutorials.json} into a {@link TutorialManifest} (image map + scripts).
 */
public class TutorialLoader {

  private static final Logger logger = LoggerFactory.getLogger(TutorialLoader.class);
  private static final String TUTORIALS_FILE = "tutorials.json";

  private final TutorialManifest manifest;

  public TutorialLoader() {
    this.manifest = loadManifest();
  }

  /** Test/seam constructor: build a loader directly over an already-parsed manifest. */
  TutorialLoader(TutorialManifest manifest) {
    this.manifest = manifest == null ? new TutorialManifest() : manifest;
  }

  /** Parses a manifest from a JSON string (test seam; mirrors the production parse path). */
  static TutorialLoader fromJson(String json) throws IOException {
    return new TutorialLoader(new ObjectMapper().readValue(json, TutorialManifest.class));
  }

  private TutorialManifest loadManifest() {
    try {
      ObjectMapper mapper = new ObjectMapper();

      // Try the classpath first (works inside the packaged JAR).
      try (InputStream is = getClass().getClassLoader().getResourceAsStream(TUTORIALS_FILE)) {
        if (is != null) {
          TutorialManifest parsed = mapper.readValue(is, TutorialManifest.class);
          logger.info("Loaded {} tutorials from classpath", parsed.getTutorials().size());
          return parsed;
        }
      }

      // Fall back to the source tree when running from an IDE without resources on the classpath.
      File file = new File("src/main/resources/" + TUTORIALS_FILE);
      if (file.exists()) {
        TutorialManifest parsed = mapper.readValue(file, TutorialManifest.class);
        logger.info("Loaded {} tutorials from file system", parsed.getTutorials().size());
        return parsed;
      }

      logger.warn("tutorials.json not found, tutorial system will be disabled");
    } catch (IOException e) {
      logger.error("Failed to load tutorials.json", e);
    }
    return new TutorialManifest();
  }

  public Map<String, TutorialScript> getTutorials() {
    return Collections.unmodifiableMap(manifest.getTutorials());
  }

  public TutorialScript getTutorial(String id) {
    return manifest.getTutorials().get(id);
  }
}

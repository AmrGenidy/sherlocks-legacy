package client.tutorial;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.HashMap;
import java.util.Map;

/**
 * Root of {@code tutorials.json}.
 *
 * <p>One section: {@code tutorials} — tutorial id → {@link TutorialScript}. Tutorial steps carry no
 * illustration; each overlay step is text + an optional pointer arrow only.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TutorialManifest {

  private Map<String, TutorialScript> tutorials = new HashMap<>();

  public Map<String, TutorialScript> getTutorials() {
    return tutorials;
  }

  public void setTutorials(Map<String, TutorialScript> tutorials) {
    this.tutorials = tutorials == null ? new HashMap<>() : tutorials;
  }
}

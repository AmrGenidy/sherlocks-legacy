package client.tutorial;

import static org.junit.Assert.*;

import org.junit.Test;

/** Manifest parsing: tutorials and step fields (tutorial steps carry no illustration). */
public class TutorialLoaderTest {

  @Test
  public void parsesScripts() throws Exception {
    String json =
        "{"
            + "\"tutorials\": {\"t1\": {"
            + "  \"startRoom\": \"Study\","
            + "  \"steps\": ["
            + "    {\"type\": \"SHOW_OVERLAY\", \"textKey\": \"k1\", \"arrowTarget\": \"NONE\"},"
            + "    {\"type\": \"AWAIT_COMMAND\", \"expectedCommand\": \"look\"},"
            + "    {\"type\": \"END\"}"
            + "  ]}}"
            + "}";

    TutorialLoader loader = TutorialLoader.fromJson(json);

    TutorialScript script = loader.getTutorial("t1");
    assertNotNull(script);
    assertEquals("Study", script.getStartRoom());
    TutorialStep first = script.getSteps().get(0);
    assertEquals("SHOW_OVERLAY", first.getType());
    assertEquals("k1", first.getTextKey());
    assertEquals("NONE", first.getArrowTarget());
    assertEquals("look", script.getSteps().get(1).getExpectedCommand());
  }

  @Test
  public void productionManifestLoadsFromClasspath() {
    TutorialLoader loader = new TutorialLoader();
    assertFalse("tutorials.json should be on the classpath", loader.getTutorials().isEmpty());
    assertNotNull(
        "the practice case tutorial must be present", loader.getTutorial("practice_case"));
  }
}

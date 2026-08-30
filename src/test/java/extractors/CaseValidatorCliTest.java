package extractors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Locks the CLI's exit-code contract (0 = clean, 1 = errors, 2 = bad usage). */
public class CaseValidatorCliTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private static final String VALID =
      """
      {
        "universal_title": "CliOk",
        "startingRoom": "Hall",
        "rooms": [{"name":"Hall","neighbors":{}}],
        "localizations": {
          "en": {
            "title":"CliOk","invitation":"Hi",
            "roomDetails":[{"name":"Hall","description":"x"}],
            "objectDetails":[],
            "final_exam":{"questions":[{"question_prompt":"q","slots":{"s":{"slot_id":"s","choices":[{"choice_id":"c","choice_text":"t"}]}},"correct_combination":{"s":"c"}}]}
          }
        }
      }
      """;

  private static final String BROKEN =
      """
      {
        "universal_title": "CliBroken",
        "startingRoom": "Ghost",
        "rooms": [{"name":"Hall","neighbors":{}}],
        "localizations": {"en": {"title":"CliBroken","invitation":"Hi","final_exam":{"questions":[]}}}
      }
      """;

  private Path write(String name, String json) throws IOException {
    Path file = tmp.newFile(name).toPath();
    Files.writeString(file, json);
    return file;
  }

  @Test
  public void cleanCaseExitsZero() throws IOException {
    Path file = write("ok.json", VALID);
    StringBuilder out = new StringBuilder();
    assertEquals(0, CaseValidatorCli.run(new String[] {file.toString()}, out));
    assertTrue(out.toString().contains("CliOk"));
  }

  @Test
  public void brokenCaseExitsNonZero() throws IOException {
    Path file = write("broken.json", BROKEN);
    StringBuilder out = new StringBuilder();
    assertEquals(1, CaseValidatorCli.run(new String[] {file.toString()}, out));
    assertTrue(out.toString().contains("error(s)"));
  }

  @Test
  public void noArgumentsExitsTwo() {
    assertEquals(2, CaseValidatorCli.run(new String[] {}, new StringBuilder()));
  }
}

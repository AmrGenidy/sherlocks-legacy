package extractors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Data guard for the shipped cases' authored art (GUI G3-fix). Every {@code imagePath} in a bundled
 * case must be non-blank and resolve via the same {@link ResourceResolver} the runtime loader uses
 * — so a case that ships images actually shows them, and the engraving placeholder stays a genuine
 * no-image fallback. This is the regression that catches a wholesale-blank like the one G3
 * introduced (every room/object/suspect silently swapped for a placeholder).
 */
public class BundledCaseImageResolutionTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** The shipped case files that are fully illustrated and must keep their art. */
  private static final String[] CASE_FILES = {
    "cases/sapphire_case.json",
    "cases/Blackwood.json",
    "cases/the-last-note-at-the-blue-room.json",
    // Self-contained bundled cases (DEC-3): JSON + images under cases/<slug>/.
    "src/main/resources/cases/sapphire_case/sapphire_case.json",
    "src/main/resources/cases/a_bitter_cup/a_bitter_cup.json",
    "src/main/resources/cases/an_invitation_to_judgement/an_invitation_to_judgement.json",
    "src/main/resources/tutorial_practice_case.json",
  };

  @Test
  public void everyAuthoredImagePathInBundledCasesResolves() throws Exception {
    for (String caseFile : CASE_FILES) {
      Path path = Paths.get(caseFile);
      assertTrue("Precondition: bundled case exists: " + caseFile, Files.exists(path));
      JsonNode root = MAPPER.readTree(path.toFile());

      List<String> imagePaths = new ArrayList<>();
      collectImagePaths(root, imagePaths);
      assertFalse("Case should declare image slots: " + caseFile, imagePaths.isEmpty());

      for (String imagePath : imagePaths) {
        assertTrue(
            "Authored imagePath must not be blank in shipped case "
                + caseFile
                + " (a blanked slot makes the engraving placeholder REPLACE real art)",
            imagePath != null && !imagePath.isBlank());
        assertTrue(
            "imagePath '"
                + imagePath
                + "' in "
                + caseFile
                + " must resolve (classpath or case dir)",
            ResourceResolver.resolves(imagePath, path.getParent()));
      }
    }
  }

  /** Recursively gathers every {@code "imagePath"} string value anywhere in the tree. */
  private static void collectImagePaths(JsonNode node, List<String> out) {
    if (node == null) {
      return;
    }
    if (node.isObject()) {
      JsonNode image = node.get("imagePath");
      if (image != null && image.isTextual()) {
        out.add(image.asText());
      }
      node.fields().forEachRemaining(e -> collectImagePaths(e.getValue(), out));
    } else if (node.isArray()) {
      node.forEach(child -> collectImagePaths(child, out));
    }
  }
}

package extractors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import JsonDTO.CaseFile;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import extractors.CaseValidator.Issue;
import extractors.CaseValidator.Severity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;

/**
 * Content guard for the bundled cases in the tracked {@code cases/} directory
 * (.scratch/gui-localized-case-names): every localization of every shipped case must author a
 * per-language Display Name for every room, object, and suspect, so players in any supported
 * language see translated names. The {@link CaseValidator} emits a WARNING for each gap; this test
 * asserts there are none left in the bundled cases (a stricter, repo-content bar than the
 * warn-only-load policy the validator applies to arbitrary community cases).
 */
public class BundledCaseDisplayNameCoverageTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private static final String[] BUNDLED = {
    "Blackwood.json", "sapphire_case.json", "the-last-note-at-the-blue-room.json"
  };

  @Test
  public void bundledCasesHaveDisplayNamesInEveryLanguage() throws Exception {
    for (String fileName : BUNDLED) {
      Path file = Path.of("cases", fileName);
      assertTrue("bundled case is present: " + file, Files.isRegularFile(file));
      CaseFile caseFile = MAPPER.readValue(Files.readString(file), CaseFile.class);

      List<Issue> issues = CaseValidator.validate(caseFile, file.getParent()).issues();
      List<Issue> displayNameGaps =
          issues.stream()
              .filter(i -> i.severity() == Severity.WARNING)
              .filter(i -> i.message().contains("Display Name"))
              .toList();
      assertTrue(
          "bundled case " + fileName + " is missing Display Names: " + displayNameGaps,
          displayNameGaps.isEmpty());
      // And it must still be structurally valid (Display Names never introduce errors).
      assertFalse(
          "bundled case " + fileName + " has errors: " + CaseValidator.validate(caseFile).errors(),
          CaseValidator.validate(caseFile).hasErrors());
    }
  }
}

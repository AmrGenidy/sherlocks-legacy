package extractors;

import static org.junit.Assert.fail;

import JsonDTO.CaseFile;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import extractors.CaseValidator.Issue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * Build gate: every shipped case (bundled resources + the external {@code cases/} directory) must
 * pass {@link CaseValidator} with zero ERRORs. A structurally broken case shipped in the repo fails
 * the build here instead of breaking a player mid-game.
 *
 * <p>Image WARNINGs are printed for visibility but do not fail the build — broken image paths are
 * tracked separately under the {@code image-display-correctness} workstream.
 */
public class AllCasesValidationTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private static final List<Path> CASE_DIRECTORIES =
      List.of(Paths.get("src", "main", "resources", "cases"), Paths.get("cases"));

  @Test
  public void allShippedCasesAreStructurallyValid() throws IOException {
    List<Path> caseFiles = new ArrayList<>();
    for (Path dir : CASE_DIRECTORIES) {
      if (Files.isDirectory(dir)) {
        try (Stream<Path> stream = Files.list(dir)) {
          stream.filter(p -> p.toString().toLowerCase().endsWith(".json")).forEach(caseFiles::add);
        }
      }
    }

    if (caseFiles.isEmpty()) {
      fail("No case files found to validate under " + CASE_DIRECTORIES);
    }

    StringBuilder failures = new StringBuilder();
    for (Path file : caseFiles) {
      CaseFile caseFile;
      try {
        caseFile = MAPPER.readValue(file.toFile(), CaseFile.class);
      } catch (IOException e) {
        failures.append("\n  ").append(file).append(" — failed to parse: ").append(e.getMessage());
        continue;
      }
      caseFile.setSourcePath(file.toAbsolutePath().toString());
      CaseValidator.Report report = CaseValidator.validate(caseFile);

      for (Issue warning : report.warnings()) {
        System.out.println("[case-warning] " + file.getFileName() + ": " + warning);
      }
      if (report.hasErrors()) {
        failures.append("\n  ").append(file).append(" (").append(report.caseTitle()).append("):");
        for (Issue error : report.errors()) {
          failures.append("\n    - ").append(error);
        }
      }
    }

    if (failures.length() > 0) {
      fail("Shipped cases have validation errors:" + failures);
    }
  }
}

package extractors;

import JsonDTO.CaseFile;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Command-line front-end for {@link CaseValidator}, for case authors.
 *
 * <pre>
 *   java extractors.CaseValidatorCli &lt;case.json | directory&gt; [more...]
 * </pre>
 *
 * Prints a human-readable, file-grouped report and exits non-zero if any case has ERROR-level
 * violations. The validation core is exactly the same code path the game uses at load time.
 */
public final class CaseValidatorCli {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private CaseValidatorCli() {}

  public static void main(String[] args) {
    System.exit(run(args, System.out));
  }

  /** Validates the given paths, prints a report, and returns the process exit code. */
  static int run(String[] args, Appendable out) {
    if (args == null || args.length == 0) {
      println(out, "Usage: CaseValidatorCli <case.json | directory> [more...]");
      return 2;
    }

    List<Path> files = new ArrayList<>();
    for (String arg : args) {
      collectJson(Paths.get(arg), files, out);
    }
    if (files.isEmpty()) {
      println(out, "No .json case files found in: " + String.join(", ", args));
      return 2;
    }

    int totalErrors = 0;
    for (Path file : files) {
      CaseFile caseFile;
      try {
        caseFile = MAPPER.readValue(file.toFile(), CaseFile.class);
      } catch (IOException e) {
        println(out, file + ": FAILED TO PARSE — " + e.getMessage());
        totalErrors++;
        continue;
      }
      caseFile.setSourcePath(file.toAbsolutePath().toString());
      CaseValidator.Report report = CaseValidator.validate(caseFile);

      println(
          out,
          file
              + " — "
              + report.caseTitle()
              + ": "
              + report.errors().size()
              + " error(s), "
              + report.warnings().size()
              + " warning(s)");
      for (CaseValidator.Issue issue : report.issues()) {
        println(out, "   " + issue);
      }
      totalErrors += report.errors().size();
    }

    return totalErrors == 0 ? 0 : 1;
  }

  private static void collectJson(Path path, List<Path> into, Appendable out) {
    if (Files.isDirectory(path)) {
      try (Stream<Path> stream = Files.list(path)) {
        stream.filter(p -> p.toString().toLowerCase().endsWith(".json")).forEach(into::add);
      } catch (IOException e) {
        println(out, "Could not list directory '" + path + "': " + e.getMessage());
      }
    } else if (Files.isRegularFile(path)) {
      into.add(path);
    } else {
      println(out, "Not found: " + path);
    }
  }

  private static void println(Appendable out, String line) {
    try {
      out.append(line).append(System.lineSeparator());
    } catch (IOException ignored) {
      // Reporting failures to the report sink are not themselves fatal.
    }
  }
}

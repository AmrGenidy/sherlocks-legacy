package extractors;

import JsonDTO.CaseFile;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CaseLoader {

  private static final Logger logger = LoggerFactory.getLogger(CaseLoader.class);

  // Case JSON is untrusted (SECURITY_PLAN A/P0-2). StreamReadConstraints reject pathological input
  // —
  // gigantic strings, deep nesting, absurd numbers — during parsing, before a CaseFile is built, so
  // a hostile document cannot exhaust memory at the parser level. File-size and structural caps are
  // enforced separately (see MAX_CASE_FILE_BYTES checks below and CaseValidator).
  private static final ObjectMapper mapper = buildCaseMapper();

  private static ObjectMapper buildCaseMapper() {
    StreamReadConstraints constraints =
        StreamReadConstraints.builder()
            .maxStringLength(CaseLimits.MAX_JSON_STRING_LENGTH)
            .maxNestingDepth(CaseLimits.MAX_JSON_NESTING_DEPTH)
            .maxNumberLength(CaseLimits.MAX_JSON_NUMBER_LENGTH)
            .build();
    JsonFactory factory = JsonFactory.builder().streamReadConstraints(constraints).build();
    return new ObjectMapper(factory)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  private CaseLoader() {}

  public static List<CaseFile> loadCases(String directoryPath) {
    List<CaseFile> cases = new ArrayList<>();

    // --- 1. Built-in cases bundled on the classpath (inside the app jar, or target/classes in
    // dev).
    // This step must NEVER abort the method: on ANY failure we log and fall through so external
    // cases still load. In particular, FileSystems.newFileSystem on the app's OWN running jar
    // throws
    // FileSystemAlreadyExistsException (an unchecked RuntimeException) — loadBuiltInCases recovers
    // by
    // reusing the already-open FileSystem instead of letting it propagate (the packaged "no cases"
    // bug: the exception used to escape the narrow catch and abort loading before external cases
    // ran).
    try {
      loadBuiltInCases(directoryPath, cases);
    } catch (Exception e) {
      logger.warn(
          "Could not load built-in cases from resource directory '{}': {}. Continuing with external cases.",
          directoryPath,
          e.toString());
    }

    // --- 2. External cases from a "cases" folder BESIDE the application (created if missing),
    // scanned RECURSIVELY so cases living in their own subfolders — cases/<name>/<name>.json and
    // deeper — are all found. External cases override bundled ones of the same title (registerCase
    // replaces by title, so later loads win).
    Path externalDir = externalCasesDir(directoryPath);
    logger.info("Looking for external cases in: {}", externalDir.toAbsolutePath());
    if (Files.isDirectory(externalDir)) {
      int before = cases.size();
      loadCasesRecursively(externalDir, cases);
      logger.info("Registered {} case(s) after scanning '{}'.", cases.size() - before, externalDir);
    }

    logger.info("Finished loading cases. Found {} valid case(s).", cases.size());
    return cases;
  }

  /**
   * Loads the cases bundled on the classpath into {@code cases}. Runs from the packaged app jar (a
   * {@code jar:} URL — reuse the app's already-open jar FileSystem to dodge {@link
   * FileSystemAlreadyExistsException}) or from plain files in dev ({@code target/classes/cases}).
   * Recurses to any depth so self-contained {@code cases/<slug>/<slug>.json} folders are found.
   * Declared {@code throws Exception} so the caller's broad catch keeps a built-in failure from
   * aborting the whole load.
   */
  private static void loadBuiltInCases(String directoryPath, List<CaseFile> cases)
      throws Exception {
    URL resource = CaseLoader.class.getClassLoader().getResource(directoryPath);
    if (resource == null) {
      logger.info("No bundled cases directory '{}' on the classpath.", directoryPath);
      return;
    }
    URI uri = resource.toURI();
    int before = cases.size();
    if ("jar".equals(uri.getScheme())) {
      FileSystem fileSystem;
      boolean owns = false;
      try {
        fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap());
        owns = true; // we created it, so we must close it
      } catch (FileSystemAlreadyExistsException already) {
        // The running app jar already has an open FileSystem — reuse it, and do NOT close it (we
        // did not open it, and other code may still be using it).
        fileSystem = FileSystems.getFileSystem(uri);
      }
      try {
        walkJarCases(fileSystem.getPath("/" + directoryPath), cases);
      } finally {
        if (owns) {
          fileSystem.close();
        }
      }
    } else { // dev / IDE: bundled resources are plain files.
      loadCasesRecursively(Paths.get(uri), cases);
    }
    logger.info("Loaded {} built-in case(s) from '{}'.", cases.size() - before, directoryPath);
  }

  /**
   * The external cases directory the app reads and writes: a {@code <dirName>} folder BESIDE the
   * application. In a packaged jpackage app-image, {@code jpackage.app-path} is the launcher's own
   * path, so its parent is the install directory → {@code <install>/<dirName>}. In dev (property
   * absent) it falls back to a {@code <dirName>} folder relative to the working directory. The
   * directory is created if missing so imported cases always have a home. Never throws.
   */
  public static Path externalCasesDir(String dirName) {
    Path dir;
    String appPath = System.getProperty("jpackage.app-path");
    if (appPath != null && !appPath.isBlank()) {
      Path parent = Paths.get(appPath).getParent();
      dir = (parent != null ? parent : Paths.get("")).resolve(dirName);
    } else {
      dir = Paths.get(dirName);
    }
    try {
      Files.createDirectories(dir);
    } catch (IOException e) {
      logger.warn("Could not create external cases directory '{}': {}", dir, e.toString());
    }
    return dir;
  }

  /** Walks a directory inside the app jar (any depth) and loads every {@code .json} case. */
  private static void walkJarCases(Path casesPath, List<CaseFile> cases) {
    if (!Files.exists(casesPath)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(casesPath)) {
      paths
          .filter(path -> !Files.isDirectory(path) && isJson(path))
          .forEach(
              path -> {
                try {
                  if (exceedsSizeBudget(Files.size(path), path.toString())) {
                    return;
                  }
                  try (InputStream is = Files.newInputStream(path)) {
                    CaseFile caseFile = mapper.readValue(is, CaseFile.class);
                    registerCase(caseFile, cases, path.toString());
                  }
                } catch (IOException e) {
                  logger.error("Error reading or parsing bundled case '{}'", path, e);
                }
              });
    } catch (IOException e) {
      logger.error("Error walking bundled cases directory '{}'", casesPath, e);
    }
  }

  /**
   * Validates a parsed case and, if it is structurally sound, registers it (overriding any
   * already-loaded case with the same title). Cases with validation ERRORs are refused — they are
   * logged but never offered, so a broken case fails at load instead of mid-play. WARNINGs (e.g.
   * unresolved images) are logged but do not block the case.
   */
  private static void registerCase(CaseFile caseFile, List<CaseFile> cases, String sourceLabel) {
    if (caseFile == null) {
      return;
    }
    if (caseFile.getUniversalTitle() == null || caseFile.getUniversalTitle().isBlank()) {
      logger.warn("Skipping case from '{}': missing universal_title.", sourceLabel);
      return;
    }

    CaseValidator.Report report = CaseValidator.validate(caseFile);
    if (report.hasErrors()) {
      logger.warn(
          "Refusing to offer broken case '{}' from '{}' ({} validation error(s)):",
          report.caseTitle(),
          sourceLabel,
          report.errors().size());
      for (CaseValidator.Issue error : report.errors()) {
        logger.warn("   - {}", error);
      }
      return;
    }
    if (!report.warnings().isEmpty()) {
      logger.warn(
          "Case '{}' from '{}' loaded with {} warning(s):",
          report.caseTitle(),
          sourceLabel,
          report.warnings().size());
      for (CaseValidator.Issue warning : report.warnings()) {
        logger.warn("   - {}", warning);
      }
    }

    // Later loads override earlier ones with the same title (external overrides built-in).
    cases.removeIf(c -> c.getUniversalTitle().equalsIgnoreCase(caseFile.getUniversalTitle()));
    cases.add(caseFile);
    logger.info(
        "Loaded case '{}' from '{}'. startingInsightTokens={}",
        caseFile.getUniversalTitle(),
        sourceLabel,
        caseFile.getStartingInsightTokens());
  }

  /**
   * Loads every {@code .json} case anywhere under {@code dir} (recursively), so cases living in
   * their own subfolders — {@code cases/<name>/<name>.json} and deeper — are all found. Used for
   * external cases and for the dev/IDE built-in resource directory. Never throws.
   */
  private static void loadCasesRecursively(Path dir, List<CaseFile> cases) {
    if (!Files.isDirectory(dir)) {
      return;
    }
    try (Stream<Path> stream = Files.walk(dir)) {
      stream
          .filter(path -> !Files.isDirectory(path) && isJson(path))
          .forEach(path -> loadCaseJson(path, cases));
    } catch (IOException e) {
      logger.error("Error walking case directory '{}'", dir, e);
    }
  }

  private static boolean isJson(Path path) {
    return path.toString().toLowerCase().endsWith(".json");
  }

  private static void loadCaseJson(Path filePath, List<CaseFile> cases) {
    File file = filePath.toFile();
    try {
      // Refuse an oversized case before reading it into memory (SECURITY_PLAN A/P0-2).
      if (exceedsSizeBudget(file.length(), file.getAbsolutePath())) {
        return;
      }
      CaseFile caseFile = mapper.readValue(file, CaseFile.class);
      caseFile.setSourcePath(file.getAbsolutePath());
      registerCase(caseFile, cases, file.getAbsolutePath());
    } catch (IOException e) {
      logger.error("Error reading or parsing external case file '{}'", file.getName(), e);
    }
  }

  /**
   * True (and logs a refusal) when {@code sizeBytes} exceeds {@link
   * CaseLimits#MAX_CASE_FILE_BYTES}. An oversized case is skipped exactly like a schema-invalid one
   * — logged, never registered.
   */
  private static boolean exceedsSizeBudget(long sizeBytes, String sourceLabel) {
    if (sizeBytes > CaseLimits.MAX_CASE_FILE_BYTES) {
      logger.warn(
          "Refusing oversized case '{}': {} bytes exceeds the {} byte limit.",
          sourceLabel,
          sizeBytes,
          CaseLimits.MAX_CASE_FILE_BYTES);
      return true;
    }
    return false;
  }
}

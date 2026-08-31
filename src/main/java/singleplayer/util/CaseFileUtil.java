package singleplayer.util;

import extractors.CaseLoader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import singleplayer.SinglePlayerMain;

public class CaseFileUtil {

  private CaseFileUtil() {}

  /** The external cases directory beside the app (created if missing) — see {@link CaseLoader}. */
  private static Path casesDir() {
    return CaseLoader.externalCasesDir(SinglePlayerMain.CASES_DIRECTORY);
  }

  /**
   * Lists the available case {@code .json} files in the external cases directory, searched
   * RECURSIVELY so self-contained {@code cases/<slug>/<slug>.json} folders are included.
   *
   * @return a list of {@link File}s, or an empty list if the directory is missing/unreadable.
   */
  public static List<File> getAvailableCaseFiles() {
    Path externalCasesDir = casesDir();
    if (!Files.isDirectory(externalCasesDir)) {
      return Collections.emptyList();
    }
    try (Stream<Path> stream = Files.walk(externalCasesDir)) {
      return stream
          .filter(
              path -> !Files.isDirectory(path) && path.toString().toLowerCase().endsWith(".json"))
          .map(Path::toFile)
          .collect(Collectors.toList());
    } catch (IOException e) {
      return Collections.emptyList();
    }
  }

  /**
   * Imports a case by COPYING it — its {@code .json} and its sibling {@code images/} folder — into
   * the external cases directory beside the app as {@code cases/<slug>/<slug>.json} (creating the
   * directory if needed). Copying (rather than merely referencing the original location) means an
   * imported case lives with the app's cases folder and travels with it.
   *
   * @param sourcePathStr the full path to the source {@code .json} file.
   * @return a status message; a "Success…" prefix signals the import succeeded.
   */
  public static String addCaseFile(String sourcePathStr) {
    Path sourcePath = Paths.get(sourcePathStr);

    // 1. Validate the source file.
    if (!Files.exists(sourcePath)) {
      return "Error: Source file does not exist at '" + sourcePathStr + "'.";
    }
    if (Files.isDirectory(sourcePath)) {
      return "Error: The provided path is a directory, not a file.";
    }
    if (!sourcePathStr.toLowerCase().endsWith(".json")) {
      return "Error: The file must be a .json file.";
    }

    // 2. Derive a folder-safe slug from the JSON filename and prepare cases/<slug>/.
    String slug = slugFor(sourcePath);
    Path destDir = casesDir();
    Path caseDir = destDir.resolve(slug);
    if (Files.exists(caseDir)) {
      return "Warning: A case folder named '" + slug + "' already exists. Nothing was copied.";
    }

    try {
      Files.createDirectories(caseDir);

      // 3. Copy the case JSON as <slug>.json.
      Path destJson = caseDir.resolve(slug + ".json");
      Files.copy(sourcePath, destJson, StandardCopyOption.REPLACE_EXISTING);

      // 4. Copy the sibling images/ folder (if the case ships one) into cases/<slug>/images/.
      Path sourceImages = sourcePath.toAbsolutePath().getParent();
      if (sourceImages != null) {
        Path imagesDir = sourceImages.resolve("images");
        if (Files.isDirectory(imagesDir)) {
          copyTree(imagesDir, caseDir.resolve("images"));
        }
      }
      return "Success: Case '" + slug + "' added successfully.";
    } catch (IOException e) {
      return "Error: Failed to copy case: " + e.getMessage();
    }
  }

  /** A folder-safe slug derived from the JSON filename (no extension); never empty. */
  private static String slugFor(Path jsonFile) {
    String name = jsonFile.getFileName().toString();
    int dot = name.toLowerCase().lastIndexOf(".json");
    if (dot > 0) {
      name = name.substring(0, dot);
    }
    String slug = name.trim().replaceAll("[^a-zA-Z0-9-_]+", "_").replaceAll("^_+|_+$", "");
    return slug.isEmpty() ? "case" : slug;
  }

  /** Recursively copies the file tree rooted at {@code src} into {@code dest}. */
  private static void copyTree(Path src, Path dest) throws IOException {
    try (Stream<Path> stream = Files.walk(src)) {
      // Collect first so an IOException in the copy lambda surfaces (forEach swallows checked
      // ones).
      List<Path> entries = stream.collect(Collectors.toList());
      for (Path from : entries) {
        Path to = dest.resolve(src.relativize(from).toString());
        if (Files.isDirectory(from)) {
          Files.createDirectories(to);
        } else {
          Files.createDirectories(to.getParent());
          Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }
}

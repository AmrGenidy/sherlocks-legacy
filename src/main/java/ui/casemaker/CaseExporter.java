package ui.casemaker;

import JsonDTO.CaseFile;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import extractors.CaseValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import ui.casemaker.model.CaseDraft;
import ui.casemaker.model.CaseMakerSerializer;
import ui.casemaker.model.ObjectDraft;
import ui.casemaker.model.RoomDraft;
import ui.casemaker.model.SuspectDraft;

/**
 * Exports a {@link CaseDraft} to a self-contained case folder {@code cases/<slug>/} (DEC-3): the
 * case JSON plus an {@code images/} folder holding every picked asset, with the JSON's asset paths
 * rewritten to be case-relative (DEC-7 — copy happens here at export, not while authoring).
 *
 * <p>Asset paths that already resolve elsewhere (a classpath/relative path that is not a local
 * file) are left untouched. The draft's in-memory paths are restored after serialization, so the
 * model keeps pointing at the author's originals and stays re-exportable.
 */
public final class CaseExporter {

  /** Outcome of an export: where it landed and how many assets were copied. */
  public record Result(Path caseDir, Path caseJson, int assetsCopied) {}

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private CaseExporter() {}

  /**
   * Validates the draft as it would be exported, by serializing it and running the shared {@link
   * CaseValidator}. The editor surfaces the returned report live; ERRORs block export, WARNINGs are
   * shown. Asset-path WARNINGs may appear pre-export (picked paths are absolute) and clear once the
   * assets are copied into the case folder on export.
   */
  public static CaseValidator.Report validate(CaseDraft draft) {
    try {
      CaseFile caseFile = MAPPER.readValue(CaseMakerSerializer.toJson(draft), CaseFile.class);
      return CaseValidator.validate(caseFile);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to validate case draft", e);
    }
  }

  /** One asset path on the model that can be read and rewritten. */
  private record AssetSlot(Supplier<String> get, Consumer<String> set) {}

  public static Result export(CaseDraft draft, Path casesDir) throws IOException {
    String slug = slugify(caseTitle(draft));
    Path caseDir = casesDir.resolve(slug);
    return writeCase(draft, caseDir, caseDir.resolve(slug + ".json"));
  }

  /**
   * Saves the case back to the folder + file it was loaded from (DEC-3 self-contained layout),
   * overwriting the JSON in place and copying any newly-picked images into that folder's {@code
   * images/} — existing images are left untouched. Use when the author opened an existing case and
   * just adjusted it (e.g. placement) and wants to persist without exporting a fresh copy and
   * swapping it in by hand. Throws {@link IllegalStateException} when the draft has no source
   * location (a brand-new case must be exported first to create its file).
   */
  public static Result saveInPlace(CaseDraft draft) throws IOException {
    Path caseDir = draft.getSourceDir();
    Path caseJson = draft.getSourceFile();
    if (caseDir == null || caseJson == null) {
      throw new IllegalStateException("Case has no saved location yet; export it first.");
    }
    return writeCase(draft, caseDir, caseJson);
  }

  /**
   * Copies the draft's local-file assets into {@code caseDir/images/}, rewrites the model's asset
   * paths to case-relative targets, serializes, restores the in-memory paths, and writes the JSON to
   * {@code caseJson}. An asset already living in this case's {@code images/} folder (a re-opened
   * case) is kept as-is — never re-copied or renamed — so saving in place doesn't spawn duplicates.
   */
  private static Result writeCase(CaseDraft draft, Path caseDir, Path caseJson) throws IOException {
    Path imagesDir = caseDir.resolve("images");
    Files.createDirectories(imagesDir);
    Path imagesDirNorm = imagesDir.toAbsolutePath().normalize();

    List<AssetSlot> slots = assetSlots(draft);

    // Copy each local-file asset into images/, mapping its original path to the relative target.
    // Distinct sources with the same filename are disambiguated; the same source maps once.
    Map<String, String> originalToRelative = new LinkedHashMap<>();
    int copied = 0;
    for (AssetSlot slot : slots) {
      String path = slot.get().get();
      if (path == null || path.isBlank()) {
        continue;
      }
      Path source = asExistingFile(path);
      if (source == null) {
        continue; // not a local file (already a classpath/relative reference) — leave as-is
      }
      String relative = originalToRelative.get(path);
      if (relative == null) {
        Path sourceParent = source.toAbsolutePath().normalize().getParent();
        if (imagesDirNorm.equals(sourceParent)) {
          // Already in this case's images/ (re-opened case) — keep it; don't re-copy or rename.
          relative = "images/" + source.getFileName();
        } else {
          String target = uniqueName(imagesDir, source.getFileName().toString());
          Files.copy(source, imagesDir.resolve(target), StandardCopyOption.REPLACE_EXISTING);
          relative = "images/" + target;
          copied++;
        }
        originalToRelative.put(path, relative);
      }
    }

    // Temporarily rewrite the model's asset paths to the case-relative targets, serialize, restore.
    for (AssetSlot slot : slots) {
      String path = slot.get().get();
      if (path != null && originalToRelative.containsKey(path)) {
        slot.set().accept(originalToRelative.get(path));
      }
    }
    String json;
    try {
      json = ui.casemaker.model.CaseMakerSerializer.toJson(draft);
    } finally {
      restore(slots, originalToRelative);
    }

    if (caseJson.getParent() != null) {
      Files.createDirectories(caseJson.getParent());
    }
    Files.writeString(caseJson, json);
    return new Result(caseDir, caseJson, copied);
  }

  /** Restores each rewritten slot to its original (the reverse of the relative mapping). */
  private static void restore(List<AssetSlot> slots, Map<String, String> originalToRelative) {
    Map<String, String> relativeToOriginal = new LinkedHashMap<>();
    originalToRelative.forEach((original, relative) -> relativeToOriginal.put(relative, original));
    for (AssetSlot slot : slots) {
      String path = slot.get().get();
      if (path != null && relativeToOriginal.containsKey(path)) {
        slot.set().accept(relativeToOriginal.get(path));
      }
    }
  }

  private static List<AssetSlot> assetSlots(CaseDraft draft) {
    List<AssetSlot> slots = new ArrayList<>();
    slots.add(new AssetSlot(draft::getWatsonImagePath, draft::setWatsonImagePath));
    slots.add(new AssetSlot(draft::getSoundtrack, draft::setSoundtrack));
    for (RoomDraft room : draft.getRooms()) {
      slots.add(new AssetSlot(room::getImagePath, room::setImagePath));
      for (ObjectDraft object : room.getObjects()) {
        slots.add(new AssetSlot(object::getImagePath, object::setImagePath));
      }
    }
    for (SuspectDraft suspect : draft.getSuspects()) {
      slots.add(new AssetSlot(suspect::getImagePath, suspect::setImagePath));
    }
    return slots;
  }

  private static Path asExistingFile(String path) {
    try {
      Path p = Path.of(path);
      return Files.isRegularFile(p) ? p : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static String uniqueName(Path imagesDir, String name) {
    if (!Files.exists(imagesDir.resolve(name))) {
      return name;
    }
    String base = name;
    String ext = "";
    int dot = name.lastIndexOf('.');
    if (dot > 0) {
      base = name.substring(0, dot);
      ext = name.substring(dot);
    }
    int n = 2;
    while (Files.exists(imagesDir.resolve(base + "_" + n + ext))) {
      n++;
    }
    return base + "_" + n + ext;
  }

  private static String caseTitle(CaseDraft draft) {
    if (draft.getUniversalTitle() != null && !draft.getUniversalTitle().isBlank()) {
      return draft.getUniversalTitle();
    }
    String primary = draft.titleText().get();
    return primary != null && !primary.isBlank() ? primary : "case";
  }

  /** Lowercases, turns runs of non-alphanumeric characters into single hyphens, trims hyphens. */
  public static String slugify(String title) {
    String slug = title.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
    return slug.isBlank() ? "case" : slug;
  }
}

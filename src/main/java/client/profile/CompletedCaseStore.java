package client.profile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import common.dto.save.CompletedCaseRecord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local, best-effort store of {@link CompletedCaseRecord}s — the "one go" save model (see {@code
 * docs/SAVE_AND_PROFILE.md}). Only <em>completed</em> investigations are saved; there is no
 * mid-case save or resume. Records are keyed by Case {@code universal_title} and persisted to
 * {@code ~/.sherlocks-legacy/records.json}.
 *
 * <p>This is the "optional local profile file" the roadmap allows offline single-player (Hard
 * Constraint 1): all IO is swallowed, and a read/write failure never blocks play. It supersedes the
 * earlier boolean {@code CaseProgressStore}; on first run it migrates that store's solved-set into
 * minimal {@link CompletedCaseRecord#migrated migrated} records so previously-solved Cases keep
 * their "Solved" seal.
 *
 * <p>This is local persistence, not the wire protocol, so it uses a plain {@link ObjectMapper} with
 * no polymorphic default typing.
 */
public class CompletedCaseStore {

  private static final Logger logger = LoggerFactory.getLogger(CompletedCaseStore.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final Path file;

  public CompletedCaseStore() {
    this(defaultPath(), legacyProgressPath());
  }

  /** Test seam: point the store at an arbitrary records file, with no legacy migration. */
  public CompletedCaseStore(Path file) {
    this(file, null);
  }

  /**
   * Test seam: an explicit records file plus the legacy solved-set file to migrate from on first
   * run.
   */
  public CompletedCaseStore(Path file, Path legacyProgressFile) {
    this.file = file;
    migrateLegacyIfNeeded(legacyProgressFile);
  }

  private static Path defaultPath() {
    return Paths.get(System.getProperty("user.home"), ".sherlocks-legacy", "records.json");
  }

  private static Path legacyProgressPath() {
    return Paths.get(System.getProperty("user.home"), ".sherlocks-legacy", "case-progress.json");
  }

  /** The record for {@code universalTitle}, if the Case has been solved. */
  public Optional<CompletedCaseRecord> find(String universalTitle) {
    if (universalTitle == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(readAll().get(universalTitle));
  }

  /** A Case is "solved" exactly when a (detailed or migrated) record exists for it. */
  public boolean isSolved(String universalTitle) {
    return find(universalTitle).isPresent();
  }

  public Set<String> solvedTitles() {
    return new LinkedHashSet<>(readAll().keySet());
  }

  /**
   * Persists {@code record} under the <strong>hybrid</strong> keep-the-best model (see {@code
   * docs/SAVE_AND_PROFILE.md}): the monotonic Best Result is only ever raised (never lowered),
   * while the Latest Finish — every other field — is overwritten by this solve, better or worse. A
   * detail-less migrated stub never clobbers an existing record (it only seeds a seal when nothing
   * exists). All IO is best-effort: a failure is logged and swallowed.
   */
  public void save(CompletedCaseRecord record) {
    if (record == null || record.getUniversalTitle() == null || file == null) {
      return;
    }
    Map<String, CompletedCaseRecord> all = readAll();
    CompletedCaseRecord existing = all.get(record.getUniversalTitle());

    if (!record.hasDetail()) {
      // A migrated stub: seed a seal only when nothing exists; never overwrite a real record.
      if (existing == null) {
        all.put(record.getUniversalTitle(), record);
        writeAll(all);
      }
      return;
    }

    // A real finish always becomes the Latest Finish; the Best Result is lifted monotonically by
    // rank strength (fewer Deductions = a higher tier — Rank Tier is monotonic in deductions).
    CompletedCaseRecord merged = record;
    if (existing != null) {
      boolean newRunIsNewBest = record.effectiveDeductions() < existing.effectiveBestDeductions();
      merged =
          newRunIsNewBest
              ? record // record already seeds Best Result from this (new best) run
              : record.withBestResult(existing.getBestRankName(), existing.getBestDeductionsUsed());
    }
    all.put(record.getUniversalTitle(), merged);
    writeAll(all);
  }

  /** All records by {@code universal_title}. Returns an empty map on any read failure. */
  private Map<String, CompletedCaseRecord> readAll() {
    if (file == null || !Files.exists(file)) {
      return new LinkedHashMap<>();
    }
    try {
      Map<String, CompletedCaseRecord> records =
          MAPPER.readValue(
              file.toFile(), new TypeReference<LinkedHashMap<String, CompletedCaseRecord>>() {});
      return records == null ? new LinkedHashMap<>() : records;
    } catch (IOException | RuntimeException e) {
      logger.warn("Could not read completed-case records from {}: {}", file, e.toString());
      return new LinkedHashMap<>();
    }
  }

  private void writeAll(Map<String, CompletedCaseRecord> records) {
    try {
      if (file.getParent() != null) {
        Files.createDirectories(file.getParent());
      }
      MAPPER.writeValue(file.toFile(), records);
    } catch (IOException | RuntimeException e) {
      logger.warn("Could not persist completed-case records to {}: {}", file, e.toString());
    }
  }

  /**
   * On first run only (records file absent, legacy file present), folds the legacy solved-set — a
   * plain JSON array of solved {@code universal_title}s — into minimal {@link
   * CompletedCaseRecord#migrated migrated} records. Once {@code records.json} exists this never
   * runs again, so the legacy file is simply ignored thereafter (left in place, non-destructive).
   */
  private void migrateLegacyIfNeeded(Path legacyProgressFile) {
    if (file == null
        || legacyProgressFile == null
        || Files.exists(file)
        || !Files.exists(legacyProgressFile)) {
      return;
    }
    try {
      Set<String> legacy =
          MAPPER.readValue(
              legacyProgressFile.toFile(), new TypeReference<LinkedHashSet<String>>() {});
      if (legacy == null || legacy.isEmpty()) {
        return;
      }
      Map<String, CompletedCaseRecord> migrated = new LinkedHashMap<>();
      for (String title : legacy) {
        if (title != null && !title.isBlank()) {
          migrated.put(title, CompletedCaseRecord.migrated(title));
        }
      }
      if (!migrated.isEmpty()) {
        writeAll(migrated);
        logger.info("Migrated {} solved case(s) from {}", migrated.size(), legacyProgressFile);
      }
    } catch (IOException | RuntimeException e) {
      logger.warn(
          "Could not migrate legacy case progress from {}: {}", legacyProgressFile, e.toString());
    }
  }
}

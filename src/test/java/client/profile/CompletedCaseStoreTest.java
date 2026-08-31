package client.profile;

import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import common.dto.save.CompletedCaseRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.Test;

/**
 * Persistence and "keep the best" behaviour of the Completed-Case Record store, plus first-run
 * migration of the legacy solved-set. All IO is best-effort, so a missing/unwritable file never
 * throws.
 */
public class CompletedCaseStoreTest {

  private static CompletedCaseRecord solve(String title, int deductions) {
    return solve(title, "Inspector", deductions);
  }

  private static CompletedCaseRecord solve(String title, String rankName, int deductions) {
    return CompletedCaseRecord.detailed(title, rankName, deductions, 5, 5, 1L, null, null);
  }

  @Test
  public void savesAreReadableAndPersistAcrossInstances() throws Exception {
    Path file = Files.createTempDirectory("ccs").resolve("nested").resolve("records.json");
    CompletedCaseStore store = new CompletedCaseStore(file);

    assertFalse(store.isSolved("The Sapphire Affair"));
    store.save(solve("The Sapphire Affair", 4));

    assertTrue(store.isSolved("The Sapphire Affair"));
    assertEquals(Integer.valueOf(4), store.find("The Sapphire Affair").get().getDeductionsUsed());
    // A fresh instance over the same file sees the persisted record.
    assertTrue(new CompletedCaseStore(file).isSolved("The Sapphire Affair"));
  }

  @Test
  public void hybridBestRankIsMonotonicWhileLatestFinishAlwaysOverwrites() throws Exception {
    Path file = Files.createTempDirectory("ccs").resolve("records.json");
    CompletedCaseStore store = new CompletedCaseStore(file);

    // First solve: best == latest.
    store.save(solve("c", "Keen Investigator", 6));
    CompletedCaseRecord r1 = store.find("c").get();
    assertEquals(Integer.valueOf(6), r1.getDeductionsUsed());
    assertEquals(Integer.valueOf(6), r1.getBestDeductionsUsed());
    assertEquals("Keen Investigator", r1.getBestRankName());

    // A better replay (fewer deductions) improves the best AND becomes the latest.
    store.save(solve("c", "Master Detective", 3));
    CompletedCaseRecord r2 = store.find("c").get();
    assertEquals("latest follows the new run", Integer.valueOf(3), r2.getDeductionsUsed());
    assertEquals("Master Detective", r2.getRankName());
    assertEquals("best improved", Integer.valueOf(3), r2.getBestDeductionsUsed());
    assertEquals("Master Detective", r2.getBestRankName());

    // A WORSE replay: best is untouched, but the latest finish is overwritten with the worse run.
    store.save(solve("c", "Confused Constable", 8));
    CompletedCaseRecord r3 = store.find("c").get();
    assertEquals("latest is the worse run now", Integer.valueOf(8), r3.getDeductionsUsed());
    assertEquals("Confused Constable", r3.getRankName());
    assertEquals(
        "best rank strength is never lowered", Integer.valueOf(3), r3.getBestDeductionsUsed());
    assertEquals("best rank name is kept", "Master Detective", r3.getBestRankName());
  }

  @Test
  public void realSolveReplacesMigratedButNotViceVersa() throws Exception {
    Path file = Files.createTempDirectory("ccs").resolve("records.json");
    CompletedCaseStore store = new CompletedCaseStore(file);

    store.save(CompletedCaseRecord.migrated("c"));
    assertTrue(store.isSolved("c"));
    assertFalse(store.find("c").get().hasDetail());

    // Any real solve beats a migrated stub (unknown deductions = worst).
    store.save(solve("c", 9));
    assertTrue(store.find("c").get().hasDetail());
    assertEquals(Integer.valueOf(9), store.find("c").get().getDeductionsUsed());

    // A later migrated stub never overwrites a detailed record.
    store.save(CompletedCaseRecord.migrated("c"));
    assertTrue(store.find("c").get().hasDetail());
  }

  @Test
  public void firstRunMigratesLegacySolvedSetThenIgnoresIt() throws Exception {
    Path dir = Files.createTempDirectory("ccs");
    Path records = dir.resolve("records.json");
    Path legacy = dir.resolve("case-progress.json");
    Set<String> legacySolved = new LinkedHashSet<>();
    legacySolved.add("Old Case A");
    legacySolved.add("Old Case B");
    new ObjectMapper().writeValue(legacy.toFile(), legacySolved);

    CompletedCaseStore store = new CompletedCaseStore(records, legacy);

    // Previously-solved cases keep their seal as migrated (detail-less) records.
    assertTrue(store.isSolved("Old Case A"));
    assertTrue(store.isSolved("Old Case B"));
    assertFalse(store.find("Old Case A").get().hasDetail());

    // Migration does not re-run once records.json exists: a real solve then survives a fresh load.
    store.save(solve("Old Case A", 2));
    Set<String> stillSolved = new CompletedCaseStore(records, legacy).solvedTitles();
    assertTrue(stillSolved.contains("Old Case A"));
    assertEquals(
        Integer.valueOf(2),
        new CompletedCaseStore(records, legacy).find("Old Case A").get().getDeductionsUsed());
  }

  @Test
  public void missingFileReadsAsEmptyAndNeverThrows() {
    CompletedCaseStore store =
        new CompletedCaseStore(Paths.get("Z:/definitely/not/here/records.json"));
    assertFalse(store.isSolved("anything"));
    assertTrue(store.solvedTitles().isEmpty());
    assertFalse(store.find("anything").isPresent());
  }
}

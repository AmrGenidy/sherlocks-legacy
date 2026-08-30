package common.dto.save;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import common.dto.ExamResultDTO;
import common.dto.JournalEntryDTO;
import common.dto.pinboard.PinboardStateDTO;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * The pure-data artifact written when a Detective passes a Case's Final Exam (see {@code
 * docs/SAVE_AND_PROFILE.md}). It captures how the Case was solved — Rank Tier name, Deductions
 * used, Final Exam score, date, and the final Journal and Pinboard — keyed by the Case's {@code
 * universal_title}. It holds <em>no</em> JavaFX/UI types and reuses the existing serializable DTOs,
 * so a future hosted server can reuse it unchanged (ROADMAP Phase 3c).
 *
 * <p>A <strong>migrated record</strong> ({@link #migrated(String)}) marks a Case solved without any
 * detail: the pre-record solved-set (the old {@code case-progress.json}) only knew <em>that</em> a
 * Case was solved, so its records carry no rank/deductions/journal/pinboard. The scalar fields a
 * migrated record cannot know are therefore boxed and {@code null} means <em>unknown</em>.
 *
 * <p><strong>Two layers (.scratch/completed-case-records DEC-9 — hybrid keep-the-best):</strong>
 * the monotonic <em>Best Result</em> ({@link #bestRankName} + the language-independent strength
 * {@link #bestDeductionsUsed}, the fewest Deductions ever — shown on the "Solved" seal) and the
 * <em>Latest Finish</em> (everything else: {@link #rankName}, {@link #deductionsUsed}, score, date,
 * journal, pinboard — the most recent run, overwritten every solve, shown in Review). On load, an
 * old (v1) record with no Best Result fields seeds them from its stored latest run.
 */
public class CompletedCaseRecord implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** Bumped when the on-disk shape changes incompatibly (v2 added the Best Result fields). */
  public static final int CURRENT_FORMAT_VERSION = 2;

  private final int formatVersion;
  private final String universalTitle;

  // --- Best Result (monotonic; the trophy on the seal) ---
  private final String bestRankName; // best rank label; fallback when re-derivation is unavailable
  private final Integer bestDeductionsUsed; // best (fewest) Deductions ever; null = unknown

  // --- Latest Finish (the most recent run; what Review shows) ---
  private final String rankName; // null = unknown (migrated)
  private final Integer deductionsUsed; // null = unknown (migrated); compared as +infinity
  private final Integer finalExamScore; // null = unknown; display only
  private final Integer finalExamTotal; // null = unknown; display only
  private final Long dateSolvedEpochMillis; // null = unknown (migrated)
  private final List<JournalEntryDTO> journal; // never null; empty for migrated
  private final PinboardStateDTO pinboard; // may be null for migrated
  // The language the case was SOLVED in. Review re-opens the case in this language so the saved
  // Journal/Pinboard text matches the world around it. Null = unknown (a pre-v3 or migrated record),
  // in which case the caller's chosen language is used as before.
  private final String languageCode;

  @JsonCreator
  public CompletedCaseRecord(
      @JsonProperty("formatVersion") int formatVersion,
      @JsonProperty("universalTitle") String universalTitle,
      @JsonProperty("bestRankName") String bestRankName,
      @JsonProperty("bestDeductionsUsed") Integer bestDeductionsUsed,
      @JsonProperty("rankName") String rankName,
      @JsonProperty("deductionsUsed") Integer deductionsUsed,
      @JsonProperty("finalExamScore") Integer finalExamScore,
      @JsonProperty("finalExamTotal") Integer finalExamTotal,
      @JsonProperty("dateSolvedEpochMillis") Long dateSolvedEpochMillis,
      @JsonProperty("journal") List<JournalEntryDTO> journal,
      @JsonProperty("pinboard") PinboardStateDTO pinboard,
      @JsonProperty("languageCode") String languageCode) {
    this.formatVersion = formatVersion;
    this.universalTitle = universalTitle;
    // Migration: a v1 record has no Best Result fields -> seed them from the stored latest run, so
    // a
    // previously-saved solve keeps a seal rank under the hybrid model.
    this.bestRankName = bestRankName != null ? bestRankName : rankName;
    this.bestDeductionsUsed = bestDeductionsUsed != null ? bestDeductionsUsed : deductionsUsed;
    this.rankName = rankName;
    this.deductionsUsed = deductionsUsed;
    this.finalExamScore = finalExamScore;
    this.finalExamTotal = finalExamTotal;
    this.dateSolvedEpochMillis = dateSolvedEpochMillis;
    this.journal = journal != null ? new ArrayList<>(journal) : new ArrayList<>();
    this.pinboard = pinboard;
    this.languageCode = languageCode;
  }

  /** A full record for a real solve. The Best Result is seeded from this run. */
  public static CompletedCaseRecord detailed(
      String universalTitle,
      String rankName,
      int deductionsUsed,
      int finalExamScore,
      int finalExamTotal,
      long dateSolvedEpochMillis,
      List<JournalEntryDTO> journal,
      PinboardStateDTO pinboard,
      String languageCode) {
    return new CompletedCaseRecord(
        CURRENT_FORMAT_VERSION,
        universalTitle,
        rankName, // best seeds from this run
        deductionsUsed,
        rankName,
        deductionsUsed,
        finalExamScore,
        finalExamTotal,
        dateSolvedEpochMillis,
        journal,
        pinboard,
        languageCode);
  }

  /** Overload without a recorded language — Review then falls back to the chosen language. */
  public static CompletedCaseRecord detailed(
      String universalTitle,
      String rankName,
      int deductionsUsed,
      int finalExamScore,
      int finalExamTotal,
      long dateSolvedEpochMillis,
      List<JournalEntryDTO> journal,
      PinboardStateDTO pinboard) {
    return detailed(
        universalTitle,
        rankName,
        deductionsUsed,
        finalExamScore,
        finalExamTotal,
        dateSolvedEpochMillis,
        journal,
        pinboard,
        null);
  }

  /**
   * A minimal record marking {@code universalTitle} solved with no detail — used when migrating the
   * pre-record solved-set so previously-solved Cases keep their "Solved" seal.
   */
  public static CompletedCaseRecord migrated(String universalTitle) {
    return new CompletedCaseRecord(
        CURRENT_FORMAT_VERSION,
        universalTitle,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  /**
   * This Latest Finish with its Best Result fields overridden — used by the store to keep the
   * monotonic best across replays while the rest of the record reflects the latest run.
   */
  public CompletedCaseRecord withBestResult(String bestRankName, Integer bestDeductionsUsed) {
    return new CompletedCaseRecord(
        formatVersion,
        universalTitle,
        bestRankName,
        bestDeductionsUsed,
        rankName,
        deductionsUsed,
        finalExamScore,
        finalExamTotal,
        dateSolvedEpochMillis,
        journal,
        pinboard,
        languageCode); // the latest run's language travels with its Journal/Pinboard
  }

  /** The language the case was solved in, or null when unknown (pre-v3 / migrated record). */
  public String getLanguageCode() {
    return languageCode;
  }

  /** Assembles a detailed record from the Final Exam result plus the session's journal/pinboard. */
  /** Overload without a recorded language — Review then falls back to the chosen language. */
  public static CompletedCaseRecord fromExamResult(
      String universalTitle,
      ExamResultDTO result,
      int deductionsUsed,
      List<JournalEntryDTO> journal,
      PinboardStateDTO pinboard,
      long dateSolvedEpochMillis) {
    return fromExamResult(
        universalTitle, result, deductionsUsed, journal, pinboard, dateSolvedEpochMillis, null);
  }

  public static CompletedCaseRecord fromExamResult(
      String universalTitle,
      ExamResultDTO result,
      int deductionsUsed,
      List<JournalEntryDTO> journal,
      PinboardStateDTO pinboard,
      long dateSolvedEpochMillis,
      String languageCode) {
    return detailed(
        universalTitle,
        result != null ? result.getFinalRank() : null,
        deductionsUsed,
        result != null ? result.getScore() : 0,
        result != null ? result.getTotalQuestions() : 0,
        dateSolvedEpochMillis,
        journal,
        pinboard,
        languageCode);
  }

  public int getFormatVersion() {
    return formatVersion;
  }

  public String getUniversalTitle() {
    return universalTitle;
  }

  public String getRankName() {
    return rankName;
  }

  public Integer getDeductionsUsed() {
    return deductionsUsed;
  }

  public Integer getFinalExamScore() {
    return finalExamScore;
  }

  public Integer getFinalExamTotal() {
    return finalExamTotal;
  }

  public Long getDateSolvedEpochMillis() {
    return dateSolvedEpochMillis;
  }

  public List<JournalEntryDTO> getJournal() {
    return journal;
  }

  public PinboardStateDTO getPinboard() {
    return pinboard;
  }

  /** The Best Result rank label (fallback for the seal when re-derivation is unavailable). */
  public String getBestRankName() {
    return bestRankName;
  }

  /** The Best Result strength: the fewest Deductions ever used; {@code null} if unknown. */
  public Integer getBestDeductionsUsed() {
    return bestDeductionsUsed;
  }

  /** True when this record carries a real solve's detail (rank, deductions, journal, pinboard). */
  public boolean hasDetail() {
    return deductionsUsed != null;
  }

  /** Deductions used, treating an unknown (migrated) count as the worst possible result. */
  public int effectiveDeductions() {
    return deductionsUsed != null ? deductionsUsed : Integer.MAX_VALUE;
  }

  /** Best (fewest) Deductions ever, treating an unknown count as the worst possible result. */
  public int effectiveBestDeductions() {
    return bestDeductionsUsed != null ? bestDeductionsUsed : Integer.MAX_VALUE;
  }

  /**
   * Whether this solve should replace {@code other} under "keep the best". Compares purely on
   * Deductions used (fewer is better; an equal count is not an improvement, so a record is never
   * worsened).
   *
   * <p><strong>Assumption:</strong> the Rank Tier is monotonic in deduction count — {@code
   * Core.util.RankEvaluator} derives the tier solely from the count, so fewer Deductions can never
   * yield a <em>lower</em> tier. This is why comparing on Deductions alone honours the spec's
   * "higher rank, or same rank with fewer deductions". Revisit this method if rank computation ever
   * stops being purely deduction-derived. The Final Exam score is display-only and intentionally
   * excluded (a solve is always a perfect score).
   */
  public boolean isBetterThan(CompletedCaseRecord other) {
    return other == null || effectiveDeductions() < other.effectiveDeductions();
  }
}

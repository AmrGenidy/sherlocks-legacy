package ui.review;

import common.dto.JournalEntryDTO;
import common.dto.pinboard.PinboardStateDTO;
import common.dto.save.CompletedCaseRecord;
import java.util.ArrayList;
import java.util.List;

/**
 * The pure, FX-free view-model behind the read-only Review viewer (docs/SAVE_AND_PROFILE.md). It
 * adapts a {@link CompletedCaseRecord} into the data the viewer renders — the summary fields, the
 * Journal lines (using the existing {@link JournalEntryDTO#toString()} rendering), and the Pinboard
 * state — and answers whether there is anything to review at all.
 *
 * <p>It exposes display data only: there is no mutation, no engine, and no command path, so the
 * Review viewer is <em>read-only by construction</em>. A {@link CompletedCaseRecord#migrated
 * migrated} (detail-less) record has {@link #hasDetail()} {@code false}, and the viewer shows a
 * graceful "no detailed record" message instead of empty panels.
 */
public class CaseReviewModel {

  private final CompletedCaseRecord record;

  public CaseReviewModel(CompletedCaseRecord record) {
    this.record = record;
  }

  /**
   * Whether this record carries a real solve's detail to review (rank, deductions, journal, etc.).
   */
  public boolean hasDetail() {
    return record != null && record.hasDetail();
  }

  public String getRankName() {
    return record != null ? record.getRankName() : null;
  }

  public Integer getDeductionsUsed() {
    return record != null ? record.getDeductionsUsed() : null;
  }

  public Integer getFinalExamScore() {
    return record != null ? record.getFinalExamScore() : null;
  }

  public Integer getFinalExamTotal() {
    return record != null ? record.getFinalExamTotal() : null;
  }

  public Long getDateSolvedEpochMillis() {
    return record != null ? record.getDateSolvedEpochMillis() : null;
  }

  /** The Journal entries rendered as display lines, using the existing entry rendering. */
  public List<String> journalLines() {
    List<String> lines = new ArrayList<>();
    if (record != null && record.getJournal() != null) {
      for (JournalEntryDTO entry : record.getJournal()) {
        if (entry != null) {
          lines.add(entry.toString());
        }
      }
    }
    return lines;
  }

  /** Whether there is a Pinboard worth showing (at least one pinned item). */
  public boolean hasPinboard() {
    PinboardStateDTO pinboard = getPinboard();
    return pinboard != null && pinboard.getItems() != null && !pinboard.getItems().isEmpty();
  }

  public PinboardStateDTO getPinboard() {
    return record != null ? record.getPinboard() : null;
  }
}

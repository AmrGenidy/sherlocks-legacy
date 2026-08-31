package common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

public class JournalEntryDTO implements Serializable {
  @Serial
  private static final long serialVersionUID = 2L; // Bumped version

  private final String id;
  private final JournalEntryType type;
  private final String sourceId;
  private final String title;
  private final String text;
  private final String contributorPlayerId;
  private final long timestamp;

  // New Full Constructor
  @JsonCreator
  public JournalEntryDTO(
          @JsonProperty("id") String id,
          @JsonProperty("type") JournalEntryType type,
          @JsonProperty("sourceId") String sourceId,
          @JsonProperty("title") String title,
          @JsonProperty("text") String text,
          @JsonProperty("contributorPlayerId") String contributorPlayerId,
          @JsonProperty("timestamp") long timestamp) {

    this.text = Objects.requireNonNull(text, "Text cannot be null");
    this.contributorPlayerId = Objects.requireNonNull(contributorPlayerId, "Contributor ID cannot be null");
    this.timestamp = timestamp;

    // New fields
    this.id = id;
    this.type = type != null ? type : JournalEntryType.NOTE;
    this.sourceId = sourceId;
    this.title = title != null ? title : "";
  }

  // Backward compatibility constructor (maps old calls to new structure as NOTES)
  public JournalEntryDTO(String text, String contributorPlayerId, long timestamp) {
      this(
          "note:" + timestamp,
          JournalEntryType.NOTE,
          null,
          "Note",
          text,
          contributorPlayerId,
          timestamp
      );
  }

  public String getId() {
    return id;
  }

  public JournalEntryType getType() {
    return type;
  }

  public String getSourceId() {
    return sourceId;
  }

  public String getTitle() {
    return title;
  }

  public String getText() {
    return text;
  }

  public String getContributorPlayerId() {
    return contributorPlayerId;
  }

  public long getTimestamp() {
    return timestamp;
  }

  @Override
  public String toString() {
    // Format based on type
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    String timeStr = sdf.format(new Date(timestamp));
    String prefix = contributorPlayerId.startsWith("Player") ? contributorPlayerId + ":" : contributorPlayerId;

    String typeTag;
    switch (type) {
        case CLUE: typeTag = "[CLUE]"; break;
        case SUSPECT_STATEMENT: typeTag = "[SUSPECT]"; break;
        case DEDUCTION: typeTag = "[DEDUCTION]"; break;
        case NOTE: typeTag = "[NOTE]"; break;
        default: typeTag = "[JOURNAL]";
    }

    // Example: [CLUE] Bloody Knife: The blade is still wet...
    if (title != null && !title.isEmpty()) {
        return typeTag + " " + title + ": " + text;
    } else {
        return typeTag + " " + text;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    JournalEntryDTO that = (JournalEntryDTO) o;
    // Identity should primarily be based on ID if available,
    // but for backward compatibility or strict value semantics:
    return Objects.equals(id, that.id) &&
           Objects.equals(text, that.text) &&
           Objects.equals(contributorPlayerId, that.contributorPlayerId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, text, contributorPlayerId);
  }
}

package common.commands;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import common.dto.JournalEntryDTO;
import common.dto.JournalEntryType;
import common.dto.TextMessage;
import common.interfaces.GameActionContext;
import java.io.Serial;

public class JournalAddCommand extends BaseCommand {
  @Serial private static final long serialVersionUID = 1L;
  private final String note;

  @JsonCreator
  public JournalAddCommand(@JsonProperty("note") String note) {
    super(true);
    if (note == null || note.trim().isEmpty()) {
      throw new IllegalArgumentException("Note cannot be null or empty for JournalAddCommand.");
    }
    common.WireLimits.requireLength(note, common.WireLimits.MAX_NOTE_TEXT_LENGTH, "note");
    this.note = note.trim();
  }

  public String getNote() {
    return note;
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    long timestamp = System.currentTimeMillis();
    // ID Scheme: note:<timestamp>
    String noteId = "note:" + timestamp;

    JournalEntryDTO newEntry =
        new JournalEntryDTO(
            noteId,
            JournalEntryType.NOTE,
            "player", // sourceId
            "Note",
            this.note,
            getPlayerId(),
            timestamp);

    context.addJournalEntry(newEntry);

    // Send a simple confirmation message to the terminal
    TextMessage confirmation =
        new TextMessage("Note added to journal.", false, "game.journal.noteAdded", null);
    context.sendResponseToPlayer(getPlayerId(), confirmation);
  }

  @Override
  protected boolean allowedDuringFinalExam() {
    return true; // the Journal is a reference tool the detective keeps during the Final Exam
  }

  @Override
  public String getDescription() {
    return "Adds a custom note to your journal. Usage: journal add [your note text]";
  }
}

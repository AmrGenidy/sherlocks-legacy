package common.commands;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import common.dto.JournalEntryDTO;
import common.dto.TextMessage;
import common.interfaces.GameActionContext;
import java.io.Serial;
import java.util.List;
import java.util.stream.Collectors;

public class JournalCommand extends BaseCommand {
  @Serial private static final long serialVersionUID = 1L;
  private final String keyword;

  @JsonCreator
  public JournalCommand(@JsonProperty("keyword") String keyword) {
    super(true);
    this.keyword =
        (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim().toLowerCase() : null;
  }

  public String getKeyword() {
    return keyword;
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    List<JournalEntryDTO> entries = context.getJournalEntries(getPlayerId());
    if (entries.isEmpty()) {
      context.sendResponseToPlayer(getPlayerId(), new TextMessage("Your journal is empty.", false,
              "game.journal.empty", null));
      return;
    }

    List<JournalEntryDTO> filteredEntries = entries;
    String responseTitle = "Journal Contents:";

    if (this.keyword != null) {
      responseTitle = "Journal search results for '" + this.keyword + "':";
      filteredEntries =
          entries.stream()
              .filter(
                  entry ->
                      entry.getText().toLowerCase().contains(this.keyword)
                          || entry.getContributorPlayerId().toLowerCase().contains(this.keyword))
              .collect(Collectors.toList());

      if (filteredEntries.isEmpty()) {
        context.sendResponseToPlayer(
            getPlayerId(),
            new TextMessage("No journal entries found matching '" + this.keyword + "'.", false,
                "game.journal.noMatch", java.util.List.of(this.keyword)));
        return;
      }
    }

    context.sendResponseToPlayer(getPlayerId(), new TextMessage(responseTitle, false));
    for (JournalEntryDTO entry : filteredEntries) {
      context.sendResponseToPlayer(getPlayerId(), new TextMessage(entry.toString(), false));
    }
  }

  @Override
  protected boolean allowedDuringFinalExam() {
    return true; // the Journal is a reference tool the detective keeps during the Final Exam
  }

  @Override
  protected boolean allowedDuringReview() {
    return true; // viewing the saved Journal is the whole point of a Review Session
  }

  @Override
  public String getDescription() {
    return "Displays all journal entries. Usage: journal [optional_keyword_to_search]";
  }
}

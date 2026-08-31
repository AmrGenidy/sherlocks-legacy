package common.commands;

import Core.Room;
import Core.Suspect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import common.dto.DialogueEventDTO;
import common.dto.DialogueType;
import common.dto.JournalEntryDTO;
import common.dto.JournalEntryType;
import common.dto.TextMessage;
import common.interfaces.GameActionContext;
import java.io.Serial;
import java.util.List;
import java.util.stream.Collectors;

public class QuestionCommand extends BaseCommand {
  @Serial
  private static final long serialVersionUID = 1L;
  private final String suspectName;

  @JsonCreator
  public QuestionCommand(@JsonProperty("suspectName") String suspectName) {
    super(true);
    if (suspectName == null || suspectName.trim().isEmpty()) {
      throw new IllegalArgumentException("Suspect name cannot be null or empty for QuestionCommand.");
    }
    common.WireLimits.requireLength(suspectName, common.WireLimits.MAX_NAME_LENGTH, "suspectName");
    this.suspectName = suspectName.trim();
  }

  public String getSuspectName() {
    return suspectName;
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    Room currentRoom = context.getCurrentRoomForPlayer(getPlayerId());
    if (currentRoom == null) {
      context.sendResponseToPlayer(getPlayerId(), new TextMessage("Error: You are not in a valid room to question anyone.", true));
      return;
    }

    List<Suspect> suspectsInRoom = context.getAllSuspects().stream()
            .filter(s -> s.getCurrentRoom() != null && s.getCurrentRoom().getName().equalsIgnoreCase(currentRoom.getName()))
            .filter(s -> s.getName().equalsIgnoreCase(this.suspectName))
            .collect(Collectors.toList());

    if (suspectsInRoom.isEmpty()) {
      context.sendResponseToPlayer(getPlayerId(), new TextMessage("Suspect '" + this.suspectName + "' is not in this room.", false,
              "game.suspect.notInRoom", java.util.List.of(this.suspectName)));
      return;
    }

    Suspect targetSuspect = suspectsInRoom.get(0);
    String statement = targetSuspect.getStatement();
    // An authored statement is case content (already localized in the case JSON, shown verbatim). The
    // empty-statement fallback is UI chrome, so it carries a localization key + the suspect name arg.
    String textKey = null;
    java.util.List<String> args = null;
    if (statement == null || statement.trim().isEmpty()) {
      statement = targetSuspect.getName() + " has nothing to say or seems unwilling to talk right now.";
      textKey = "game.question.nothingToSay";
      args = java.util.List.of(targetSuspect.getName());
    }

    // Send Dialogue Event (Local Only). The speaker shows the per-language Display Name; the command
    // and journal source id stay keyed on the Universal name / id.
    context.sendResponseToPlayer(getPlayerId(), new DialogueEventDTO(
            targetSuspect.getDisplayName(),
            "\"" + statement + "\"",
            DialogueType.SUSPECT,
            null,
            textKey,
            args
    ));

    // Structured Entry
    String stmtId = "stmt:" + targetSuspect.getId() + ":default";

    JournalEntryDTO entry = new JournalEntryDTO(
            stmtId,
            JournalEntryType.SUSPECT_STATEMENT,
            targetSuspect.getId(),
            targetSuspect.getDisplayName() + " Statement",
            statement, // Raw content
            getPlayerId(),
            System.currentTimeMillis()
    );

    context.addJournalEntry(entry);
    context.sendResponseToPlayer(getPlayerId(), new TextMessage("(You can now try to 'deduce " + targetSuspect.getName() + "' based on their statement.)", false,
            "game.question.tryDeduce", java.util.List.of(targetSuspect.getName())));
  }

  @Override
  public String getDescription() {
    return "Questions a suspect in the current room. Usage: question [suspect_name]";
  }
}

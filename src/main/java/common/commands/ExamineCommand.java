package common.commands;

import Core.GameObject;
import Core.Room;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import common.dto.DialogueEventDTO;
import common.dto.DialogueType;
import common.dto.JournalEntryDTO;
import common.dto.JournalEntryType;
import common.dto.TextMessage;
import common.interfaces.GameActionContext;
import java.io.Serial;

public class ExamineCommand extends BaseCommand {
  @Serial
  private static final long serialVersionUID = 1L;
  private final String objectName;

  @JsonCreator
  public ExamineCommand(@JsonProperty("objectName") String objectName) {
    super(true);
    if (objectName == null || objectName.trim().isEmpty()) {
      throw new IllegalArgumentException("Object name cannot be null or empty for ExamineCommand.");
    }
    common.WireLimits.requireLength(objectName, common.WireLimits.MAX_NAME_LENGTH, "objectName");
    this.objectName = objectName.trim();
  }

  public String getObjectName() {
    return objectName;
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    Room currentRoom = context.getCurrentRoomForPlayer(getPlayerId());
    if (currentRoom == null) {
      context.sendResponseToPlayer(getPlayerId(), new TextMessage("Error: You are not in a valid room.", true));
      return;
    }

    GameObject objectToExamine = currentRoom.getObject(this.objectName);
    if (objectToExamine == null) {
      context.sendResponseToPlayer(getPlayerId(), new TextMessage("There is no '" + this.objectName + "' to examine here.", false,
              "game.examine.noObject", java.util.List.of(this.objectName)));
      return;
    }

    String examinationResult = objectToExamine.getExamine();
    if (examinationResult == null || examinationResult.trim().isEmpty()) {
      examinationResult = objectToExamine.getDescription();
    }

    // Send Dialogue Event (Local Only). Title shows the per-language Display Name; the command and
    // journal source id stay keyed on the Universal name / id.
    context.sendResponseToPlayer(getPlayerId(), new DialogueEventDTO(
            "Examining: " + objectToExamine.getDisplayName(),
            examinationResult,
            DialogueType.EXAMINE
    ));

    // Create structured journal entry
    String clueId = "clue:" + objectToExamine.getId();

    JournalEntryDTO entry = new JournalEntryDTO(
            clueId,
            JournalEntryType.CLUE,
            objectToExamine.getId(),
            objectToExamine.getDisplayName(),
            examinationResult, // Raw content
            getPlayerId(),
            System.currentTimeMillis()
    );

    context.addJournalEntry(entry);
  }

  @Override
  public String getDescription() {
    return "Inspects an object for clues. Usage: examine [object_name]";
  }
}

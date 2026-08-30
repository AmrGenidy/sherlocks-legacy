package common.commands;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import common.dto.DialogueEventDTO;
import common.dto.DialogueType;
import common.dto.TextMessage;
import common.dto.WatsonHintResponseDTO;
import common.interfaces.GameActionContext;
import java.io.Serial;

public class AskWatsonCommand extends BaseCommand {
  @Serial private static final long serialVersionUID = 1L;

  private String target;

  public AskWatsonCommand() {
    this(null);
  }

  @JsonCreator
  public AskWatsonCommand(@JsonProperty("target") String target) {
    super(true);
    common.WireLimits.requireLength(target, common.WireLimits.MAX_NAME_LENGTH, "target");
    this.target = target;
  }

  public String getTarget() {
    return target;
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    WatsonHintResponseDTO watsonResponse;
    if (target != null && !target.trim().isEmpty()) {
      watsonResponse = context.askWatsonAboutTarget(getPlayerId(), target.trim());
    } else {
      watsonResponse = context.askWatsonForHint(getPlayerId());
    }

    if (watsonResponse == null) {
      context.sendResponseToPlayer(
          getPlayerId(), new TextMessage("Error receiving response from Watson.", true));
      return;
    }

    String messageContent = watsonResponse.getMessage();
    String messageKey = watsonResponse.getMessageKey();

    // Check if it's an actual hint or just a system/error message
    boolean isDialogue = watsonResponse.isActualHint();

    if (isDialogue) {
      // A generic response carries a UI-language key (resolved + quoted on the client); an authored
      // hint is already localized case content, quoted here. The English text rides along as the
      // fallback either way. (.scratch/gui-localized-watson-hints phase 2)
      DialogueEventDTO event =
          messageKey != null
              ? new DialogueEventDTO(
                  "Dr. Watson", "\"" + messageContent + "\"", DialogueType.WATSON, messageKey)
              : new DialogueEventDTO(
                  "Dr. Watson", "\"" + messageContent + "\"", DialogueType.WATSON);
      context.sendResponseToPlayer(getPlayerId(), event);
    } else {
      context.sendResponseToPlayer(getPlayerId(), new TextMessage(messageContent, false));
    }
  }

  @Override
  public String getDescription() {
    return "Asks Dr. Watson for a hint if he is in the same room.";
  }
}

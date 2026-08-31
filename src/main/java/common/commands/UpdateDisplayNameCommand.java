package common.commands;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import common.dto.UpdateDisplayNameRequestDTO;
import common.interfaces.GameActionContext;
import java.io.Serial;

public class UpdateDisplayNameCommand extends BaseCommand {
  @Serial
  private static final long serialVersionUID = 1L;
  private final UpdateDisplayNameRequestDTO payload;

  @JsonCreator
  public UpdateDisplayNameCommand(@JsonProperty("payload") UpdateDisplayNameRequestDTO payload) {
    super(false);
    this.payload = payload;
  }

  public UpdateDisplayNameRequestDTO getPayload() {
    return payload;
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    context.processUpdateDisplayName(getPlayerId(), payload.getNewDisplayName());
  }

  @Override
  public String getDescription() {
    return "Requests the server to update the player's display name.";
  }
}
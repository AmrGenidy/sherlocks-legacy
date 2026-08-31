package common.commands;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import common.dto.UpdateAvatarRequestDTO;
import common.interfaces.GameActionContext;
import java.io.Serial;

/**
 * Requests the server to record this player's chosen avatar. Mirrors {@link
 * UpdateDisplayNameCommand}.
 */
public class UpdateAvatarCommand extends BaseCommand {
  @Serial private static final long serialVersionUID = 1L;
  private final UpdateAvatarRequestDTO payload;

  @JsonCreator
  public UpdateAvatarCommand(@JsonProperty("payload") UpdateAvatarRequestDTO payload) {
    super(false);
    this.payload = payload;
  }

  public UpdateAvatarRequestDTO getPayload() {
    return payload;
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    context.processUpdateAvatar(getPlayerId(), payload.getAvatarId());
  }

  @Override
  public String getDescription() {
    return "Requests the server to update the player's chosen avatar.";
  }
}

package common.commands;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import common.dto.JoinPrivateGameRequestDTO;
import common.interfaces.GameActionContext;
import java.io.Serial;

public class JoinPrivateGameCommand extends BaseCommand {
  private static final org.slf4j.Logger logger =
      org.slf4j.LoggerFactory.getLogger(JoinPrivateGameCommand.class);

  @Serial
  private static final long serialVersionUID = 1L;
  private final JoinPrivateGameRequestDTO payload;

  @JsonCreator
  public JoinPrivateGameCommand(@JsonProperty("payload") JoinPrivateGameRequestDTO payload) {
    super(false);
    if (payload == null) {
      throw new IllegalArgumentException("Payload cannot be null for JoinPrivateGameCommand.");
    }
    this.payload = payload;
  }

  public JoinPrivateGameRequestDTO getPayload() {
    return payload;
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    logger.debug("Server received JoinPrivateGameCommand for code: " + payload.getGameCode() + " from player: " + getPlayerId());
  }

  @Override
  public String getDescription() {
    return "Requests to join a specific private game session using a code.";
  }
}
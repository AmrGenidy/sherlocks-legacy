package common.commands;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import common.dto.JoinPublicGameRequestDTO;
import common.interfaces.GameActionContext;
import java.io.Serial;

public class JoinPublicGameCommand extends BaseCommand {
  private static final org.slf4j.Logger logger =
      org.slf4j.LoggerFactory.getLogger(JoinPublicGameCommand.class);

  @Serial
  private static final long serialVersionUID = 1L;
  private final JoinPublicGameRequestDTO payload;

  @JsonCreator
  public JoinPublicGameCommand(@JsonProperty("payload") JoinPublicGameRequestDTO payload) {
    super(false);
    if (payload == null) {
      throw new IllegalArgumentException("Payload cannot be null for JoinPublicGameCommand.");
    }
    this.payload = payload;
  }

  public JoinPublicGameRequestDTO getPayload() {
    return payload;
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    logger.debug("Server received JoinPublicGameCommand for session: " + payload.getSessionId() + " from player: " + getPlayerId());
  }

  @Override
  public String getDescription() {
    return "Requests to join a specific public game session.";
  }
}
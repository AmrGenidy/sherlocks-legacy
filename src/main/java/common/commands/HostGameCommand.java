package common.commands;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import common.dto.HostGameRequestDTO;
import common.interfaces.GameActionContext;
import java.io.Serial;

public class HostGameCommand extends BaseCommand {
  private static final org.slf4j.Logger logger =
      org.slf4j.LoggerFactory.getLogger(HostGameCommand.class);

  @Serial
  private static final long serialVersionUID = 1L;
  private final HostGameRequestDTO payload;

  @JsonCreator
  public HostGameCommand(@JsonProperty("payload") HostGameRequestDTO payload) {
    super(false);
    if (payload == null) {
      throw new IllegalArgumentException("Payload cannot be null for HostGameCommand.");
    }
    this.payload = payload;
  }

  public HostGameRequestDTO getPayload() {
    return payload;
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    logger.debug("Server received HostGameCommand from player: " + getPlayerId() + " for case: " + payload.getCaseUniversalTitle());
  }

  @Override
  public String getDescription() {
    return "Requests to host a new game with a selected case. Usage: host game [case_title] [public/private]";
  }
}
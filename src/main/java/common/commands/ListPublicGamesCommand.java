package common.commands;

import common.interfaces.GameActionContext;
import java.io.Serial;

public class ListPublicGamesCommand extends BaseCommand {
  private static final org.slf4j.Logger logger =
      org.slf4j.LoggerFactory.getLogger(ListPublicGamesCommand.class);

  @Serial
  private static final long serialVersionUID = 1L;

  public ListPublicGamesCommand() {
    super(false);
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    logger.debug("Server received ListPublicGamesCommand from player: " + getPlayerId());
  }

  @Override
  public String getDescription() {
    return "Requests a list of currently available public games to join.";
  }
}
package common.commands;

import java.io.Serial;

import common.interfaces.GameActionContext;

public class ExitCommand extends BaseCommand {
  @Serial
  private static final long serialVersionUID = 1L;

  public ExitCommand() {
    // FIX: Changed from true to false. An exit command must be allowed in a lobby.
    super(false);
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    context.handlePlayerExitRequest(getPlayerId());
  }

  @Override
  public String getDescription() {
    return "Exits the current case (returning to case selection/lobby) or the application if not in a case.";
  }
}
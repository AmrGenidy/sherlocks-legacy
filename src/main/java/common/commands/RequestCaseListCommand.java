package common.commands;

import common.interfaces.GameActionContext;
import java.io.Serial;

public class RequestCaseListCommand extends BaseCommand {
  @Serial
  private static final long serialVersionUID = 1L;

  public RequestCaseListCommand() {
    super(false);
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    // Server-side only logic is in GameSessionManager
  }

  @Override
  public String getDescription() {
    return "Client request to the server for the list of available game cases.";
  }
}
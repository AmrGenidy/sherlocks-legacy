package common.commands;

import common.interfaces.GameActionContext;
import java.io.Serial;

public class RequestStartCaseCommand extends BaseCommand {
  @Serial
  private static final long serialVersionUID = 1L;

  public RequestStartCaseCommand() {
    super(false);
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    context.processRequestStartCase(getPlayerId());
  }

  @Override
  public String getDescription() {
    return "Requests the host to start the case investigation.";
  }
}
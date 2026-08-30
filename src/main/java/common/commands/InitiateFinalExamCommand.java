package common.commands;

import common.dto.TextMessage;
import common.interfaces.GameActionContext;
import java.io.Serial;

public class InitiateFinalExamCommand extends BaseCommand {
  @Serial
  private static final long serialVersionUID = 1L;

  public InitiateFinalExamCommand() {
    super(true);
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    if (!context.canStartFinalExam(getPlayerId())) {
      context.sendResponseToPlayer(getPlayerId(), new TextMessage("You cannot start the final exam at this time.", true,
              "game.exam.cannotStartNow", null));
      return;
    }
    context.startExamProcess(getPlayerId());
  }

  @Override
  public String getDescription() {
    return "Initiates the final exam process to solve the case.";
  }
}
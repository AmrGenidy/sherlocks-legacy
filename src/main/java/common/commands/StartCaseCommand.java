package common.commands;

import JsonDTO.CaseData;
import common.dto.TextMessage;
import common.interfaces.GameActionContext;
import java.io.Serial;

public class StartCaseCommand extends BaseCommand {
  @Serial
  private static final long serialVersionUID = 1L;

  public StartCaseCommand() {
    super(false);
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    if (context.isCaseStarted()) {
      context.sendResponseToPlayer(getPlayerId(), new TextMessage("The case has already started.", false,
              "game.case.alreadyStarted", null));
      return;
    }

    CaseData selectedCase = context.getSelectedCase();

    if (selectedCase == null) {
      context.sendResponseToPlayer(getPlayerId(), new TextMessage("Error: No case is currently selected or ready in this session.", true));
      return;
    }

    context.setCaseStarted(true);
  }

  @Override
  public String getDescription() {
    return "Begins the investigation for the selected case.";
  }
}
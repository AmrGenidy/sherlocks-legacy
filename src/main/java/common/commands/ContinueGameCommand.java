package common.commands;

import common.interfaces.GameActionContext;
import java.io.Serial;

public class ContinueGameCommand extends BaseCommand {
  @Serial private static final long serialVersionUID = 1L;

  public ContinueGameCommand() {
    // requiresCaseStarted = true (SECURITY_PLAN B/P2-4): route through the shared exam/review
    // lockout gate so a peer can't drive it during a Final Exam / Review. It is only ever used
    // after the case has started (post-event view refresh), so the case-started gate is a no-op
    // for legitimate use.
    super(true);
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    context.processContinueGame(getPlayerId());
  }

  @Override
  public String getDescription() {
    return "Continues the game after a major event (e.g., Final Exam), refreshing the view for all players.";
  }
}

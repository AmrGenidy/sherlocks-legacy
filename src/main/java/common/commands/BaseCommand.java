package common.commands;

import com.fasterxml.jackson.annotation.JsonProperty;
import common.dto.TextMessage;
import common.interfaces.GameActionContext;

public abstract class BaseCommand implements Command {
  private static final org.slf4j.Logger logger =
      org.slf4j.LoggerFactory.getLogger(BaseCommand.class);

  protected String playerId;
  protected final boolean requiresCaseStarted;

  public BaseCommand(@JsonProperty("requiresCaseStarted") boolean requiresCaseStarted) {
    this.requiresCaseStarted = requiresCaseStarted;
  }

  @Override
  public String getPlayerId() {
    return playerId;
  }

  @Override
  public void setPlayerId(String playerId) {
    this.playerId = playerId;
  }

  public boolean isRequiresCaseStarted() {
    return requiresCaseStarted;
  }

  @Override
  public final void execute(GameActionContext context) {
    if (playerId == null || playerId.trim().isEmpty()) {
      logger.error("Error: Player ID not set for command: " + getClass().getSimpleName());
      return;
    }
    if (requiresCaseStarted && !context.isCaseStarted()) {
      context.sendResponseToPlayer(
          playerId,
          new TextMessage("The case has not started yet. Use 'start case' to begin.", true,
              "game.case.notStarted", null));
      return;
    }
    // Final Exam lockout (.scratch/exam-command-lockout): while an exam is in progress the engine
    // authority refuses every gameplay/action command in both contexts; only the reference tools
    // (Journal, and the UI-only chat/Pinboard) stay open. The flag and the refusal text are owned
    // by the engine layer; this is the single dispatch chokepoint both adapters route through.
    if (requiresCaseStarted && context.isExamActive() && !allowedDuringFinalExam()) {
      context.sendResponseToPlayer(
          playerId,
          new TextMessage(context.getGameTexts().commandUnavailableDuringFinalExam(), true));
      return;
    }
    // Review Session lockout (.scratch/gui-review-enter-case): while reviewing a solved Case the
    // engine refuses gameplay mutations + the Final Exam so nothing changes the saved record; only
    // navigation/reference commands (move/look/journal/tasks/help) opt back in. Same single dispatch
    // chokepoint as the exam lockout, so both adapters honour it by construction (ADR-0001).
    if (requiresCaseStarted && context.isReviewMode() && !allowedDuringReview()) {
      context.sendResponseToPlayer(
          playerId,
          new TextMessage(context.getGameTexts().commandUnavailableDuringReview(), true));
      return;
    }
    executeCommandLogic(context);
  }

  /**
   * Whether this command may still run while a Final Exam is in progress. Defaults to {@code false}
   * (action commands are locked out); the Journal reference commands override it to {@code true}.
   */
  protected boolean allowedDuringFinalExam() {
    return false;
  }

  /**
   * Whether this command may run during a Review Session. Defaults to {@code false} (gameplay
   * mutations are no-ops while reviewing); the navigation/reference commands (move, look, journal,
   * tasks, help) override it to {@code true} so the player can walk the solved Case.
   */
  protected boolean allowedDuringReview() {
    return false;
  }

  protected abstract void executeCommandLogic(GameActionContext context);
}

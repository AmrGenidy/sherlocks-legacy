package common.commands;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import common.interfaces.GameActionContext;
import java.io.Serial;

public class UpdateTaskStateCommand extends BaseCommand {
  @Serial private static final long serialVersionUID = 1L;

  private final int taskIndex;
  private final boolean isCompleted;

  @JsonCreator
  public UpdateTaskStateCommand(
      @JsonProperty("taskIndex") int taskIndex, @JsonProperty("isCompleted") boolean isCompleted) {
    // requiresCaseStarted = true (SECURITY_PLAN B/P2-4): task toggles mutate shared state, so
    // they must run through the case-started AND exam/review lockout gates in BaseCommand.execute
    // — previously super(false) let them bypass the lockout (and contradicted this "not available
    // before game starts" intent).
    super(true);
    this.taskIndex = taskIndex;
    this.isCompleted = isCompleted;
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    context.processUpdateTaskState(getPlayerId(), this.taskIndex, this.isCompleted);
  }

  public int getTaskIndex() {
    return taskIndex;
  }

  @JsonProperty("isCompleted")
  public boolean getIsCompleted() {
    return isCompleted;
  }

  @Override
  public String getDescription() {
    return "Updates the state of a task.";
  }
}

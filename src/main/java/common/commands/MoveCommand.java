package common.commands;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import common.dto.TextMessage;
import common.interfaces.GameActionContext;
import java.io.Serial;

public class MoveCommand extends BaseCommand {
  @Serial
  private static final long serialVersionUID = 1L;
  private final String direction;

  @JsonCreator
  public MoveCommand(@JsonProperty("direction") String direction) {
    super(true);
    if (direction == null || direction.trim().isEmpty()) {
      throw new IllegalArgumentException("Direction cannot be null or empty for MoveCommand.");
    }
    common.WireLimits.requireLength(direction, common.WireLimits.MAX_NAME_LENGTH, "direction");
    this.direction = direction.trim().toLowerCase();
  }

  public String getDirection() {
    return direction;
  }

  @Override
  protected boolean allowedDuringReview() {
    return true; // navigation — the player walks the solved Case while reviewing
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    boolean success = context.movePlayer(getPlayerId(), direction);
    if (!success) {
      context.sendResponseToPlayer(getPlayerId(), new TextMessage("You can't move " + direction + ".", false,
              "game.move.blocked", java.util.List.of(direction)));
    }
  }

  @Override
  public String getDescription() {
    return "Moves your character to a neighboring room. Usage: move [direction]";
  }
}
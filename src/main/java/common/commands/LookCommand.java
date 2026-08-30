package common.commands;

import Core.Room;
import common.dto.RoomDescriptionDTO;
import common.dto.TextMessage;
import common.interfaces.GameActionContext;
import java.io.Serial;

public class LookCommand extends BaseCommand {
  @Serial
  private static final long serialVersionUID = 1L;

  public LookCommand() {
    super(true);
  }

  @Override
  protected boolean allowedDuringReview() {
    return true; // looking at the room is a reference action, usable while reviewing
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    Room currentRoom = context.getCurrentRoomForPlayer(getPlayerId());
    if (currentRoom == null) {
      context.sendResponseToPlayer(
          getPlayerId(), new TextMessage("Error: You are not in a valid room.", true));
      return;
    }

    // Re-describe the CURRENT room through the canonical builder so the DTO carries the room's
    // imagePath (plus object/suspect positions and sprite scales) — exactly as on entry. Building a
    // partial DTO here made RoomView blank the image (.scratch/ingame-fixes-2 issue 01).
    RoomDescriptionDTO roomDTO = context.createRoomDescriptionDTO(currentRoom, getPlayerId());
    context.sendResponseToPlayer(getPlayerId(), roomDTO);
  }

  @Override
  public String getDescription() {
    return "Views your surroundings (current room's description, objects, exits, and occupants).";
  }
}
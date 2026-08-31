package Core;

import common.interfaces.GameContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public abstract class MovableCharacter {
  protected Room currentRoom;
  protected final Random random = new Random();

  public Room getCurrentRoom() {
    return currentRoom;
  }

  public void setCurrentRoom(Room room) {
    this.currentRoom = room;
  }

  /**
   * Moves the character to a random neighboring room.
   *
   * @param context The GameContext object providing access to game data.
   */
  public void randomMove(GameContext context) {
    Map<String, Room> neighbors = currentRoom.getNeighbors();
    List<Room> allowedRooms = new ArrayList<>(neighbors.values());
    if (!allowedRooms.isEmpty()) {
      currentRoom = allowedRooms.get(random.nextInt(allowedRooms.size()));
    }
  }
}

package extractors;

import Core.Room;
import JsonDTO.CaseFile;
import common.interfaces.GameContext;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import JsonDTO.CaseData;

public class BuildingExtractor {

  private static final Set<String> VALID_DIRECTIONS =
          new HashSet<>(Set.of("north", "south", "east", "west", "up", "down"));

  private BuildingExtractor() {} // Utility class

  /**
   * Loads the building structure from the case file and populates the GameContext.
   *
   * @param caseFile The case file containing room and neighbor data.
   * @param context The GameContext (either Server or SinglePlayer) to populate.
   * @return True if the building was successfully loaded without critical errors, false otherwise.
   */
  public static boolean loadBuilding(CaseData caseFile, GameContext context) {
    if (caseFile == null || caseFile.getRooms() == null || caseFile.getStartingRoom() == null) {
      context.logLoadingMessage("Error: CaseFile is null, or has no rooms/starting room defined.");
      return false;
    }

    Map<String, Room> createdRooms = new HashMap<>();
    Set<String> roomNamesProcessed = new HashSet<>();
    boolean hasErrors = false;

    // Step 1: Create all Room instances
    for (CaseFile.RoomData roomData : caseFile.getRooms()) {
      String roomName = roomData.getName();
      if (roomName == null || roomName.trim().isEmpty()) {
        context.logLoadingMessage(
                "Warning: Skipping room with null or empty name in " + context.getContextIdForLog());
        hasErrors = true;
        continue;
      }
      if (!roomNamesProcessed.add(roomName.toLowerCase())) {
        context.logLoadingMessage(
                "Warning: Duplicate room name '"
                        + roomName
                        + "' found. Skipping duplicate in "
                        + context.getContextIdForLog());
        hasErrors = true;
        continue;
      }

      // Using the concrete Room class from 'core'
      // Normalizing imagePath (empty -> null)
      String imagePath = (roomData.getImagePath() != null && !roomData.getImagePath().trim().isEmpty())
                       ? roomData.getImagePath().trim()
                       : null;
      Room room = new Room(roomData.getName(), roomData.getDescription(), imagePath);
      // Per-language room Display Name (.scratch/gui-localized-case-names); null -> falls back.
      room.setDisplayName(roomData.getDisplayName());
      // Per-room authored Dr. Watson position (nullable); null leaves RoomView on its default spot.
      room.setWatsonPosition(roomData.getWatsonPosX(), roomData.getWatsonPosY());
      // Per-room Watson size/orientation (nullable); null → the engine uses the global metadata value.
      room.setWatsonPlacement(
          roomData.getWatsonImageScaleX(),
          roomData.getWatsonImageScaleY(),
          roomData.getWatsonFlipX(),
          roomData.getWatsonFlipY(),
          roomData.getWatsonRotation(),
          roomData.getWatsonLabelDX(),
          roomData.getWatsonLabelDY());
      createdRooms.put(room.getName().toLowerCase(), room);
      context.addRoom(room); // Add to the provided game context
    }

    // Step 2: Link neighbors
    for (CaseFile.RoomData roomData : caseFile.getRooms()) {
      Room currentRoom = createdRooms.get(roomData.getName().toLowerCase());
      if (currentRoom == null) continue; // Was skipped due to error or duplicate

      if (roomData.getNeighbors() != null) {
        for (Map.Entry<String, String> neighborEntry : roomData.getNeighbors().entrySet()) {
          String direction = neighborEntry.getKey().toLowerCase();
          String neighborName = neighborEntry.getValue();

          if (!VALID_DIRECTIONS.contains(direction)) {
            context.logLoadingMessage(
                    "Warning: Invalid direction '"
                            + direction
                            + "' for room '"
                            + currentRoom.getName()
                            + "'. Skipping neighbor link in "
                            + context.getContextIdForLog());
            hasErrors = true;
            continue;
          }

          Room neighborRoom = createdRooms.get(neighborName.toLowerCase());
          if (neighborRoom != null) {
            currentRoom.setNeighbor(direction, neighborRoom);
          } else {
            context.logLoadingMessage(
                    "Warning: Neighbor room '"
                            + neighborName
                            + "' not found for room '"
                            + currentRoom.getName()
                            + "'. Skipping neighbor link in "
                            + context.getContextIdForLog());
            hasErrors = true;
          }
        }
      }
    }

    // Step 3: Validate starting room
    Room startingRoom = createdRooms.get(caseFile.getStartingRoom().toLowerCase());
    if (startingRoom == null) {
      context.logLoadingMessage(
              "Critical Error: Starting room '"
                      + caseFile.getStartingRoom()
                      + "' not found or was invalid. Building load failed for "
                      + context.getContextIdForLog());
      return false; // Critical error
    }
    // Setting the starting room is now the responsibility of the context that *uses* this building,
    // e.g., GameContextSinglePlayer.initializeNewCase or GameContextServer.

    // Step 4: Validate room connectivity (all rooms reachable from starting room)
    try {
      validateRoomConnectivity(context, startingRoom);
    } catch (IllegalStateException e) {
      context.logLoadingMessage(
              "Critical Error: Room connectivity validation failed: "
                      + e.getMessage()
                      + " for "
                      + context.getContextIdForLog());
      return false; // Critical error
    }

    if (hasErrors) {
      context.logLoadingMessage(
              "Building loaded with some non-critical errors/warnings for "
                      + context.getContextIdForLog());
    } else {
      context.logLoadingMessage(
              "Building structure loaded successfully for " + context.getContextIdForLog());
    }
    return true; // Returns true even if there were non-critical warnings
  }

  private static void validateRoomConnectivity(GameContext context, Room startRoom) {
    Set<Room> visited = new HashSet<>();
    Queue<Room> queue = new LinkedList<>();

    if (startRoom == null) {
      throw new IllegalStateException("Start room for connectivity check is null.");
    }

    queue.add(startRoom);
    visited.add(startRoom);

    while (!queue.isEmpty()) {
      Room current = queue.poll();
      for (Room neighbor : current.getNeighbors().values()) {
        if (neighbor != null && !visited.contains(neighbor)) {
          visited.add(neighbor);
          queue.add(neighbor);
        }
      }
    }

    for (Room room : context.getAllRooms().values()) {
      if (!visited.contains(room)) {
        throw new IllegalStateException(
                "Room '"
                        + room.getName()
                        + "' is unreachable from the starting room '"
                        + startRoom.getName()
                        + "'.");
      }
    }
  }
}

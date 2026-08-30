package extractors; // Or your chosen package for extractors

import Core.Room;
import Core.Suspect;
import JsonDTO.CaseData;
import JsonDTO.CaseFile;
import common.interfaces.GameContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

// ... other necessary imports for loadSuspects if it's in this file ...

public class SuspectExtractor {

  // Custom Exception for No Valid Rooms (if not already defined elsewhere)
  public static class NoValidRoomsException extends Exception {
    public NoValidRoomsException(String message) {
      super(message);
    }
  }

  private SuspectExtractor() {} // Utility class

  // Assuming your loadSuspects method is also in this class
  public static void loadSuspects(CaseData caseFile, GameContext context)
      throws NoValidRoomsException { // Make it throw NoValidRoomsException
    if (caseFile == null || caseFile.getSuspects() == null) {
      context.logLoadingMessage(
          "Warning: No suspects defined in case file or case file is null for "
              + context.getContextIdForLog());
      return;
    }

    Set<String> suspectNames = new HashSet<>(); // To check for duplicates

    for (CaseFile.SuspectData suspectData : caseFile.getSuspects()) {
      String suspectName = suspectData.getName();
      if (suspectName == null || suspectName.trim().isEmpty()) {
        context.logLoadingMessage(
            "Warning: Skipping suspect with null or empty name in " + context.getContextIdForLog());
        continue;
      }
      if (!suspectNames.add(suspectName.toLowerCase())) {
        context.logLoadingMessage(
            "Warning: Duplicate suspect name '"
                + suspectName
                + "' found. Skipping duplicate in "
                + context.getContextIdForLog());
        continue;
      }

      // Derive ID if missing
      String id = suspectData.getId();
      if (id == null || id.trim().isEmpty()) {
        id = suspectName.toLowerCase().replace(" ", "_");
      } else {
        id = id.trim();
      }

      // Normalize imagePath
      String imagePath =
          (suspectData.getImagePath() != null && !suspectData.getImagePath().trim().isEmpty())
              ? suspectData.getImagePath().trim()
              : null;

      Suspect suspect =
          new Suspect(
              id,
              suspectData.getName(),
              suspectData.getStatement(),
              suspectData.getClue(),
              imagePath);
      if (suspectData.getImageScale() != null) {
        suspect.setImageScale(suspectData.getImageScale()); // legacy uniform → both axes
      }
      if (suspectData.getImageScaleX() != null) {
        suspect.setImageScaleX(suspectData.getImageScaleX());
      }
      if (suspectData.getImageScaleY() != null) {
        suspect.setImageScaleY(suspectData.getImageScaleY());
      }
      suspect.setFlipX(Boolean.TRUE.equals(suspectData.getFlipX()));
      suspect.setFlipY(Boolean.TRUE.equals(suspectData.getFlipY()));
      if (suspectData.getRotation() != null) {
        suspect.setRotation(suspectData.getRotation());
      }
      // Per-language suspect Display Name (.scratch/gui-localized-case-names); null -> falls back
      // to
      // the Universal name in the GUI popup.
      suspect.setDisplayName(suspectData.getDisplayName());

      // Authored placement (DEC-5): home room + normalized render position + stationary flag.
      suspect.setHomeRoom(suspectData.getHomeRoom());
      suspect.setPosition(suspectData.getPosX(), suspectData.getPosY());
      // Authored name-label offset (Case Maker placement tab); null leaves RoomView's default.
      suspect.setLabelOffset(suspectData.getLabelDX(), suspectData.getLabelDY());
      suspect.setStationary(suspectData.isStationary());

      // Load state data if available
      if (suspectData.getStates() != null && !suspectData.getStates().isEmpty()) {
        suspect.setStateData(suspectData.getStates());
        suspect.setInitialState(suspectData.getInitialState());
      } else {
        // Backward compatibility: create default LIE state if needed?
        // Suspect constructor handles fallback, but we could explicitly map 'statement' to LIE
        // here.
        // For now, Suspect logic handles fallback via getStatement(), so no extra work needed here.
      }

      try {
        // Honor the authored home room when it resolves; otherwise fall back to the historical
        // random placement (legacy cases without a home room keep working).
        Room home =
            suspect.getHomeRoom() == null ? null : context.getRoomByName(suspect.getHomeRoom());
        Room startingRoom =
            home != null
                ? home
                : assignRandomStartingRoom(suspect, context, context.getContextIdForLog());
        suspect.setCurrentRoom(startingRoom);
        context.addSuspect(suspect);
      } catch (NoValidRoomsException e) {
        // Log it, and decide if this is critical.
        // If one suspect can't be placed, it might be okay to continue, or you might want to fail
        // loading.
        // For now, let's log and re-throw if loadSuspects is declared to throw it.
        context.logLoadingMessage(
            "Error placing suspect '"
                + suspect.getName()
                + "': "
                + e.getMessage()
                + " for "
                + context.getContextIdForLog());
        throw e; // Or handle by skipping this suspect and continuing
      }
    }
  }

  /**
   * Assigns a random starting room to a suspect from the available rooms in the context.
   *
   * @param suspect The suspect to assign a room to.
   * @param context The game context containing all available rooms.
   * @param contextId A string identifier for the context, for logging purposes.
   * @return A randomly selected Room.
   * @throws NoValidRoomsException if no rooms are available in the context.
   */
  private static Room assignRandomStartingRoom(
      Suspect suspect, GameContext context, String contextId) throws NoValidRoomsException {

    if (context == null || context.getAllRooms() == null) {
      throw new NoValidRoomsException(
          "Game context or room list is null, cannot assign room for suspect: "
              + suspect.getName()
              + " in "
              + contextId);
    }

    // CORRECTED LINE:
    Collection<Room> allRoomsCollection = context.getAllRooms().values();

    if (allRoomsCollection.isEmpty()) {
      throw new NoValidRoomsException(
          "No rooms available in context '"
              + contextId
              + "' to assign to suspect: "
              + suspect.getName());
    }

    // Convert collection to list to get random element by index
    List<Room> roomList = new ArrayList<>(allRoomsCollection);

    // Optional: Filter out rooms if SuspectData had 'allowedRooms' or 'disallowedRooms'
    // For now, just pick from any available room.

    return roomList.get(new Random().nextInt(roomList.size()));
  }
}

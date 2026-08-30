package common.interfaces;

import Core.Room;
import Core.Suspect;
import java.util.Map;

/**
 * GameContext This interface is all about what the *Extractors* need when they're loading a case
 * from a JSON file. Think of it as the "setup phase" context. It allows adding rooms and suspects,
 * and getting them by name, plus some logging. Both my SinglePlayer and Server contexts will
 * implement this.
 */
public interface GameContext {

  /**
   * Adds a created Room object to this game context. The extractor will call this after parsing
   * room data from the JSON.
   *
   * @param room The fully instantiated Room object to add.
   */
  void addRoom(Room room);

  /**
   * Fetches a previously added Room object by its unique name. Extractors (like BuildingExtractor
   * when linking neighbors) will use this.
   *
   * @param name The name of the room (should be case-insensitive in implementations).
   * @return The Room object if it exists in the context, otherwise null.
   */
  Room getRoomByName(String name);

  /**
   * Provides access to all rooms currently loaded in the context. Useful for things like building
   * connectivity validation after all rooms are added.
   *
   * @return An unmodifiable Map of room names (keys) to Room objects (values). Implementations
   *     should return a copy or unmodifiable view.
   */
  Map<String, Room> getAllRooms();

  /**
   * Adds a created Suspect object to this game context. The SuspectExtractor will call this after
   * parsing suspect data.
   *
   * @param suspect The fully instantiated Suspect object to add.
   */
  void addSuspect(Suspect suspect);

  /**
   * For logging messages specifically during the case loading/extraction phase. Helps me debug if a
   * case JSON has issues.
   *
   * @param message The informational or error message related to loading.
   */
  void logLoadingMessage(String message);

  /**
   * Gets a unique identifier for this specific context instance. Super useful for prefixing log
   * messages to know if it's from SP, or which server session, especially when looking at combined
   * logs.
   *
   * @return A String like "SinglePlayer" or "ServerSession-123xyz".
   */
  String getContextIdForLog();

  /**
   * Retrieves the path to the image representing Dr. Watson, if configured in the case metadata.
   *
   * @return The image path string, or null if not configured.
   */
  default String getWatsonImagePath() {
      return null;
  }
}

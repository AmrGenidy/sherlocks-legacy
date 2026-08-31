package engine;

import java.io.Serializable;

/**
 * Output seam of the {@link GameEngine}: every player-visible event the engine produces leaves
 * through this interface (ADR-0001). The engine never talks to a transport directly.
 *
 * <p>The multiplayer adapter routes {@code toPlayer} to the addressed {@code ClientSession} and
 * {@code toAll} to the {@code GameSession} broadcast. The single-player adapter routes both to its
 * in-process {@code GameOutputSink} / state listener — a direct method call, never a socket
 * (ROADMAP Hard Constraint 1).
 */
public interface GameEventListener {

  /** Emits one event to a single player. */
  void toPlayer(String playerId, Serializable event);

  /**
   * Emits one event to every player in the session.
   *
   * @param excludePlayerId player to skip, or {@code null} to include everyone
   */
  void toAll(Serializable event, String excludePlayerId);
}

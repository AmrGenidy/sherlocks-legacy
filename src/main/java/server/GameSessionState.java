package server;

/**
 * GameSessionState This enum defines the possible states a GameSession can be in. Helps me manage
 * the lifecycle of a game from creation to completion or termination.
 */
public enum GameSessionState {
  LOADING, // Initial state: Session is being created, case data is loading.
  // Not yet ready for players to fully interact beyond basic connection.

  WAITING_FOR_PLAYERS, // Post-loading & host joined: Lobby is open (if public), waiting for P2.
  // Host is in, but game can't start.

  IN_LOBBY_AWAITING_START, // Both players have joined. Session is full.
  // Now just waiting for the host to issue the 'start case' command.
  // Chat might be enabled here.

  ACTIVE, // Game case has officially started (e.g., after 'start case' command).
  // Both players are actively playing. All game commands are valid.

  ENDED_NORMAL, // Game concluded as expected (e.g., case solved via final exam, or a win/loss
  // condition met).
  // Session will be cleaned up.

  ENDED_ABANDONED, // Game ended prematurely (e.g., a player disconnected, quit, or server admin
  // action).
  // Session will be cleaned up.

  ERROR // Session encountered an unrecoverable error during setup or gameplay.
  // (e.g., failed to load critical case data). Session should be cleaned up.
}

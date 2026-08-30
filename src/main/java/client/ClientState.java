package client;

public enum ClientState {
  // Connection States
  DISCONNECTED(true), // User can type 'connect' or 'quit'
  CONNECTING(false), // Waits for connection
  RECONNECTING(false), // Waits for reconnection

  // Lobby / Main Menu States
  CONNECTED_IDLE(true), // Main menu, interactive

  // Hosting Flow
  SELECTING_HOST_TYPE(true), // Interactive menu
  REQUESTING_CASE_LIST_FOR_HOST(false), // Waits for server response
  SELECTING_HOST_CASE(true), // Interactive menu (after getting cases)
  SELECTING_HOST_LANGUAGE(true), // NEW: Interactive menu for choosing a language
  SENDING_HOST_REQUEST(false), // Waits for server response
  HOSTING_LOBBY_WAITING(true), // Interactive (chat, 'exit lobby')

  // Joining Flow
  SELECTING_JOIN_TYPE(true), // Interactive menu
  REQUESTING_PUBLIC_GAMES(false), // Waits for server response
  VIEWING_PUBLIC_GAMES(true), // Interactive menu (after getting games)
  SENDING_JOIN_PUBLIC_REQUEST(false), // Waits for server response
  ENTERING_PRIVATE_CODE(true), // Interactive (typing code or 'cancel')
  SENDING_JOIN_PRIVATE_REQUEST(false), // Waits for server response

  // In-Session / In-Game States
  SHOWING_INVITATION(true), // Special state for showing the invitation screen
  IN_LOBBY_AWAITING_START(true), // Interactive (host: 'start case', guest: 'request...', chat)
  IN_GAME(true), // Fully interactive game play

  // Exam Flow States (primarily for host)
  ATTEMPTING_FINAL_EXAM(false), // Host sent "final exam", waiting for first Question DTO
  ANSWERING_FINAL_EXAM_Q(true), // Host received a question, typing answer
  SUBMITTING_EXAM_ANSWER(false), // Host sent an answer, waiting for next Q DTO or Result DTO
  VIEWING_EXAM_RESULT(true), // Results displayed, user might press Enter to continue (transient state)

  // Terminal State
  EXITING(false); // Client is shutting down, not interactive

  private final boolean interactive;

  ClientState(boolean isInteractive) {
    this.interactive = isInteractive;
  }

  /**
   * Checks if this client state is one where the user is expected to provide input via the main
   * command prompt (not just system messages).
   *
   * @return true if the state is interactive, false otherwise.
   */
  public boolean isInteractive() {
    return interactive;
  }

  /**
   * Checks if this client state is primarily a "waiting" state, meaning it's not interactive and
   * not a terminal/disconnected state where specific actions like 'connect' or 'quit' are expected.
   *
   * @return true if it's a waiting state.
   */
  public boolean isPrimarilyWaiting() {
    // A waiting state is non-interactive AND not one of the fundamental
    // non-connected or exiting states.
    return !interactive
            && this != CONNECTING
            && this != RECONNECTING
            && this != DISCONNECTED // DISCONNECTED is interactive (for 'connect'/'quit')
            && this != EXITING;
  }
}

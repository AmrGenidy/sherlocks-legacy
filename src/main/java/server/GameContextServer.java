package server;

import Core.*;
import JsonDTO.CaseData;
import common.commands.*;
import common.dto.*;
import common.interfaces.GameActionContext;
import engine.GameEngine;
import engine.GameEventListener;
import engine.PlayerSet;
import java.io.Serializable;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GameContextServer implements GameActionContext {

  private static final Logger logger = LoggerFactory.getLogger(GameContextServer.class);

  private final GameSession gameSession; // Reference back to the session for communication
  // Player specific state - managed by player IDs
  private Detective player1Detective;
  private Detective player2Detective;
  private String player1Id; // Host
  private String player2Id; // Guest

  // All shared game state and rules live in the engine (ADR-0001); this context is its
  // multiplayer adapter: transport routing, Host gating, and Game Session lifecycle.
  private final GameEngine engine;

  private final CaseData selectedCase;

  public GameContextServer(
      GameSession gameSession, CaseData selectedCase, String p1Id, String p2Id) {
    this.gameSession = Objects.requireNonNull(gameSession, "GameSession cannot be null");
    this.selectedCase =
        Objects.requireNonNull(selectedCase, "SelectedCase (CaseData) cannot be null");
    // Player IDs can be null initially if P2 hasn't joined when context is first
    // created by
    // GameSession constructor
    this.player1Id = p1Id;
    this.player2Id = p2Id;

    this.engine =
        new GameEngine(
            new ServerPlayerSet(),
            new GameEventListener() {
              @Override
              public void toPlayer(String playerId, Serializable event) {
                sendResponseToPlayer(playerId, event);
              }

              @Override
              public void toAll(Serializable event, String excludePlayerId) {
                broadcastToSession(event, excludePlayerId);
              }
            });

    resetForNewCaseLoad(); // Initialize all collections and states

    if (p1Id != null) this.player1Detective = new Detective(p1Id);
    if (p2Id != null) this.player2Detective = new Detective(p2Id);
  }

  // Called by GameSession when P2 joins or if context needs re-init with both
  // players
  public void setPlayerIds(String p1Id, String p2Id) {
    this.player1Id = p1Id;
    this.player2Id = p2Id;

    if (p1Id != null) {
      if (this.player1Detective == null || !this.player1Detective.getPlayerId().equals(p1Id)) {
        this.player1Detective = new Detective(p1Id);
      }
    } else {
      this.player1Detective = null;
    }

    if (p2Id != null) {
      if (this.player2Detective == null || !this.player2Detective.getPlayerId().equals(p2Id)) {
        this.player2Detective = new Detective(p2Id);
      }
      // *** ADDED/MODIFIED: Ensure P2's room is set if game is ready ***
      if (this.selectedCase != null
          && this.selectedCase.getStartingRoom() != null
          && this.player2Detective != null) {
        Room startingRoom = getRoomByName(this.selectedCase.getStartingRoom());
        if (startingRoom != null) {
          this.player2Detective.setCurrentRoom(startingRoom);
          logGameMessage(
              "Player 2 (" + p2Id + ") position set to starting room: " + startingRoom.getName());
        } else {
          logGameMessage(
              "Warning: Could not set starting room for Player 2 ("
                  + p2Id
                  + ") upon ID set - starting room not found.");
        }
      }
    } else {
      this.player2Detective = null;
    }
    // If initializePlayerStartingState() is robust enough to handle being called
    // multiple times
    // or if it checks if players already have rooms, you could call it here.
    // For now, direct setting is more targeted.
    // initializePlayerStartingState(); // Re-evaluate if this is needed here
  }

  public void resetForNewCaseLoad() {
    engine.resetWorld();
    engine.loadCase(selectedCase);
    logGameMessage(
        "Init sharedInsightTokens from case file: startingInsightTokens="
            + selectedCase.getStartingInsightTokens()
            + ", sharedInsightTokens="
            + engine.getSharedInsightTokens());

    if (selectedCase.getTasks() == null) {
      logGameMessage("Warning: No tasks found in selected case '" + selectedCase.getTitle() + "'.");
    }
    if (selectedCase.getStructuredWatsonHints() == null
        || selectedCase.getStructuredWatsonHints().isEmpty()) {
      logGameMessage(
          "Warning: No Watson hints found in selected case '" + selectedCase.getTitle() + "'.");
    }

    if (player1Detective != null) player1Detective.resetForNewCase();
    if (player2Detective != null) player2Detective.resetForNewCase();
  }

  public void initializePlayerStartingState() {
    if (selectedCase.getStartingRoom() == null) {
      logGameMessage(
          "CRITICAL Error: Cannot initialize player state, selected case has no startingRoom defined.");
      gameSession.endSession(
          "Configuration error: No starting room defined."); // End session if critical
      return;
    }
    if (!engine.initializeStartingState()) {
      logGameMessage("CRITICAL Error: No rooms loaded at all. Cannot set starting room.");
      gameSession.endSession("Configuration error: No rooms loaded."); // End session
      return;
    }
    logGameMessage(
        "Initialized player states. Starting room: "
            + (getCurrentRoomForPlayer(player1Id) != null
                ? getCurrentRoomForPlayer(player1Id).getName()
                : "unknown"));
  }

  private void logGameMessage(String message) {
    // This now uses the GameSession's getSessionId() to add context to the log
    // message.
    logger.info("[SESS_CTX:{}] {}", gameSession.getSessionId(), message);
  }

  // --- GameContext Implementation (for Extractors) ---
  @Override
  public void addRoom(Room room) {
    engine.addRoom(room);
  }

  @Override
  public Room getRoomByName(String name) {
    return engine.getRoomByName(name);
  }

  @Override
  public common.dto.RoomDescriptionDTO createRoomDescriptionDTO(Room room, String playerId) {
    return engine.buildRoomDescription(room, playerId);
  }

  @Override
  public Map<String, Room> getAllRooms() {
    return engine.getAllRooms();
  }

  @Override
  public void addSuspect(Suspect suspect) {
    engine.addSuspect(suspect);
  }

  @Override
  public void logLoadingMessage(String message) {
    // We can differentiate loader messages with a specific marker
    logger.info("[LOADER] {}", message);
  }

  @Override
  public String getContextIdForLog() {
    return "ServerSess-" + gameSession.getSessionId();
  }

  // --- GameActionContext Implementation (for Commands) ---
  @Override
  public boolean isCaseStarted() {
    return engine.isCaseStarted();
  }

  @Override
  public boolean isExamActive() {
    return engine.isExamActive();
  }

  @Override
  public void setCaseStarted(boolean started) {
    // Prevent re-entry or redundant calls if state is already set
    if (engine.isCaseStarted() == started && started) {
      logGameMessage("setCaseStarted(true) called, but case was already started.");
      return;
    }
    if (!started && !engine.isCaseStarted()) { // Trying to stop an already stopped case
      logGameMessage("setCaseStarted(false) called, but case was already not started.");
      return;
    }

    engine.setCaseStartedFlag(started);

    if (started) {
      logGameMessage(
          "Case '"
              + (selectedCase != null ? selectedCase.getTitle() : "Unknown")
              + "' is being started.");

      // *** NOTIFY GameSession TO UPDATE ITS STATE ***
      if (this.gameSession != null) {
        this.gameSession.setSessionState(GameSessionState.ACTIVE); // New method in GameSession
      } else {
        logGameMessage(
            "CRITICAL ERROR: gameSession is null in GameContextServer. Cannot update session state.");
        // This would be a major issue.
      }
      // *** END NOTIFICATION ***

      engine.announceCaseStart(); // Now broadcast all the initial game data
    } else {
      logGameMessage(
          "Case '"
              + (selectedCase != null ? selectedCase.getTitle() : "Unknown")
              + "' has been stopped/reset (caseStarted=false).");
    }
  }

  @Override
  public CaseData getSelectedCase() {
    return selectedCase;
  }

  @Override
  public Detective getPlayerDetective(String playerId) {
    if (playerId == null) return null;
    if (player1Id != null && player1Id.equals(playerId)) return player1Detective;
    if (player2Id != null && player2Id.equals(playerId)) return player2Detective;
    logGameMessage("Warning: getPlayerDetective called for unknown or null playerId: " + playerId);
    return null;
  }

  @Override
  public Room getCurrentRoomForPlayer(String playerId) {
    return engine.getCurrentRoomForPlayer(playerId);
  }

  @Override
  public String getOccupantsDescriptionInRoom(Room room, String askingPlayerId) {
    return engine.getOccupantsDescriptionInRoom(room, askingPlayerId);
  }

  @Override
  public TaskList getTaskList() {
    return engine.getTaskList();
  }

  @Override
  public DoctorWatson getWatson() {
    return engine.getWatson();
  }

  @Override
  public List<Suspect> getAllSuspects() {
    return engine.getAllSuspects();
  }

  @Override
  public boolean movePlayer(String playerId, String direction) {
    return engine.movePlayer(playerId, direction);
  }

  @Override
  public void broadcastMessage(TextMessage message) {
    broadcastToSession(message, null);
  }

  @Override
  public void addJournalEntry(JournalEntryDTO entry) {
    engine.addJournalEntry(entry);
  }

  @Override
  public List<JournalEntryDTO> getJournalEntries(String playerId) {
    return engine.getJournalEntries();
  }

  // --- New Journal Query Implementations ---
  @Override
  public List<JournalEntryDTO> getJournalEntriesByType(String playerId, JournalEntryType type) {
    return engine.getJournalEntriesByType(type);
  }

  @Override
  public List<JournalEntryDTO> getJournalEntriesBySourceId(String playerId, String sourceId) {
    return engine.getJournalEntriesBySourceId(sourceId);
  }

  @Override
  public JournalEntryDTO getJournalEntryById(String playerId, String entryId) {
    return engine.getJournalEntryById(entryId);
  }

  @Override
  public Map<JournalEntryType, List<JournalEntryDTO>> getJournalEntriesGroupedByType(
      String playerId) {
    return engine.getJournalEntriesGroupedByType();
  }

  @Override
  public void sendResponseToPlayer(String playerId, Serializable responseDto) {
    ClientSession client = gameSession.getClientSessionById(playerId);
    if (client != null) {
      client.send(responseDto);
    } else {
      logGameMessage(
          "Error: Attempted to send DTO to null or disconnected client: "
              + playerId
              + ". DTO: "
              + responseDto.getClass().getSimpleName());
    }
  }

  @Override
  public void broadcastToSession(Serializable dto, String excludePlayerId) {
    gameSession.broadcast(dto, excludePlayerId);
  }

  @Override
  public void notifyPlayerMove(String movingPlayerId, Room newRoom, Room oldRoom) {
    engine.notifyPlayerMove(movingPlayerId, newRoom, oldRoom);
  }

  // NEW: Token Sync
  public void broadcastTokenUpdate() {
    // Create and send the DTO with current consolidated token count
    common.dto.InsightTokenUpdateDTO dto =
        new common.dto.InsightTokenUpdateDTO(engine.getSharedInsightTokens());
    broadcastToSession(dto, null);
    logGameMessage("Broadcasting shared insight tokens update: " + engine.getSharedInsightTokens());
  }

  // --- Exam Logic (lifecycle engine-owned; host gating stays session-layer) ---
  @Override
  public boolean canStartFinalExam(String playerId) {
    boolean isHost = isPlayerHost(playerId);
    if (!isHost) {
      logGameMessage("Player " + playerId + " (guest) attempted to start exam directly. Denied.");
    }
    boolean conditionsMet = engine.canStartFinalExam();
    if (!conditionsMet) {
      logGameMessage(
          "Exam conditions not met for player "
              + playerId
              + " (isCaseStarted: "
              + isCaseStarted()
              + ", examActive: "
              + engine.isExamActive()
              + ")");
    }
    return isHost && conditionsMet;
  }

  @Override
  public void startExamProcess(String playerId) { // playerId is the initiator
    if (!canStartFinalExam(playerId)) {
      sendResponseToPlayer(
          playerId,
          new TextMessage(
              "You cannot start the final exam at this time (not host or conditions not met).",
              true));
      return;
    }
    logGameMessage(
        "Interactive final exam initiated by host: "
            + playerId
            + ". Sending first question to all.");
    engine.startExam(playerId);
  }

  @Override
  public void processSubmitQuestionAnswer(
      String playerId, int questionIndex, Map<String, String> answers) {
    if (!isPlayerHost(playerId)) {
      sendResponseToPlayer(playerId, new TextMessage("Only the host can answer the exam.", true,
              "mp.exam.onlyHost", null));
      return;
    }
    engine.submitExamAnswer(playerId, questionIndex, answers);
  }

  public boolean isPlayerHost(String playerId) {
    return this.player1Id != null && this.player1Id.equals(playerId);
  }

  // Inside server.GameContextServer.java
  @Override
  public void processRequestStartCase(String requestingPlayerId) {
    logGameMessage(
        "PROCESS_REQUEST_START_CASE: by PlayerId="
            + requestingPlayerId
            + ", CaseStarted="
            + isCaseStarted()
            + ", IsHost="
            + isPlayerHost(requestingPlayerId));

    if (isCaseStarted()) {
      sendResponseToPlayer(
          requestingPlayerId, new TextMessage("The case has already started.", false,
              "game.case.alreadyStarted", null));
      return;
    }
    if (isPlayerHost(requestingPlayerId)) { // If host typed "request start case"
      sendResponseToPlayer(
          requestingPlayerId,
          new TextMessage("As host, you can directly use the 'start case' command.", false,
              "mp.host.useStartCase", null));
      return;
    }

    // If it's a guest making the request
    if (player1Id != null) { // Check if host (player1Id) is actually connected/present
      ClientSession requestingPlayerSession = gameSession.getClientSessionById(requestingPlayerId);
      String requesterDisplay =
          (requestingPlayerSession != null)
              ? requestingPlayerSession.getDisplayId()
              : "Your partner (" + requestingPlayerId.substring(0, 4) + "..)";

      // Send prompt to HOST (player1Id)
      logGameMessage(
          "PROCESS_REQUEST_START_CASE: Sending prompt to host "
              + player1Id
              + " about request from "
              + requestingPlayerId);
      sendResponseToPlayer(
          player1Id,
          new TextMessage(
              requesterDisplay + " has requested to start the case. Type 'start case' to begin.",
              false,
              "mp.host.guestRequestedStart",
              java.util.List.of(requesterDisplay)));

      // Send confirmation to GUEST (requestingPlayerId)
      sendResponseToPlayer(
          requestingPlayerId,
          new TextMessage("Request sent to the host to start the case.", false,
              "mp.host.startRequested", null));
    } else {
      logGameMessage(
          "PROCESS_REQUEST_START_CASE: Host (player1Id) is null or not available. Cannot process request from "
              + requestingPlayerId);
      sendResponseToPlayer(
          requestingPlayerId,
          new TextMessage("The host is not currently available to start the case.", true,
              "mp.host.startUnavailable", null));
    }
  }

  @Override
  public void processRequestInitiateExam(String requestingPlayerId) {
    if (!isCaseStarted()) {
      sendResponseToPlayer(
          requestingPlayerId,
          new TextMessage("The case has not started yet. Cannot request exam.", true,
              "mp.exam.notStartedYet", null));
      return;
    }
    if (engine.isExamActive()) {
      sendResponseToPlayer(
          requestingPlayerId, new TextMessage("An exam is already in progress.", false,
              "mp.exam.alreadyInProgress", null));
      return;
    }
    if (isPlayerHost(requestingPlayerId)) {
      sendResponseToPlayer(
          requestingPlayerId,
          new TextMessage("As host, you can directly use 'final exam' to initiate.", false,
              "mp.host.useFinalExam", null));
      return;
    }
    // Guest is requesting
    if (player1Id != null) { // If host is present
      ClientSession requestingPlayerSession = gameSession.getClientSessionById(requestingPlayerId);
      String requesterDisplay =
          requestingPlayerSession != null ? requestingPlayerSession.getDisplayId() : "Your partner";

      sendResponseToPlayer(
          player1Id, new FinalExamRequestDTO(requestingPlayerId, requesterDisplay));
      sendResponseToPlayer(
          requestingPlayerId,
          new TextMessage("Request sent to host to initiate the final exam.", false,
              "mp.exam.requestSent", null));
      logGameMessage(
          "Player "
              + requestingPlayerId
              + " requested final exam. Host "
              + player1Id
              + " notified.");
    } else {
      sendResponseToPlayer(
          requestingPlayerId, new TextMessage("Host is not available to start the exam.", true,
              "mp.exam.hostUnavailable", null));
    }
  }

  @Override
  public void processUpdateDisplayName(String playerId, String newDisplayName) {
    ClientSession client = gameSession.getClientSessionById(playerId);
    if (client != null) {
      String oldDisplayName = client.getDisplayId();
      if (newDisplayName != null
          && !newDisplayName.equals(oldDisplayName)
          && !newDisplayName.trim().isEmpty()
          && newDisplayName.length() < 25) {
        client.setDisplayId(newDisplayName); // Update on the ClientSession object
        logGameMessage(
            "Player "
                + playerId
                + " (formerly "
                + oldDisplayName
                + ") changed display name to "
                + newDisplayName);

        // Broadcast the change to all players in the session
        PlayerNameChangedDTO pncDTO =
            new PlayerNameChangedDTO(playerId, oldDisplayName, newDisplayName);
        broadcastToSession(
            pncDTO, null); // Send to all, including the changer for confirmation sync

        // If this player is hosting a public game that's still in the lobby list,
        // the GameSessionManager needs to be notified to update the public game info.
        gameSession.notifyNameChangeToManagerIfHost(playerId, newDisplayName);

      } else {
        assert newDisplayName != null;
        if (newDisplayName.equals(oldDisplayName)) {
          // Name is the same, just confirm back to sender if needed (optional)
          sendResponseToPlayer(
              playerId,
              new TextMessage("Your display name is already " + newDisplayName + ".", false));
        } else {
          sendResponseToPlayer(
              playerId, new TextMessage("New display name is invalid or too long.", true));
        }
      }
    } else {
      logGameMessage("Error: processUpdateDisplayName received for unknown playerId: " + playerId);
    }
  }

  @Override
  public void processUpdateAvatar(String playerId, String avatarId) {
    ClientSession client = gameSession.getClientSessionById(playerId);
    if (client == null) {
      logGameMessage("Error: processUpdateAvatar received for unknown playerId: " + playerId);
      return;
    }
    // Allowlist check: only a known preset id is accepted — a hostile peer cannot inject an
    // arbitrary string/path. An unknown id is dropped, keeping the previous avatar.
    if (!common.PlayerAvatars.isValid(avatarId)) {
      logGameMessage(
          "Ignoring avatar update for player " + playerId + ": unknown preset id " + avatarId);
      return;
    }
    String oldAvatarId = client.getAvatarId();
    if (avatarId.equals(oldAvatarId)) {
      return; // no change
    }
    client.setAvatarId(avatarId);
    logGameMessage("Player " + playerId + " chose avatar " + avatarId);
    // Broadcast to all (including the chooser, for confirmation sync), like name changes.
    broadcastToSession(new PlayerAvatarChangedDTO(playerId, oldAvatarId, avatarId), null);
  }

  @Override
  public void updateNpcMovements(String triggeringPlayerId) {
    engine.updateNpcMovements(triggeringPlayerId);
  }

  @Override
  public void handlePlayerExitRequest(String playerId) {
    logger.info(
        "[SESS_CTX:{}] Player {} has requested to exit the game session.",
        gameSession.getSessionId(),
        playerId);
    // Delegate the actual session termination and notification to GameSession
    gameSession.playerRequestsExit(playerId);
  }

  @Override
  public void handlePlayerCancelLobby(String playerId) {
    logger.info(
        "[SESS_CTX:{}] Player {} is cancelling lobby participation.",
        gameSession.getSessionId(),
        playerId);
    gameSession.playerCancelsLobby(playerId);
  }

  public void executeCommand(Command command) { // This is the method called by GameSession
    if (command == null) {
      /* ... */
      return;
    }
    if (command.getPlayerId() == null) {
      /* ... */
      return;
    }

    logGameMessage(
        "Context executing command: "
            + command.getClass().getSimpleName()
            + " for player "
            + command.getPlayerId());

    // --- HOST CHECKS ---
    if (command instanceof StartCaseCommand) {
      if (!isPlayerHost(command.getPlayerId())) {
        sendResponseToPlayer(
            command.getPlayerId(),
            new TextMessage(
                "Only the host can directly start the case. Guests can use 'request start case'.",
                true));
        return;
      }
    } else if (command instanceof InitiateFinalExamCommand) {
      if (!isPlayerHost(command.getPlayerId())) {
        processRequestInitiateExam(command.getPlayerId());
        return;
      }
    }
    // --- END HOST CHECKS ---

    command.execute(this);
  }

  @Override
  public void processUpdateTaskState(String playerId, int taskIndex, boolean isCompleted) {
    logGameMessage(
        "Player "
            + playerId
            + " requested task "
            + taskIndex
            + " state: "
            + (isCompleted ? "Completed" : "Incomplete"));
    engine.processUpdateTaskState(playerId, taskIndex, isCompleted);
  }

  @Override
  public Map<Integer, Boolean> getTaskStates() {
    return engine.getTaskStates();
  }

  @Override
  public int getSessionDeduceCount() {
    return engine.getSessionDeduceCount();
  }

  @Override
  public boolean trySpendInsightToken() {
    return engine.trySpendInsightToken();
  }

  @Override
  public void awardInsightToken() {
    engine.awardInsightToken();
  }

  @Override
  public void incrementSessionDeduceCount() {
    engine.incrementSessionDeduceCount();
  }

  @Override
  public void processContinueGame(String playerId) {
    // In Multiplayer, continue means re-sending the current room view to all players
    // so their UI updates and exits the exam view.
    engine.processContinueGame(playerId);
  }

  @Override
  public String getWatsonImagePath() {
    if (selectedCase != null) {
      return selectedCase.getWatsonImagePath();
    }
    return null;
  }

  /**
   * Returns the current shared insight-token balance for the session. Mirrors {@code
   * GameContextSinglePlayer#getSharedInsightTokens()} so both contexts expose the balance through
   * an identical read-only accessor (used by the engine test suite to assert exact balances).
   */
  public int getSharedInsightTokens() {
    return engine.getSharedInsightTokens();
  }

  @Override
  public boolean trySpendInsightTokens(int amount) {
    return engine.trySpendInsightTokens(amount);
  }

  @Override
  public WatsonHintResponseDTO askWatsonAboutTarget(String playerId, String targetName) {
    return engine.askWatsonAboutTarget(this, playerId, targetName);
  }

  @Override
  public WatsonHintResponseDTO askWatsonForHint(String playerId) {
    return engine.askWatsonForHint(this, playerId);
  }

  // --- Cooldown Implementation (engine-owned) ---
  @Override
  public void reportCombineSuccess() {
    engine.reportCombineSuccess();
  }

  @Override
  public void reportCombineFailure() {
    engine.reportCombineFailure();
  }

  @Override
  public boolean isCombineOnCooldown() {
    return engine.isCombineOnCooldown();
  }

  @Override
  public long getCombineCooldownRemaining() {
    return engine.getCombineCooldownRemaining();
  }

  @Override
  public void reportContradictSuccess() {
    engine.reportContradictSuccess();
  }

  @Override
  public void reportContradictFailure() {
    engine.reportContradictFailure();
  }

  @Override
  public boolean isContradictOnCooldown() {
    return engine.isContradictOnCooldown();
  }

  @Override
  public long getContradictCooldownRemaining() {
    return engine.getContradictCooldownRemaining();
  }

  /** The host/guest pair as the engine's {@link PlayerSet} seam. */
  private final class ServerPlayerSet implements PlayerSet {
    @Override
    public Detective detectiveFor(String playerId) {
      return getPlayerDetective(playerId);
    }

    @Override
    public List<Detective> detectives() {
      List<Detective> detectives = new ArrayList<>(2);
      if (player1Detective != null) detectives.add(player1Detective);
      if (player2Detective != null) detectives.add(player2Detective);
      return detectives;
    }

    @Override
    public String displayName(String playerId) {
      ClientSession session = gameSession.getClientSessionById(playerId);
      return session != null ? session.getDisplayId() : "Player";
    }

    @Override
    public boolean isSolo() {
      return false;
    }
  }
}

package server;

import JsonDTO.CaseData;
import common.commands.Command;
import common.commands.pinboard.PinboardStateResponseCommand;
import common.commands.pinboard.RequestPinboardStateCommand;
import common.commands.pinboard.UpdatePinboardCommand;
import common.dto.ChatMessage;
import common.dto.LobbyUpdateDTO;
import common.dto.ReturnToLobbyDTO;
import common.dto.TextMessage;
import common.dto.pinboard.PinboardStateDTO;
import common.dto.pinboard.PinboardUpdateDTO;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GameSession {
  private static final Logger logger = LoggerFactory.getLogger(GameSession.class);
  private final String sessionId;
  private final PinboardStateDTO pinboardState = new PinboardStateDTO();
  private final GameContextServer gameContext;
  private ClientSession player1;
  private ClientSession player2;
  private final ReentrantLock sessionLock = new ReentrantLock();
  // Per-player cap on shared-pool actions (deduce / task toggles) to blunt co-op griefing
  // (SECURITY_PLAN B/P2-5). Consulted on the server thread under sessionLock.
  private final SharedPoolThrottle sharedPoolThrottle = new SharedPoolThrottle();
  private GameSessionState state;
  private String gameCode;
  private final boolean isPublic;
  private final GameSessionManager sessionManager;
  private final GameServer server;
  private final CaseData caseFile;
  private LanGameBroadcaster broadcaster;
  private Thread broadcasterThread;

  /** Overloaded constructor for backward compatibility. Assumes a public game. */
  public GameSession(
      CaseData caseFile, ClientSession hostPlayer, GameSessionManager manager, GameServer server) {
    this(caseFile, hostPlayer, true, null, manager, server);
  }

  public GameSession(
      CaseData caseFile,
      ClientSession hostPlayer,
      boolean isPublic,
      String assignedGameCode,
      GameSessionManager manager,
      GameServer server) {
    this.sessionId = UUID.randomUUID().toString();
    this.caseFile = Objects.requireNonNull(caseFile, "CaseData object cannot be null");
    this.sessionManager = Objects.requireNonNull(manager, "GameSessionManager cannot be null");
    this.server = Objects.requireNonNull(server, "GameServer cannot be null");
    this.isPublic = isPublic;
    this.state = GameSessionState.LOADING;
    this.player1 = Objects.requireNonNull(hostPlayer, "Host player (player1) cannot be null");
    hostPlayer.setAssociatedGameSession(this);
    if (!isPublic) {
      this.gameCode =
          Objects.requireNonNull(
              assignedGameCode, "Private game session must be created with an assigned game code.");
    } else {
      this.gameCode = null;
    }

    this.gameContext = new GameContextServer(this, this.caseFile, hostPlayer.getPlayerId(), null);

    if (!loadCaseDataIntoContext()) {
      this.state = GameSessionState.ERROR;
      log("CRITICAL: Failed to load case data for new session " + sessionId);
      hostPlayer.send(
          new TextMessage(
              "Error: Failed to initialize the game data for this case. Session cannot start.",
              true));
    } else {
      this.state = GameSessionState.WAITING_FOR_PLAYERS;
      log(
          "Session created for case '"
              + this.caseFile.getTitle()
              + "'. Host: "
              + hostPlayer.getDisplayId()
              + ". Waiting for Player 2.");
      startBroadcasting();
    }
  }

  private void log(String message) {
    logger.info("[SESS:{}] {}", this.sessionId.substring(0, 8), message);
  }

  private boolean loadCaseDataIntoContext() {
    log("Loading case data into context...");
    gameContext.resetForNewCaseLoad();
    try {
      if (!extractors.BuildingExtractor.loadBuilding(this.caseFile, this.gameContext)) {
        log("Failed to load building data.");
        return false;
      }
      extractors.GameObjectExtractor.loadObjects(this.caseFile, this.gameContext);
      extractors.SuspectExtractor.loadSuspects(this.caseFile, this.gameContext);
    } catch (Exception e) {
      log("Unexpected error during case data loading: " + e.getMessage());
      server.logError("Stack trace for unexpected loading error in session " + sessionId, e);
      return false;
    }
    gameContext.initializePlayerStartingState();
    log("Case data loaded successfully.");
    return true;
  }

  public boolean addPlayer(ClientSession newPlayer) {
    sessionLock.lock();
    try {
      if (player2 != null) {
        newPlayer.send(new common.dto.JoinGameResponseDTO(false, "Session is already full.", null));
        return false;
      }
      if (this.state != GameSessionState.WAITING_FOR_PLAYERS) {
        newPlayer.send(
            new common.dto.JoinGameResponseDTO(
                false, "Session not currently accepting new players.", null));
        return false;
      }
      if (player1 != null && player1.getPlayerId().equals(newPlayer.getPlayerId())) {
        newPlayer.send(
            new common.dto.JoinGameResponseDTO(
                false, "You cannot join your own game as the second player.", null));
        return false;
      }
      player2 = newPlayer;
      newPlayer.setAssociatedGameSession(this);
      log("Player 2 (" + player2.getDisplayId() + ") joined session.");
      this.gameContext.setPlayerIds(player1.getPlayerId(), player2.getPlayerId());

      newPlayer.send(
          new common.dto.TextMessage(
              "You have joined the lobby for case '"
                  + this.caseFile.getTitle()
                  + "' hosted by "
                  + player1.getDisplayId(),
              false));

      stopBroadcasting();
      startGameSession();
      return true;
    } finally {
      sessionLock.unlock();
    }
  }

  private void startGameSession() {
    setSessionState(GameSessionState.IN_LOBBY_AWAITING_START);
    log(
        "Session is now "
            + this.state
            + ". Players: "
            + player1.getDisplayId()
            + ", "
            + player2.getDisplayId());
    LobbyUpdateDTO gameReadyMsg =
        new LobbyUpdateDTO(
            "Both players are in the lobby.",
            new ArrayList<>(getPlayerDisplayIds()),
            new ArrayList<>(getPlayerActualIds()),
            new ArrayList<>(getPlayerAvatarIds()),
            (player1 != null ? player1.getPlayerId() : null),
            true,
            caseFile.getInvitation(),
            caseFile.getTasks());
    broadcast(gameReadyMsg, null);
    // Sync tokens for the joining player (and host again, to be safe)
    gameContext.broadcastTokenUpdate(); // NEW
    broadcast(
        new TextMessage(
            "--- Case Invitation ---\n"
                + caseFile.getInvitation()
                + "\n\nHost ("
                + player1.getDisplayId()
                + ") should type 'start case' to begin.",
            false),
        null);
  }

  public void handlePlayerDisconnect(ClientSession disconnectedClient) {
    sessionLock.lock();
    try {
      String leavingPlayerId = disconnectedClient.getPlayerId();
      String leavingPlayerDisplayId = disconnectedClient.getDisplayId();
      boolean wasP1 = player1 != null && player1.getPlayerId().equals(leavingPlayerId);
      boolean wasP2 = player2 != null && player2.getPlayerId().equals(leavingPlayerId);

      if (!wasP1 && !wasP2) return;

      log("Player " + leavingPlayerDisplayId + " (ID: " + leavingPlayerId + ") has disconnected.");
      disconnectedClient.setAssociatedGameSession(null);

      if (wasP1) { // HOST DISCONNECTED
        log("Host has disconnected. Ending session " + sessionId);
        if (player2 != null) {
          player2.send(
              new TextMessage(
                  "The host ("
                      + leavingPlayerDisplayId
                      + ") has disconnected. The session has ended.",
                  false));
          player2.send(new ReturnToLobbyDTO("Returning to main menu as host has left."));
          player2.setAssociatedGameSession(null);
        }
        stopBroadcasting();
        sessionManager.endSession(this.sessionId, "Host disconnected.");
      } else if (wasP2) { // GUEST DISCONNECTED
        log("Guest has disconnected. Session " + sessionId + " continues for host.");
        this.player2 = null;
        if (gameContext != null) {
          gameContext.setPlayerIds(player1.getPlayerId(), null);
        }
        if (this.state == GameSessionState.ACTIVE && player1 != null) {
          player1.send(
              new TextMessage(
                  leavingPlayerDisplayId
                      + " has left the game. You may continue your investigation solo.",
                  false));
        } else if (this.state == GameSessionState.IN_LOBBY_AWAITING_START && player1 != null) {
          this.state = GameSessionState.WAITING_FOR_PLAYERS;
          player1.send(
              new TextMessage(
                  leavingPlayerDisplayId + " has left the lobby. Waiting for a new player...",
                  false));
          log("Session " + sessionId + " is now back to WAITING_FOR_PLAYERS.");
        }
      }
    } finally {
      sessionLock.unlock();
    }
  }

  public void playerRequestsExit(String playerId) {
    sessionLock.lock();
    try {
      ClientSession exitingPlayer = getClientSessionById(playerId);
      if (exitingPlayer == null) {
        log("PlayerRequestsExit called for a player ID not in this session: " + playerId);
        return;
      }

      log("Player " + exitingPlayer.getDisplayId() + " has requested to exit the game.");

      boolean wasHost = (player1 != null && player1.getPlayerId().equals(playerId));

      if (wasHost) {
        log("Host is exiting. The session will be terminated.");
        if (player2 != null) {
          player2.send(new TextMessage("The host has ended the game session.", false));
          player2.send(new ReturnToLobbyDTO("Returning to main menu."));
          player2.setAssociatedGameSession(null);
        }
        player1.send(new ReturnToLobbyDTO("You have left the game."));
        player1.setAssociatedGameSession(null);
        sessionManager.endSession(this.sessionId, "Host exited the game.");
      } else {
        log("Guest (" + exitingPlayer.getDisplayId() + ") is exiting. Host will remain.");
        exitingPlayer.send(new ReturnToLobbyDTO("You have left the game."));
        exitingPlayer.setAssociatedGameSession(null);
        this.player2 = null;
        if (gameContext != null) {
          gameContext.setPlayerIds(player1.getPlayerId(), null);
        }
        if (player1 != null) {
          player1.send(
              new TextMessage(exitingPlayer.getDisplayId() + " has left the lobby.", false));
          if (this.state == GameSessionState.ACTIVE) {
            player1.send(new TextMessage("You may continue your investigation solo.", false));
          } else if (this.state == GameSessionState.IN_LOBBY_AWAITING_START) {
            setSessionState(GameSessionState.WAITING_FOR_PLAYERS);
            log("Session is now back to WAITING_FOR_PLAYERS.");
            player1.send(new TextMessage("Waiting for a new player...", false));
            if (this.gameCode == null) {
              sessionManager.relistPublicLobby(this);
            }
          }
        }
      }
    } finally {
      sessionLock.unlock();
    }
  }

  public void processCommand(Command command, String playerId) {
    sessionLock.lock();
    try {
      command.setPlayerId(playerId);

      // Pinboard traffic is in-game shared state: only valid while the lobby/game is live
      // (security-pass issue 02 — previously accepted in ANY state).
      if (command instanceof UpdatePinboardCommand
          || command instanceof RequestPinboardStateCommand) {
        if (this.state != GameSessionState.ACTIVE
            && this.state != GameSessionState.IN_LOBBY_AWAITING_START) {
          ClientSession sender = getClientSessionById(playerId);
          if (sender != null) {
            sender.send(
                new TextMessage("Pinboard not available in session state: " + this.state, true));
          }
        } else if (command instanceof UpdatePinboardCommand) {
          handleUpdatePinboard((UpdatePinboardCommand) command, playerId);
        } else {
          handleRequestPinboardState((RequestPinboardStateCommand) command, playerId);
        }
        return;
      }

      boolean commandAllowed = false;
      if (this.state == GameSessionState.ACTIVE) {
        // In an active game, lobby-management commands make no sense and are refused; everything
        // else is gameplay and goes to the context (which does its own host gating).
        commandAllowed =
            !(command instanceof common.commands.HostGameCommand
                || command instanceof common.commands.JoinPublicGameCommand
                || command instanceof common.commands.JoinPrivateGameCommand);
      } else if (this.state == GameSessionState.WAITING_FOR_PLAYERS
          || this.state == GameSessionState.IN_LOBBY_AWAITING_START) {
        if (command instanceof common.commands.StartCaseCommand
            || command instanceof common.commands.RequestStartCaseCommand
            || command instanceof common.commands.ExitCommand
            || command instanceof common.commands.CancelLobbyCommand) {
          commandAllowed = true;
        }
      }

      if (commandAllowed) {
        // Per-player shared-pool throttle (SECURITY_PLAN B/P2-5): a peer spamming deduce / task
        // toggles can't drain shared insight tokens or inflate the shared deduction penalty.
        if (isSharedPoolCommand(command) && !sharedPoolThrottle.tryAcquire(playerId)) {
          ClientSession sender = getClientSessionById(playerId);
          if (sender != null) {
            sender.send(
                new TextMessage(
                    "You are performing shared actions too quickly — please slow down.", true));
          }
          return;
        }
        gameContext.executeCommand(command);
      } else {
        ClientSession sender = getClientSessionById(playerId);
        if (sender != null) {
          sender.send(
              new TextMessage("Command not allowed in current session state: " + this.state, true));
        }
      }
    } finally {
      sessionLock.unlock();
    }
  }

  /**
   * Commands that mutate the shared pool (insight tokens / deduction count / task checklist) and so
   * are subject to the per-player {@link SharedPoolThrottle} (SECURITY_PLAN B/P2-5).
   */
  private static boolean isSharedPoolCommand(Command command) {
    return command instanceof common.commands.DeduceCommand
        || command instanceof common.commands.UpdateTaskStateCommand;
  }

  public void processChatMessage(ChatMessage chatMessage) {
    sessionLock.lock();
    try {
      if (this.state == GameSessionState.ACTIVE
          || this.state == GameSessionState.IN_LOBBY_AWAITING_START) {
        broadcast(chatMessage, null);
      }
    } finally {
      sessionLock.unlock();
    }
  }

  public void broadcast(Serializable dto, String excludePlayerId) {
    if (player1 != null
        && (excludePlayerId == null || !player1.getPlayerId().equals(excludePlayerId))) {
      player1.send(dto);
    }
    if (player2 != null
        && (excludePlayerId == null || !player2.getPlayerId().equals(excludePlayerId))) {
      player2.send(dto);
    }
  }

  public void endSession(String reason) {
    this.state = GameSessionState.ENDED_ABANDONED;
  }

  public void notifyNameChangeToManagerIfHost(String updatedPlayerId, String newDisplayName) {
    sessionLock.lock();
    try {
      if (player1 != null
          && player1.getPlayerId().equals(updatedPlayerId)
          && sessionManager != null
          && gameCode == null) {
        sessionManager.updatePublicGameHostName(this.sessionId, newDisplayName);
      }
    } finally {
      sessionLock.unlock();
    }
  }

  public String getSessionId() {
    return sessionId;
  }

  public String getCaseTitle() {
    return caseFile.getTitle();
  }

  public String getGameCode() {
    return gameCode;
  }

  public GameSessionState getState() {
    return state;
  }

  public void setSessionState(GameSessionState state) {
    this.state = state;
    if (state == GameSessionState.ACTIVE) {
      // Broadcast full pinboard state to ensure clients are synced on start
      broadcast(new PinboardStateResponseCommand(pinboardState), null);
    }
  }

  public GameContextServer getGameContext() {
    return gameContext;
  }

  public GameServer getServer() {
    return server;
  }

  public ClientSession getPlayer1() {
    return player1;
  }

  public boolean isFull() {
    return player1 != null && player2 != null;
  }

  public List<String> getPlayerDisplayIds() {
    List<String> ids = new ArrayList<>();
    if (player1 != null) ids.add(player1.getDisplayId());
    if (player2 != null) ids.add(player2.getDisplayId());
    return ids;
  }

  public List<String> getPlayerActualIds() {
    List<String> ids = new ArrayList<>();
    if (player1 != null) ids.add(player1.getPlayerId());
    if (player2 != null) ids.add(player2.getPlayerId());
    return ids;
  }

  /** Chosen avatar preset ids, in the same player order as {@link #getPlayerDisplayIds()}. */
  public List<String> getPlayerAvatarIds() {
    List<String> ids = new ArrayList<>();
    if (player1 != null) ids.add(player1.getAvatarId());
    if (player2 != null) ids.add(player2.getAvatarId());
    return ids;
  }

  public ClientSession getClientSessionById(String playerId) {
    if (playerId == null) return null;
    if (player1 != null && player1.getPlayerId().equals(playerId)) return player1;
    if (player2 != null && player2.getPlayerId().equals(playerId)) return player2;
    return null;
  }

  public ClientSession getOtherPlayer(String currentPlayerId) {
    if (currentPlayerId == null) return null;
    if (player1 != null && player1.getPlayerId().equals(currentPlayerId)) return player2;
    if (player2 != null && player2.getPlayerId().equals(currentPlayerId)) return player1;
    return null;
  }

  private void handleUpdatePinboard(UpdatePinboardCommand command, String senderId) {
    PinboardUpdateDTO update = command.getUpdate();

    // 1. Validate BEFORE broadcasting — never relay unchecked client geometry/text to the peer
    // (security-pass issue 02).
    if (!PinboardUpdateValidator.isAcceptable(update, pinboardState)) {
      ClientSession sender = getClientSessionById(senderId);
      if (sender != null) {
        sender.send(new TextMessage("Pinboard update rejected (invalid or board full).", true));
      }
      return;
    }

    // 2. Broadcast to other players
    broadcast(command, senderId);

    // 3. Update local state (Simplified reducer)
    // Note: This is a basic reducer to keep state for new joiners.
    // Complex updates (like text area partial updates) might rely on "last write
    // wins" or full replacements in DTO.
    // PinboardUpdateDTO is granular.

    // Lazy initialization of lists
    if (pinboardState.getItems() == null) pinboardState.setItems(new ArrayList<>());
    if (pinboardState.getLinks() == null) pinboardState.setLinks(new ArrayList<>());
    if (pinboardState.getTemplateData() == null)
      pinboardState.setTemplateData(new java.util.HashMap<>());
    if (pinboardState.getTemplateDroppedItems() == null)
      pinboardState.setTemplateDroppedItems(new java.util.HashMap<>());

    switch (update.getType()) {
      case ADD_ITEM:
        if (update.getItem() != null) pinboardState.getItems().add(update.getItem());
        break;
      case REMOVE_ITEM:
        if (update.getTargetId() != null) {
          pinboardState.getItems().removeIf(i -> i.getId().equals(update.getTargetId()));
        }
        break;
      case MOVE_ITEM:
        if (update.getTargetId() != null) {
          for (common.dto.pinboard.PinboardItemDTO item : pinboardState.getItems()) {
            if (item.getId().equals(update.getTargetId())) {
              item.setX(update.getNewX());
              item.setY(update.getNewY());
              break;
            }
          }
        }
        break;
      case RESIZE_ITEM:
        if (update.getTargetId() != null && update.getItem() != null) {
          for (common.dto.pinboard.PinboardItemDTO item : pinboardState.getItems()) {
            if (item.getId().equals(update.getTargetId())) {
              item.setWidth(update.getItem().getWidth());
              item.setHeight(update.getItem().getHeight());
              break;
            }
          }
        }
        break;
      case UPDATE_CONTENT:
        if (update.getTargetId() != null && update.getValue() != null) {
          for (common.dto.pinboard.PinboardItemDTO item : pinboardState.getItems()) {
            if (item.getId().equals(update.getTargetId())) {
              item.setContent(update.getValue());
              break;
            }
          }
        }
        break;
      case ADD_LINK:
        if (update.getLink() != null) pinboardState.getLinks().add(update.getLink());
        break;
      case REMOVE_LINK:
        if (update.getLink() != null) {
          pinboardState
              .getLinks()
              .removeIf(
                  l ->
                      l.getStartItemId().equals(update.getLink().getStartItemId())
                          && l.getEndItemId().equals(update.getLink().getEndItemId()));
        }
        break;
      case UPDATE_TEMPLATE_NOTE:
        if (update.getKey() != null && update.getValue() != null) {
          pinboardState.getTemplateData().put(update.getKey(), update.getValue());
        }
        break;
      // UPDATE_TEMPLATE_DROP and others can be implemented similarly if needed for
      // full persistence
      case CLEAR_BOARD:
        pinboardState.getItems().clear();
        pinboardState.getLinks().clear();
        break;
    }
  }

  private void handleRequestPinboardState(RequestPinboardStateCommand command, String senderId) {
    ClientSession sender = getClientSessionById(senderId);
    if (sender != null) {
      sender.send(new PinboardStateResponseCommand(pinboardState));
    }
  }

  public void playerCancelsLobby(String playerId) {
    sessionLock.lock();
    try {
      if (this.state != GameSessionState.WAITING_FOR_PLAYERS
          && this.state != GameSessionState.IN_LOBBY_AWAITING_START) {
        ClientSession player = getClientSessionById(playerId);
        if (player != null) {
          player.send(
              new TextMessage(
                  "Error: The 'cancel' command can only be used in a pre-game lobby.", true));
        }
        return;
      }

      ClientSession cancellingPlayer = getClientSessionById(playerId);
      if (cancellingPlayer == null) {
        return;
      }

      boolean isHost = player1 != null && player1.equals(cancellingPlayer);

      if (isHost) {
        log("Host is cancelling the lobby. Session will be terminated.");

        if (player2 != null) {
          player2.send(new TextMessage("The host has cancelled the lobby.", false));
          player2.send(new ReturnToLobbyDTO("Returning to main menu."));
          player2.setAssociatedGameSession(null);
        }

        player1.send(new ReturnToLobbyDTO("You have left the lobby."));
        player1.setAssociatedGameSession(null);

        stopBroadcasting();
        sessionManager.endSession(this.sessionId, "Lobby cancelled by host.");

      } else {
        log(
            "Guest ("
                + cancellingPlayer.getDisplayId()
                + ") is leaving the lobby. Host will remain.");

        cancellingPlayer.send(new ReturnToLobbyDTO("You have left the lobby."));
        cancellingPlayer.setAssociatedGameSession(null);

        this.player2 = null;
        if (gameContext != null) {
          gameContext.setPlayerIds(player1.getPlayerId(), null);
        }

        if (player1 != null) {
          setSessionState(GameSessionState.WAITING_FOR_PLAYERS);
          log("Session is now back to WAITING_FOR_PLAYERS.");
          player1.send(
              new TextMessage(
                  cancellingPlayer.getDisplayId()
                      + " has left the lobby. Waiting for a new player...",
                  false));

          if (this.gameCode == null) {
            sessionManager.relistPublicLobby(this);
          }
        }
      }
    } finally {
      sessionLock.unlock();
    }
  }

  private void startBroadcasting() {
    if (broadcaster != null) {
      log("Broadcaster already running.");
      return;
    }

    java.util.function.Supplier<common.dto.LanDiscoveryPacket> packetSupplier =
        () ->
            new common.dto.LanDiscoveryPacket(
                this.sessionId,
                this.getCaseTitle(),
                this.player1.getDisplayId(),
                this.isPublic,
                // Digest, never the cleartext code — the packet reaches the whole LAN (issue 08).
                common.JoinCodes.digest(this.gameCode),
                common.NetworkConstants.DEFAULT_PORT);

    this.broadcaster = new LanGameBroadcaster(packetSupplier);
    this.broadcasterThread = new Thread(broadcaster, "LanBroadcaster-" + sessionId.substring(0, 8));
    this.broadcasterThread.setDaemon(true);
    this.broadcasterThread.start();
    log("LAN discovery broadcast started.");
  }

  private void stopBroadcasting() {
    if (broadcaster != null) {
      broadcaster.stop();
      if (broadcasterThread != null) {
        broadcasterThread.interrupt();
      }
      broadcaster = null;
      broadcasterThread = null;
      log("LAN discovery broadcast stopped.");
    }
  }
}

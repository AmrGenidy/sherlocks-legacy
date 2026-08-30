package server;

import JsonDTO.CaseFile;
import JsonDTO.LocalizedCaseFile;
import common.commands.*;
import common.commands.Command;
import common.commands.HostGameCommand;
import common.commands.JoinPrivateGameCommand;
import common.commands.JoinPublicGameCommand;
import common.commands.ListPublicGamesCommand;
import common.commands.RequestCaseListCommand;
import common.commands.UpdateDisplayNameCommand;
import common.dto.*;
import common.dto.AvailableCasesDTO;
import common.dto.HostGameRequestDTO;
import common.dto.HostGameResponseDTO;
import common.dto.JoinGameResponseDTO;
import common.dto.JoinPrivateGameRequestDTO;
import common.dto.JoinPublicGameRequestDTO;
import common.dto.PlayerNameChangedDTO;
import common.dto.PublicGameInfoDTO;
import common.dto.PublicGamesListDTO;
import common.dto.TextMessage;
import common.dto.UpdateDisplayNameRequestDTO;
import extractors.CaseLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class GameSessionManager {
  private final Map<String, CaseFile> availableCases;
  private final Map<String, GameSession> activeSessionsById;
  private final Map<String, GameSession> publicLobbiesById;
  private final Map<String, String> privateGameCodeToSessionId;
  private final Random randomForCodes = new Random();
  private final ReentrantLock managerLock = new ReentrantLock();
  private final GameServer server;
  private static final String CASES_DIRECTORY = "cases";

  public GameSessionManager(GameServer server) {
    this.server = server;
    this.availableCases = new ConcurrentHashMap<>();
    this.activeSessionsById = new ConcurrentHashMap<>();
    this.publicLobbiesById = new ConcurrentHashMap<>();
    this.privateGameCodeToSessionId = new ConcurrentHashMap<>();
    loadAllAvailableCases();
  }

  private void loadAllAvailableCases() {
    List<CaseFile> cases = CaseLoader.loadCases(CASES_DIRECTORY);
    availableCases.clear();
    for (CaseFile cf : cases) {
      // MODIFIED: Use the language-independent universal_title as the key
      availableCases.put(cf.getUniversalTitle().toLowerCase(), cf);
    }
    server.log("Loaded " + availableCases.size() + " cases from '" + CASES_DIRECTORY + "'.");
  }

  public void reloadCases() {
    server.log("Admin: Reloading all case files...");
    loadAllAvailableCases();
  }

  // NEW: This method returns the full multilingual CaseFile objects for the client.
  public List<CaseFile> getAvailableCases() {
    return new ArrayList<>(availableCases.values());
  }

  public List<PublicGameInfoDTO> getPublicLobbiesInfo() {
    return publicLobbiesById.values().stream()
        .filter(
            session ->
                session.getState() == GameSessionState.WAITING_FOR_PLAYERS
                    && session.getPlayer1() != null)
        .map(
            session ->
                new PublicGameInfoDTO(
                    session.getPlayer1().getDisplayId(),
                    session.getCaseTitle(),
                    session.getSessionId()))
        .collect(Collectors.toList());
  }

  private String generateUniquePrivateGameCode() {
    String chars = "ABCDEFGHIJKLMNPQRSTUVWXYZ123456789";
    int codeLength = 5;
    String potentialCode;
    do {
      StringBuilder codeBuilder = new StringBuilder(codeLength);
      for (int i = 0; i < codeLength; i++) {
        codeBuilder.append(chars.charAt(randomForCodes.nextInt(chars.length())));
      }
      potentialCode = codeBuilder.toString();
    } while (privateGameCodeToSessionId.containsKey(potentialCode));
    return potentialCode;
  }

  public void processLobbyCommand(ClientSession sender, Command command) {
    server.log(
        "Processing Lobby Command: "
            + command.getClass().getSimpleName()
            + " from "
            + sender.getDisplayId());
    if (command instanceof RequestCaseListCommand) {
      // Send browse-safe case summaries only — the full case (with the exam answer key) never
      // leaves the server (SECURITY_PLAN D). Hosting resolves the authoritative case by title.
      sender.send(new AvailableCasesDTO(LobbyCaseSanitizer.forBrowsing(getAvailableCases())));
    } else if (command instanceof HostGameCommand) {
      HostGameRequestDTO req = ((HostGameCommand) command).getPayload();
      // MODIFIED: Pass the language code to the createGame method.
      HostGameResponseDTO resp =
          createGame(sender, req.getCaseUniversalTitle(), req.isPublic(), req.getLanguageCode());
      sender.send(resp);
      if (resp.isSuccess() && !req.isPublic() && resp.getGameCode() != null) {
        sender.send(
            new TextMessage(
                "Private game code: " + resp.getGameCode() + ". Share it with your friend!",
                false));
      }
    } else if (command instanceof ListPublicGamesCommand) {
      sender.send(new PublicGamesListDTO(getPublicLobbiesInfo()));
    } else if (command instanceof JoinPublicGameCommand) {
      JoinPublicGameRequestDTO req = ((JoinPublicGameCommand) command).getPayload();
      sender.send(joinPublicGame(sender, req.getSessionId()));
    } else if (command instanceof JoinPrivateGameCommand) {
      JoinPrivateGameRequestDTO req = ((JoinPrivateGameCommand) command).getPayload();
      sender.send(joinPrivateGame(sender, req.getGameCode()));
    } else if (command instanceof UpdateDisplayNameCommand) {
      UpdateDisplayNameRequestDTO req = ((UpdateDisplayNameCommand) command).getPayload();
      String newName = req.getNewDisplayName();
      String oldName = sender.getDisplayId();
      if (newName != null
          && !newName.equals(oldName)
          && !newName.trim().isEmpty()
          && newName.length() < 25) {
        sender.setDisplayId(newName);
        server.log(
            "Lobby: Player " + sender.getPlayerId() + " (was " + oldName + ") is now " + newName);
        sender.send(new PlayerNameChangedDTO(sender.getPlayerId(), oldName, newName));
      } else {
        sender.send(new TextMessage("Invalid new display name.", true));
      }
    } else {
      sender.send(new TextMessage("Command not valid in lobby.", true));
    }
  }

  public JoinGameResponseDTO joinPublicGame(ClientSession joiningClient, String sessionId) {
    managerLock.lock();
    try {
      GameSession sessionToJoin = publicLobbiesById.get(sessionId);
      if (sessionToJoin == null
          || sessionToJoin.getState() != GameSessionState.WAITING_FOR_PLAYERS) {
        return new JoinGameResponseDTO(false, "Public game not available for joining.", null);
      }
      if (sessionToJoin.isFull()) {
        return new JoinGameResponseDTO(
            false,
            "This game is already full. Only two players can participate in this case.",
            null);
      }
      if (sessionToJoin.addPlayer(joiningClient)) {
        publicLobbiesById.remove(sessionId);
        return new JoinGameResponseDTO(
            true, "Successfully joined game: " + sessionToJoin.getCaseTitle(), sessionId);
      } else {
        return new JoinGameResponseDTO(false, "Failed to join session.", null);
      }
    } finally {
      managerLock.unlock();
    }
  }

  public JoinGameResponseDTO joinPrivateGame(ClientSession joiningClient, String gameCode) {
    managerLock.lock();
    try {
      String sessionId = privateGameCodeToSessionId.get(gameCode.toUpperCase());
      if (sessionId == null) {
        return new JoinGameResponseDTO(
            false, "Private game with code '" + gameCode + "' not found.", null);
      }
      GameSession sessionToJoin = activeSessionsById.get(sessionId);
      if (sessionToJoin == null
          || sessionToJoin.getState() != GameSessionState.WAITING_FOR_PLAYERS) {
        return new JoinGameResponseDTO(false, "Private game not available for joining.", null);
      }
      if (sessionToJoin.isFull()) {
        return new JoinGameResponseDTO(
            false,
            "This game is already full. Only two players can participate in this case.",
            null);
      }
      if (sessionToJoin.addPlayer(joiningClient)) {
        return new JoinGameResponseDTO(
            true, "Successfully joined private game: " + sessionToJoin.getCaseTitle(), sessionId);
      } else {
        return new JoinGameResponseDTO(false, "Failed to join private session.", null);
      }
    } finally {
      managerLock.unlock();
    }
  }

  public void handleClientDisconnect(ClientSession client) {
    GameSession session = client.getAssociatedGameSession();
    if (session != null) {
      server.log(
          "Notifying session "
              + session.getSessionId()
              + " about disconnect of "
              + client.getDisplayId());
      session.handlePlayerDisconnect(client);
    } else {
      server.log(
          "Client " + client.getDisplayId() + " disconnected but was not in any game session.");
    }
  }

  public void endSession(String sessionId, String reason) {
    managerLock.lock();
    try {
      GameSession session = activeSessionsById.remove(sessionId);
      if (session != null) {
        publicLobbiesById.remove(sessionId);
        if (session.getGameCode() != null) {
          privateGameCodeToSessionId.remove(session.getGameCode());
        }
        server.log(
            "Session " + sessionId + " fully ended and removed from manager. Reason: " + reason);
      }
    } finally {
      managerLock.unlock();
    }
  }

  public void updatePublicGameHostName(String sessionId, String newHostDisplayName) {
    // This is now implicitly handled by getPublicLobbiesInfo() reading the current display name.
    // We can keep this method for logging or future caching strategies.
    server.log(
        "Manager: Host name update for public session "
            + sessionId
            + " to '"
            + newHostDisplayName
            + "'. Listing will refresh on next query.");
  }

  // In server/GameSessionManager.java

  /**
   * Re-adds a session to the public lobbies list. Used when a guest leaves a full lobby before the
   * game starts, making it available for others to join again.
   *
   * @param session The public session to re-list.
   */
  public void relistPublicLobby(GameSession session) {
    if (session == null || session.getGameCode() != null) {
      return; // Not a public session.
    }
    managerLock.lock();
    try {
      server.log("Re-listing public lobby for session " + session.getSessionId());
      publicLobbiesById.put(session.getSessionId(), session);
    } finally {
      managerLock.unlock();
    }
  }

  public HostGameResponseDTO createGame(
      ClientSession hostClient, String caseUniversalTitle, boolean isPublic, String languageCode) {
    managerLock.lock();
    try {
      // Enforce single session per server instance
      if (!activeSessionsById.isEmpty()) {
        return new HostGameResponseDTO(
            false,
            "A multiplayer game is already being hosted on this server. End the current game before starting a new one.",
            null,
            null);
      }

      CaseFile multiLingualCase = availableCases.get(caseUniversalTitle.toLowerCase());
      if (multiLingualCase == null) {
        return new HostGameResponseDTO(
            false, "Case '" + caseUniversalTitle + "' not found on server.", null, null);
      }
      if (hostClient.getAssociatedGameSession() != null) {
        return new HostGameResponseDTO(
            false,
            "You are already in a game or lobby.",
            null,
            hostClient.getAssociatedGameSession().getSessionId());
      }

      // NEW: Create the single-language adapter.
      LocalizedCaseFile localizedCase = new LocalizedCaseFile(multiLingualCase, languageCode);

      String gameCodeForSession = isPublic ? null : generateUniquePrivateGameCode();

      // MODIFIED: Pass the LocalizedCaseFile to the GameSession constructor.
      GameSession newSession =
          new GameSession(localizedCase, hostClient, isPublic, gameCodeForSession, this, server);

      if (newSession.getState() == GameSessionState.ERROR) {
        return new HostGameResponseDTO(
            false,
            "Failed to initialize game session data for case: " + newSession.getCaseTitle(),
            null,
            null);
      }

      activeSessionsById.put(newSession.getSessionId(), newSession);
      if (isPublic) {
        publicLobbiesById.put(newSession.getSessionId(), newSession);
      } else {
        privateGameCodeToSessionId.put(gameCodeForSession, newSession.getSessionId());
      }

      server.log(
          "New game session created: "
              + newSession.getSessionId()
              + (isPublic ? " (Public)" : " (Private Code: " + newSession.getGameCode() + ")")
              + " for case: "
              + newSession.getCaseTitle()
              + " by host: "
              + hostClient.getDisplayId());
      return new HostGameResponseDTO(
          true,
          "Game hosted successfully. Waiting for opponent...",
          newSession.getGameCode(),
          newSession.getSessionId());
    } finally {
      managerLock.unlock();
    }
  }
}

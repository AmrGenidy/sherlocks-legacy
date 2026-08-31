package client;

import client.util.CommandFactoryClient;
import client.util.CommandParserClient;
import client.util.FinalExamState;
import common.NetworkConstants;
import common.SerializationUtils;
import common.commands.*;
import common.commands.pinboard.*;
import common.dto.*;
import common.dto.pinboard.PinboardStateDTO;
import common.dto.pinboard.PinboardUpdateDTO;
import java.io.IOException;
import java.io.Serializable;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GameClient implements Runnable {

  public enum LaunchMode {
    NORMAL, // Standard flow, shows the full multiplayer menu
    HOST_ONLY, // Skips the main multiplayer menu and goes directly to hosting options
    JOIN_ONLY // Skips all menus and immediately attempts to join a specific game
  }

  private static final Logger logger = LoggerFactory.getLogger(GameClient.class);
  private final String host;
  private final int port;
  private final LaunchMode launchMode;
  private final String joinGameId; // Can be a session ID for public games or a code for private
  private SocketChannel channel;
  private final AtomicBoolean running = new AtomicBoolean(true);
  private final AtomicBoolean connected = new AtomicBoolean(false);
  private final AtomicReference<ClientState> currentState =
      new AtomicReference<>(ClientState.DISCONNECTED);
  private Thread networkListenerThread;
  private Scanner consoleScanner;
  private GameClientStateListener listener;
  private java.util.function.Consumer<PinboardUpdateDTO> pinboardUpdateListener;
  private java.util.function.Consumer<PinboardStateDTO> pinboardStateListener;

  // Console-scaffolding labels (ui-localization). Legacy English by default (terminal client);
  // the GUI lobby injects UI-language texts after construction.
  private common.text.GameTexts texts = common.text.GameTexts.ENGLISH;

  // GUI input queue for JavaFX integration
  private final BlockingQueue<String> guiInputQueue = new LinkedBlockingQueue<>();

  // Session-specific data
  private String playerId;
  private String playerDisplayId;
  private String currentSessionId;
  // The private join code the server returned to THIS host, so the lobby/waiting screen can show it
  // (.scratch/private-join-code). Null for a public game or when not hosting.
  private String hostedGameCode;
  private String hostPlayerIdInSession;

  // Local player identity from the saved profile (player-profile feature): announced to the server
  // on registration so the player never retypes their name. initialDisplayName is blank when no
  // profile name is set (the random "Player####" then stands); ownAvatarId always has a value.
  private String initialDisplayName;
  private String ownAvatarId = common.PlayerAvatars.DEFAULT_ID;

  // Peer-visible identities by playerId, populated from lobby updates and the name/avatar change
  // broadcasts, so each client can render the OTHER player's real name + avatar in the lobby/room.
  private final Map<String, String> displayNameByPlayer =
      new java.util.concurrent.ConcurrentHashMap<>();
  private final Map<String, String> avatarByPlayer = new java.util.concurrent.ConcurrentHashMap<>();
  private volatile List<String> lobbyPlayerOrder = new ArrayList<>();

  private int caseIndexForLanguageSelection = -1;

  // Reconnect logic
  private int reconnectAttempts = 0;
  private static final int MAX_RECONNECT_ATTEMPTS = 2;
  private static final long RECONNECT_DELAY_MS = 5000;

  // Caches and temporary state
  private List<JsonDTO.CaseFile> availableCasesCache;
  private List<PublicGameInfoDTO> publicGamesCache;
  private List<String> currentTasks;
  private Map<Integer, Boolean> taskStates = new HashMap<>();
  private boolean finalExamUnlocked = false;
  private FinalExamState finalExamState;
  private client.exam.FinalExamController finalExamController;
  private List<JournalEntryDTO> journalEntries = new ArrayList<>();
  private List<ChatMessage> chatHistory = new ArrayList<>();
  private int currentExamQuestionNumberBeingAnswered = -1;
  private ClientState preWaitingState;
  private boolean intentToHostPublic;

  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
  private final ReentrantLock consoleLock = new ReentrantLock();
  private final java.util.function.Consumer<String> consoleWriter;
  private final boolean isGuiMode;
  private Thread mainThread;
  private String currentCaseTitle;

  public GameClient(
      String host,
      int port,
      java.util.function.Consumer<String> guiConsoleWriter,
      LaunchMode launchMode) {
    this(host, port, guiConsoleWriter, launchMode, null);
  }

  /**
   * @param guiConsoleWriter receives each console line when running under the GUI (the caller
   *     marshals to the FX thread); {@code null} for terminal mode, which writes to {@code
   *     System.out}.
   */
  public GameClient(
      String host,
      int port,
      java.util.function.Consumer<String> guiConsoleWriter,
      LaunchMode launchMode,
      String joinGameId) {
    this.host = host;
    this.port = port;
    this.launchMode = launchMode;
    this.joinGameId = joinGameId;
    this.playerDisplayId = "Player" + (int) (Math.random() * 9000 + 1000);
    if (guiConsoleWriter != null) {
      this.consoleWriter = guiConsoleWriter;
      this.isGuiMode = true;
    } else {
      this.consoleWriter = System.out::println;
      this.isGuiMode = false;
    }
  }

  public void setListener(GameClientStateListener listener) {
    this.listener = listener;
  }

  // Localizes a received DialogueEventDTO before it is rendered (bubble + transcript). The GUI
  // injects ui.i18n.WatsonDialogue::localize to resolve a generic Watson response's UI-language
  // key;
  // console mode leaves it identity. Keeps this client layer free of any ui dependency.
  // (.scratch/gui-localized-watson-hints phase 2)
  private java.util.function.UnaryOperator<common.dto.DialogueEventDTO> dialogueLocalizer =
      java.util.function.UnaryOperator.identity();

  public void setDialogueLocalizer(
      java.util.function.UnaryOperator<common.dto.DialogueEventDTO> dialogueLocalizer) {
    this.dialogueLocalizer =
        dialogueLocalizer == null ? java.util.function.UnaryOperator.identity() : dialogueLocalizer;
  }

  /**
   * Seeds this client's identity from the local player profile (player-profile feature), to be
   * announced to the server on registration. A blank display name leaves the server-assigned random
   * name in place; a blank/unknown avatar id falls back to the default preset.
   */
  public void setInitialIdentity(String displayName, String avatarId) {
    this.initialDisplayName = displayName;
    this.ownAvatarId =
        common.PlayerAvatars.isValid(avatarId) ? avatarId : common.PlayerAvatars.DEFAULT_ID;
  }

  /**
   * This client's current display name (profile- or server-assigned, or set via {@code /setname}).
   */
  public String getOwnDisplayName() {
    return playerDisplayId;
  }

  /** This client's chosen avatar preset id. */
  public String getOwnAvatarId() {
    return ownAvatarId;
  }

  /** The other player's announced display name, or {@code null} if no peer is present/known yet. */
  public String getPeerDisplayName() {
    String peer = peerPlayerId();
    return peer == null ? null : displayNameByPlayer.get(peer);
  }

  /** The other player's chosen avatar id, or {@code null} if no peer is present/known yet. */
  public String getPeerAvatarId() {
    String peer = peerPlayerId();
    return peer == null ? null : avatarByPlayer.get(peer);
  }

  /** The first known playerId that is not this client's own (max two players per session). */
  private String peerPlayerId() {
    java.util.Set<String> ids = new java.util.LinkedHashSet<>(lobbyPlayerOrder);
    ids.addAll(displayNameByPlayer.keySet());
    ids.addAll(avatarByPlayer.keySet());
    for (String id : ids) {
      if (id != null && !id.equals(playerId)) {
        return id;
      }
    }
    return null;
  }

  /** Replaces the console-scaffolding labels (never null; see {@link #texts}). */
  public void setGameTexts(common.text.GameTexts texts) {
    this.texts = texts != null ? texts : common.text.GameTexts.ENGLISH;
  }

  public void setPinboardUpdateListener(java.util.function.Consumer<PinboardUpdateDTO> listener) {
    this.pinboardUpdateListener = listener;
  }

  public void setPinboardStateListener(java.util.function.Consumer<PinboardStateDTO> listener) {
    this.pinboardStateListener = listener;
  }

  public void sendPinboardUpdate(PinboardUpdateDTO update) {
    sendToServer(new UpdatePinboardCommand(update));
  }

  public void sendPinboardStateRequest() {
    sendToServer(new RequestPinboardStateCommand());
  }

  public void enqueueUserInput(String input) {
    if (input != null && !input.trim().isEmpty()) {
      try {
        guiInputQueue.offer(input.trim());
        log("GUI input enqueued: " + input);
      } catch (Exception e) {
        logError("Failed to enqueue GUI input: " + input, e);
      }
    }
  }

  @Override
  public void run() {
    this.mainThread = Thread.currentThread();
    this.consoleScanner = new Scanner(System.in);
    printToConsole("Welcome, " + playerDisplayId + "!");

    if (currentState.get() == ClientState.DISCONNECTED) {
      attemptConnect();
    }

    while (running.get()) {
      ClientState cs = currentState.get(); // Get current state once per loop

      if (!connected.get()) {
        if (cs == ClientState.RECONNECTING) {
          if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            printToConsole(
                "Attempting to reconnect ("
                    + (reconnectAttempts + 1)
                    + "/"
                    + MAX_RECONNECT_ATTEMPTS
                    + ")...");
            try {
              Thread.sleep(RECONNECT_DELAY_MS);
            } catch (InterruptedException e) {
              if (!running.get()) break;
              Thread.currentThread().interrupt();
            }
            if (running.get() && !connected.get()) {
              reconnectAttempts++;
              attemptConnect();
            }
            continue;
          } else {
            log("Max automatic reconnect attempts reached.");
            printToConsole("Failed to reconnect automatically. Server may be offline.");
            currentState.set(ClientState.DISCONNECTED);
            // reconnectAttempts reset when user types 'connect'
          }
        } else if (cs != ClientState.CONNECTING && cs != ClientState.EXITING) {
          currentState.set(ClientState.DISCONNECTED);
        }
      }

      // Re-fetch state as it might have changed due to reconnect logic
      cs = currentState.get();
      displayMenuOrPromptForCurrentState();

      // Use the enum's property directly
      if (cs.isInteractive()) {
        String input = null;
        try {
          if (isGuiMode) {
            // GUI mode: Block and wait for input from the GUI thread only.
            input = guiInputQueue.take();
          } else {
            // Console mode: Block and wait for input from System.in.
            if (consoleScanner != null && consoleScanner.hasNextLine()) {
              input = consoleScanner.nextLine();
            }
          }
        } catch (InterruptedException e) {
          if (!running.get()) break;
          Thread.currentThread().interrupt();
        }

        if (input != null && !input.isEmpty()) {
          processUserInputBasedOnState(input.trim());
        }
      } else {
        // For non-interactive (waiting) states, we still need to process potential
        // "cancel" commands from the GUI. We use poll() to avoid getting stuck.
        try {
          String guiInput = guiInputQueue.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS);
          if (guiInput != null && !guiInput.isEmpty()) {
            processUserInputBasedOnState(guiInput.trim());
          }
        } catch (InterruptedException e) {
          if (!running.get()) break;
          Thread.currentThread().interrupt();
        }
      }
    }
    shutdownClientResources();
    log("Client main loop finished.");
  }

  private void displayMenuOrPromptForCurrentState() {
    consoleLock.lock();
    try {
      ClientState cs = currentState.get();
      if (listener != null) {
        switch (cs) {
          case CONNECTED_IDLE:
            listener.onMainMenu();
            break;
          case SELECTING_HOST_TYPE:
            listener.onHostGameOptions();
            break;
          case SELECTING_HOST_CASE:
            listener.onCaseSelection(availableCasesCache);
            break;
          case SELECTING_HOST_LANGUAGE:
            listener.onLanguageSelection(availableCasesCache.get(caseIndexForLanguageSelection));
            break;
          case HOSTING_LOBBY_WAITING:
            listener.onHostingLobby(hostedGameCode);
            break;
          case SELECTING_JOIN_TYPE:
            listener.onJoinGameOptions();
            break;
          case VIEWING_PUBLIC_GAMES:
            listener.onPublicGamesList(publicGamesCache);
            break;
          case ENTERING_PRIVATE_CODE:
            listener.onPrivateGameEntry();
            break;
          case IN_LOBBY_AWAITING_START:
            listener.onLobby();
            break;
          case CONNECTING:
            listener.onConnecting();
            break;
          case DISCONNECTED:
            listener.onDisconnected();
            break;
          default:
            // Silence state debug spam
            // printToConsole("State: " + cs.name());
            break;
        }
      } else {
        // Fallback to console if no listener
        // printToConsole("State: " + cs.name());
      }
    } finally {
      consoleLock.unlock();
    }
  }

  private void processUserInputBasedOnState(String input) {
    ClientState cs = currentState.get();
    log("PROCESS_USER_INPUT: Input='" + input + "', State=" + cs);

    // --- Global commands handled first and return ---
    if (input.equalsIgnoreCase("quit")) {
      handleQuitCommand(cs);
      return;
    }

    if (input.toLowerCase().startsWith("/setname ")) {
      handleSetNameCommand(input);
      return;
    }

    if (isChatCommand(input) && currentSessionId != null) {
      processChatCommandOnly(input);
      return; // Bypass the state-specific switch statement
    }

    if (input.equalsIgnoreCase("cancel") && cs.isPrimarilyWaiting()) {
      handleCancelWaitingState(cs);
      return;
    }

    if ((cs == ClientState.DISCONNECTED || cs == ClientState.RECONNECTING)
        && input.equalsIgnoreCase("connect")) {
      this.reconnectAttempts = 0;
      attemptConnect();
      return;
    }

    if (cs == ClientState.RECONNECTING && !input.equalsIgnoreCase("quit")) {
      return;
    } // Ignore other input during auto-reconnect

    // --- State-Specific Input Handling ---
    switch (cs) {
      case CONNECTED_IDLE:
        handleMainMenuInput(input);
        break;

      case SELECTING_HOST_TYPE:
        handleHostTypeSelection(input);
        break;

      case SELECTING_HOST_CASE:
        handleHostCaseSelection(input);
        break;

      case SELECTING_HOST_LANGUAGE:
        handleHostLanguageSelection(input);
        break;

      case SHOWING_INVITATION:
      case HOSTING_LOBBY_WAITING:
      case IN_LOBBY_AWAITING_START:
        if (input.equalsIgnoreCase("cancel")) {
          printToConsole("Returning to main menu...");
          currentState.set(ClientState.SENDING_HOST_REQUEST);
          sendToServer(new CancelLobbyCommand());
        } else if (isChatCommand(input)) {
          processChatCommandOnly(input);
        } else {
          handleInGameOrLobbyReadyInput(input, cs);
        }
        break;

      case SELECTING_JOIN_TYPE:
        handleJoinTypeSelection(input);
        break;

      case VIEWING_PUBLIC_GAMES:
        handlePublicGameSelection(input);
        break;

      case ENTERING_PRIVATE_CODE:
        handlePrivateCodeEntry(input);
        break;

      case IN_GAME:
        handleInGameOrLobbyReadyInput(input, cs);
        break;

      case ANSWERING_FINAL_EXAM_Q:
        handleExamAnswerInput(input);
        break;

      case VIEWING_EXAM_RESULT:
        if (isChatCommand(input)) {
          processChatCommandOnly(input);
        } else {
          handleInGameOrLobbyReadyInput(input, ClientState.IN_GAME);
        }
        // Do NOT auto-transition to IN_GAME. Stay in VIEWING_EXAM_RESULT until server
        // updates state.
        break;

      case DISCONNECTED:
      case RECONNECTING:
        if (!input.equalsIgnoreCase("connect") && !input.equalsIgnoreCase("quit")) {
          printToConsole("You are not connected. Type 'connect' or 'quit'.");
        }
        break;

      case REQUESTING_CASE_LIST_FOR_HOST:
      case REQUESTING_PUBLIC_GAMES:
      case SENDING_HOST_REQUEST:
      case SENDING_JOIN_PUBLIC_REQUEST:
      case SENDING_JOIN_PRIVATE_REQUEST:
      case ATTEMPTING_FINAL_EXAM:
      case SUBMITTING_EXAM_ANSWER:
        if (isChatCommand(input) && currentSessionId != null) {
          processChatCommandOnly(input);
        } else if (!input.equalsIgnoreCase("cancel")
            && !input.equalsIgnoreCase("quit")
            && !input.isEmpty()) {
          // The prompt for these states already says "Waiting... (type 'cancel'...)"
        }
        break;

      default:
        if (cs.isInteractive() && !isChatCommand(input) && !input.isEmpty()) {
          printToConsole(
              "Command '" + input + "' not applicable in current state: " + cs + ". Type 'help'.");
        }
        break;
    }
  }

  private void handleInGameOrLobbyReadyInput(String input, ClientState currentStateForCommand) {
    if (isChatCommand(input)) {
      processChatCommandOnly(input);
      return;
    }

    CommandParserClient.ParsedCommandData parsedData = CommandParserClient.parse(input);

    if (parsedData == null || parsedData.commandName == null || parsedData.commandName.isEmpty()) {
      printToConsole("Invalid command format (parser returned null or empty command name).");
      return;
    }

    Command commandToExecute;
    try {
      commandToExecute =
          CommandFactoryClient.createCommand(
              parsedData, isThisClientTheHost(), currentStateForCommand);
    } catch (IllegalArgumentException e) {
      // Command constructors enforce the wire limits (WireLimits); reject locally with the reason
      // instead of crashing the input handler.
      printToConsole("Invalid command input: " + e.getMessage());
      return;
    }

    if (commandToExecute != null) {
      updateClientStateBeforeSending(commandToExecute);
      sendToServer(commandToExecute);
    } else {
      // Simplified error handling. Assume factory prints specific errors for known
      // commands.
      printToConsole(
          "Unknown command: '" + parsedData.commandName + "' or not applicable now. Type 'help'.");
    }
  }

  private boolean isChatCommand(String input) {
    String lowerInput = input.toLowerCase();
    return lowerInput.startsWith("/chat ") || lowerInput.startsWith("/c ");
  }

  private void handleQuitCommand(ClientState cs) {
    if (currentSessionId != null
        && (cs == ClientState.IN_GAME
            || cs == ClientState.IN_LOBBY_AWAITING_START
            || cs == ClientState.HOSTING_LOBBY_WAITING
            || cs.name().contains("EXAM"))) {
      sendToServer(new ExitCommand()); // Graceful exit from session
    } else {
      stopClient(); // Exit client directly
    }
  }

  private boolean isThisClientTheHost() {
    boolean result =
        Objects.equals(this.playerId, this.hostPlayerIdInSession) && this.playerId != null;
    log(
        "isThisClientTheHost() check: myId="
            + this.playerId
            + ", sessionHostId="
            + this.hostPlayerIdInSession
            + ", result="
            + result);
    return result;
  }

  private void handleSetNameCommand(String input) {
    CommandParserClient.ParsedCommandData parsedData =
        CommandParserClient.parse(input); // input is like "/setname
    // NewName"
    if (parsedData == null
        || !parsedData.commandName.equals("/setname")
        || parsedData.getFirstArgument() == null
        || parsedData.getFirstArgument().isEmpty()) {
      printToConsole("Usage: /setname <new_display_name>");
      return;
    }

    String newName = parsedData.getFirstArgument();
    if (newName.length() < 25 && newName.length() > 0) { // Basic validation
      String oldLocalDisplayName = this.playerDisplayId;
      this.playerDisplayId = newName;
      printToConsole("Display name changed locally to: " + this.playerDisplayId);

      if (connected.get()) {
        Command setNameCmd =
            CommandFactoryClient.createCommand(
                parsedData, isThisClientTheHost(), currentState.get());
        if (setNameCmd != null) {
          sendToServer(setNameCmd);
        } else {
          logError("Failed to create UpdateDisplayNameCommand for /setname " + newName, null);
          this.playerDisplayId = oldLocalDisplayName; // Revert optimistic update
          printToConsole("Error sending name change to server.");
        }
      } else {
        printToConsole("Not connected to server. Name change is local only for now.");
      }
    } else {
      printToConsole("Invalid display name. Must be 1-24 characters.");
    }
  }

  private void handleCancelWaitingState(ClientState cs) {
    printToConsole("Cancelling current operation...");
    if (this.preWaitingState != null) {
      currentState.set(this.preWaitingState);
      this.preWaitingState = null; // Clear it after use
    } else {
      logError(
          "Cancel called from waiting state "
              + cs
              + " but preWaitingState was null. Returning to CONNECTED_IDLE.",
          null);
      currentState.set(ClientState.CONNECTED_IDLE);
    }
  }

  private void handleMainMenuInput(String input) {
    switch (input) {
      case "1":
        preWaitingState = ClientState.CONNECTED_IDLE;
        currentState.set(ClientState.SELECTING_HOST_TYPE);
        break;
      case "2":
        preWaitingState = ClientState.CONNECTED_IDLE;
        currentState.set(ClientState.SELECTING_JOIN_TYPE);
        break;
      case "4":
        stopClient();
        break;
      default:
        printToConsole("Invalid choice for Main Menu.");
        break;
    }
  }

  private void handleHostTypeSelection(String input) {
    switch (input) {
      case "1": // Host Public
        this.intentToHostPublic = true;
        this.preWaitingState = ClientState.SELECTING_HOST_TYPE;
        currentState.set(ClientState.REQUESTING_CASE_LIST_FOR_HOST);
        sendToServer(new RequestCaseListCommand());
        break;
      case "2": // Host Private
        this.intentToHostPublic = false;
        this.preWaitingState = ClientState.SELECTING_HOST_TYPE;
        currentState.set(ClientState.REQUESTING_CASE_LIST_FOR_HOST);
        sendToServer(new RequestCaseListCommand());
        break;
      case "3":
        if (launchMode == LaunchMode.HOST_ONLY) {
          stopClient();
        } else {
          currentState.set(ClientState.CONNECTED_IDLE);
        }
        break;
      default:
        printToConsole("Invalid choice for Host Type.");
        break;
    }
  }

  // REPLACE this method
  private void handleHostCaseSelection(String input) {
    if ("0".equals(input)) {
      currentState.set(ClientState.SELECTING_HOST_TYPE);
      this.availableCasesCache = null;
      this.caseIndexForLanguageSelection = -1;
      return;
    }

    try {
      int caseNum = Integer.parseInt(input);
      if (availableCasesCache != null && caseNum > 0 && caseNum <= availableCasesCache.size()) {
        this.caseIndexForLanguageSelection = caseNum - 1; // Store the index
        JsonDTO.CaseFile selectedCase = availableCasesCache.get(this.caseIndexForLanguageSelection);

        if (selectedCase.getLocalizations().size() == 1) {
          String langCode = selectedCase.getLocalizations().keySet().iterator().next();
          sendHostRequest(selectedCase.getUniversalTitle(), langCode);
        } else {
          printToConsole(
              "--- Select a Language for '" + selectedCase.getUniversalTitle() + "' ---");
          List<String> langNames =
              selectedCase.getLocalizations().values().stream()
                  .map(JsonDTO.CaseFile.LocalizedData::getLanguageName)
                  .collect(Collectors.toList());
          for (int i = 0; i < langNames.size(); i++) {
            printToConsole((i + 1) + ". " + langNames.get(i));
          }
          currentState.set(ClientState.SELECTING_HOST_LANGUAGE);
        }
      } else {
        printToConsole("Invalid case number.");
      }
    } catch (NumberFormatException e) {
      printToConsole("Invalid input. Please enter a number.");
    }
  }

  private void handleJoinTypeSelection(String input) {
    switch (input) {
      case "1": // Join Public
        preWaitingState = ClientState.SELECTING_JOIN_TYPE;
        currentState.set(ClientState.REQUESTING_PUBLIC_GAMES);
        sendToServer(new ListPublicGamesCommand());
        break;
      case "2": // Join Private
        preWaitingState = ClientState.SELECTING_JOIN_TYPE;
        currentState.set(ClientState.ENTERING_PRIVATE_CODE);
        break;
      case "3":
        if (launchMode == LaunchMode.JOIN_ONLY) {
          stopClient();
        } else {
          currentState.set(ClientState.CONNECTED_IDLE);
        }
        break;
      default:
        printToConsole("Invalid choice for Join Type.");
        break;
    }
  }

  private void handlePublicGameSelection(String input) {
    if (input.equals("0")) { // Refresh
      currentState.set(ClientState.REQUESTING_PUBLIC_GAMES);
      sendToServer(new ListPublicGamesCommand());
      return;
    } else if (input.equals("00")) { // Join by Code
      currentState.set(ClientState.ENTERING_PRIVATE_CODE);
      this.publicGamesCache = null;
      return;
    } else if (input.equals("000")) { // Back to Main Menu
      currentState.set(ClientState.SELECTING_JOIN_TYPE);
      this.publicGamesCache = null;
      return;
    }

    String sessionIdToJoin = null;
    try {
      int gameNum = Integer.parseInt(input);
      if (publicGamesCache != null && gameNum > 0 && gameNum <= publicGamesCache.size()) {
        sessionIdToJoin = publicGamesCache.get(gameNum - 1).getSessionId();
      }
    } catch (NumberFormatException e) {
      sessionIdToJoin = input; // Assume it's a direct session ID string
    }

    if (sessionIdToJoin != null && !sessionIdToJoin.isEmpty()) {
      printToConsole("Attempting to join public game: " + sessionIdToJoin);
      preWaitingState = ClientState.VIEWING_PUBLIC_GAMES;
      currentState.set(ClientState.SENDING_JOIN_PUBLIC_REQUEST);
      sendToServer(new JoinPublicGameCommand(new JoinPublicGameRequestDTO(sessionIdToJoin)));
      this.publicGamesCache = null;
    } else {
      printToConsole("Invalid selection or session ID.");
    }
  }

  private void handlePrivateCodeEntry(String inputCode) {
    if (inputCode.equalsIgnoreCase("cancel")) {
      currentState.set(ClientState.SELECTING_JOIN_TYPE);
      return;
    }
    if (inputCode.length() < 3 || inputCode.length() > 10) {
      printToConsole("Game code seems invalid. Try again or type 'cancel'.");
      return;
    }
    printToConsole("Attempting to join private game with code: " + inputCode);
    preWaitingState = ClientState.ENTERING_PRIVATE_CODE;
    currentState.set(ClientState.SENDING_JOIN_PRIVATE_REQUEST);
    sendToServer(
        new JoinPrivateGameCommand(new JoinPrivateGameRequestDTO(inputCode.toUpperCase())));
  }

  private void handleHostingLobbyInput(String input) {
    if (input.equalsIgnoreCase("exit lobby")) {
      printToConsole("Cancelling hosted game lobby...");
      sendToServer(new ExitCommand()); // Server will end the session
    } else {
      processChatCommandOnly(input); // Allows chat
    }
  }

  private void handleExamAnswerInput(String inputAnswer) {
    if (inputAnswer.isEmpty()) {
      printToConsole("Answer cannot be empty. Please provide an answer.");
      return;
    }

    // Check if it looks like an answer (e.g., "1,2" or "1 2")
    // Only intercept if we are actually expecting an answer.
    if (currentExamQuestionNumberBeingAnswered > 0 && inputAnswer.matches("(\\d+[,\\s]+)+\\d+")) {
      String[] parts = inputAnswer.split("[,\\s]+");
      if (parts.length >= 2) {
        if (finalExamController != null) {
          finalExamController.handleTerminalAnswer(parts);
        }
      } else {
        printToConsole("Please provide answers for all slots (e.g., '1,2').");
      }
      return;
    }

    // If not a specific exam command or answer, pass through to standard game input
    // handler
    // This ensures chat, movement, journal access, etc. all work transparently.
    handleInGameOrLobbyReadyInput(inputAnswer, ClientState.IN_GAME);
  }

  private void processChatCommandOnly(String input) {
    String chatText = input.substring(input.indexOf(" ") + 1).trim();
    if (!chatText.isEmpty()) {
      ChatMessage chatMsg =
          new ChatMessage(this.playerDisplayId, chatText, System.currentTimeMillis());
      sendToServer(chatMsg);
    } else {
      printToConsole("Usage: /chat <message> OR /c <message>");
    }
  }

  private void handleAvailableCases(AvailableCasesDTO ac) {
    if (currentState.get() != ClientState.REQUESTING_CASE_LIST_FOR_HOST) {
      log(
          "Received case list from server unexpectedly (current state: "
              + currentState.get()
              + ").");
      // Don't change state here, it can cause loops. Let the user's "cancel" command
      // handle it.
      return;
    }

    this.availableCasesCache = ac.getCases();
    if (availableCasesCache == null || availableCasesCache.isEmpty()) {
      printToConsole("No cases available on the server to host.");
      currentState.set(ClientState.SELECTING_HOST_TYPE);
    } else {
      printToConsole("--- Select a Case to Host ---");
      for (int i = 0; i < availableCasesCache.size(); i++) {
        JsonDTO.CaseFile currentCase = availableCasesCache.get(i);
        String languages =
            currentCase.getLocalizations().values().stream()
                .map(JsonDTO.CaseFile.LocalizedData::getLanguageName)
                .collect(Collectors.joining(", "));
        printToConsole((i + 1) + ". " + currentCase.getUniversalTitle() + " [" + languages + "]");
      }
      currentState.set(ClientState.SELECTING_HOST_CASE);
    }
  }

  private void handleTextMessage(TextMessage tm, ClientState stateWhenMessageReceived) {
    // Localize a keyed message to THIS client's UI language (English text is the fallback); the
    // exam-prompt string checks below still use the raw getText() so protocol logic is unaffected.
    Object[] a = tm.getArgs() == null ? new Object[0] : tm.getArgs().toArray();
    String localized = ui.i18n.L10n.tOr(tm.getMessageKey(), tm.getText(), a);
    String messageToPrint = (tm.isError() ? "[SERVER ERROR] " : "[SERVER] ") + localized;
    printToConsole(messageToPrint);

    if (!tm.isError() && tm.getText().startsWith("Host, please submit your answer for Q")) {
      if (isThisClientTheHost()) {
        currentState.set(ClientState.ANSWERING_FINAL_EXAM_Q);
      } else {
        log("Warning: Guest client received host-specific exam prompt: " + tm.getText());
      }
    } else if (tm.isError()) {
      switch (stateWhenMessageReceived) {
        case ATTEMPTING_FINAL_EXAM:
        case SUBMITTING_EXAM_ANSWER:
          printToConsole("Exam process interrupted by server error. Returning to game.");
          currentState.set(ClientState.IN_GAME);
          preWaitingState = null;
          break;
        case SENDING_HOST_REQUEST:
          printToConsole("Host request failed. Returning to host options.");
          currentState.set(ClientState.SELECTING_HOST_TYPE);
          preWaitingState = null;
          break;
        case SENDING_JOIN_PUBLIC_REQUEST:
        case SENDING_JOIN_PRIVATE_REQUEST:
          printToConsole("Join request failed. Returning to join options.");
          currentState.set(ClientState.SELECTING_JOIN_TYPE);
          preWaitingState = null;
          break;
        default:
          break;
      }
    }
  }

  private void handleChatMessage(ChatMessage cm) {
    printToConsole(cm.toString());
    chatHistory.add(cm);
    if (listener != null) {
      listener.onChatMessageReceived(cm);
    }
  }

  private void handleRoomDescription(RoomDescriptionDTO rd) {
    // This method now decides whether it's the start of the game or just a move.
    if (currentState.get() != ClientState.IN_GAME) {
      currentState.set(ClientState.IN_GAME);
      if (listener != null) {
        listener.onEnterGame(rd);
      }
    } else {
      if (listener != null) {
        listener.onUpdateRoom(rd);
      }
    }
    // We still print to console for non-GUI users or logging. Scaffolding labels come from the
    // injected texts (UI language in the GUI, legacy English in the terminal client).
    StringBuilder sb = new StringBuilder();
    sb.append("\n").append(texts.locationHeader(rd.getName())).append("\n");
    sb.append(rd.getDescription()).append("\n");
    sb.append(texts.objectsLabel())
        .append(" ")
        .append(
            joinDisplayNames(
                rd.getObjectNames(),
                rd.getObjectDisplayNames(),
                texts.watsonSpeaker(),
                texts.noneLabel()))
        .append("\n");
    sb.append(texts.occupantsLabel())
        .append(" ")
        .append(
            joinDisplayNames(
                rd.getOccupantNames(),
                rd.getOccupantDisplayNames(),
                texts.watsonSpeaker(),
                texts.noneLabel()))
        .append("\n");
    sb.append(texts.exitsLabel()).append(" ");
    if (rd.getExits().isEmpty()) {
      sb.append(texts.noneLabel());
    } else {
      rd.getExits()
          .forEach((dir, roomName) -> sb.append(texts.exitEntry(dir, roomName)).append(", "));
      if (!rd.getExits().isEmpty()) sb.setLength(sb.length() - 2);
    }
    printToConsole(sb.toString());
  }

  /**
   * Joins universal names to their per-language Display Names (from the DTO side map), falling back
   * to the Universal name; "Dr. Watson" falls back to the localized speaker label when the case did
   * not rename the assistant.
   */
  private static String joinDisplayNames(
      java.util.List<String> names,
      java.util.Map<String, String> displayMap,
      String watsonSpeaker,
      String noneLabel) {
    if (names == null || names.isEmpty()) {
      return noneLabel;
    }
    java.util.List<String> out = new java.util.ArrayList<>();
    for (String n : names) {
      String display = displayMap == null ? null : displayMap.get(n);
      if (display == null || display.isBlank()) {
        display = "Dr. Watson".equals(n) ? watsonSpeaker : n;
      }
      out.add(display);
    }
    return String.join(", ", out);
  }

  private void handleHostGameResponse(HostGameResponseDTO hgr) {
    if (hgr.isSuccess()) {
      this.currentSessionId = hgr.getSessionId();
      // Keep the join code so the lobby/waiting screen can surface it (private games only; null for
      // public). The transition below passes it to onHostingLobby (.scratch/private-join-code).
      this.hostedGameCode = hgr.getGameCode();
      printToConsole(
          "Game hosted successfully! Session ID: "
              + hgr.getSessionId()
              + (hgr.getGameCode() != null
                  ? ". Private Code for others: " + hgr.getGameCode()
                  : ". This is a public game."));
      printToConsole("Waiting for another player to join... (type cancel to cancel the session)");
      currentState.set(ClientState.HOSTING_LOBBY_WAITING);
    } else {
      this.hostedGameCode = null;
      printToConsole("Failed to host game: " + hgr.getMessage());
      // Revert to the appropriate state
      if (preWaitingState == ClientState.SELECTING_HOST_CASE
          || currentState.get() == ClientState.SENDING_HOST_REQUEST) {
        currentState.set(ClientState.SELECTING_HOST_CASE);
      } else {
        currentState.set(ClientState.SELECTING_HOST_TYPE);
      }
    }
    // NEW: Clean up the temporary hosting state AFTER the server has responded.
    this.availableCasesCache = null;
    this.caseIndexForLanguageSelection = -1;
  }

  private void handlePublicGamesList(PublicGamesListDTO pgl) {
    if (currentState.get() != ClientState.REQUESTING_PUBLIC_GAMES) {
      printToConsole("Received public games list unexpectedly.");
      return;
    }
    this.publicGamesCache = pgl.getGames();
    if (publicGamesCache.isEmpty()) {
      printToConsole("No public games available to join right now.");
    }

    printToConsole("--- Available Public Games ---");
    printToConsole("0. Refresh List");
    printToConsole("00. Join by Code");
    printToConsole("000. Back to Main Menu");

    if (!publicGamesCache.isEmpty()) {
      for (int i = 0; i < publicGamesCache.size(); i++) {
        PublicGameInfoDTO gameInfo = publicGamesCache.get(i);
        printToConsole(
            (i + 1)
                + ". Hosted by: "
                + gameInfo.getHostPlayerDisplayId()
                + " | Case: "
                + gameInfo.getCaseTitle()
                + " (ID: "
                + gameInfo
                    .getSessionId()
                    .substring(0, Math.min(8, gameInfo.getSessionId().length()))
                + "..)");
      }
    }
    currentState.set(ClientState.VIEWING_PUBLIC_GAMES);
  }

  private void handleJoinGameResponse(JoinGameResponseDTO jgr) {
    if (jgr.isSuccess()) {
      this.currentSessionId = jgr.getSessionId();
      String msg = jgr.getMessage();
      if (msg != null) {
        String lowerMsg = msg.toLowerCase();
        if (lowerMsg.contains("private game: ")) {
          this.currentCaseTitle = msg.substring(lowerMsg.indexOf("private game: ") + 14).trim();
        } else if (lowerMsg.contains("joined game: ")) {
          this.currentCaseTitle = msg.substring(lowerMsg.indexOf("joined game: ") + 13).trim();
        }
      }
      printToConsole(
          "Successfully joined game session: " + jgr.getSessionId() + ". " + jgr.getMessage());
      currentState.set(ClientState.IN_LOBBY_AWAITING_START);
    } else {
      // GUI MODE: Notify the MainController to show an alert and refresh.
      if (listener != null) {
        listener.onJoinGameFailed(jgr.getMessage());
        // The client's job is done after a failed join, so we stop it.
        // The MainController is now responsible for the UI.
        stopClient();
      }
      // CONSOLE MODE: Fallback to old behavior.
      else {
        printToConsole("Failed to join game: " + jgr.getMessage());
        if (preWaitingState == ClientState.VIEWING_PUBLIC_GAMES
            || currentState.get() == ClientState.SENDING_JOIN_PUBLIC_REQUEST) {
          currentState.set(ClientState.VIEWING_PUBLIC_GAMES);
        } else if (preWaitingState == ClientState.ENTERING_PRIVATE_CODE
            || currentState.get() == ClientState.SENDING_JOIN_PRIVATE_REQUEST) {
          currentState.set(ClientState.ENTERING_PRIVATE_CODE);
        } else {
          currentState.set(ClientState.SELECTING_JOIN_TYPE);
        }
      }
    }
  }

  private void handleLobbyUpdate(LobbyUpdateDTO lu) {
    printToConsole("[LOBBY UPDATE] " + lu.getMessage());
    if (!lu.getPlayerDisplayIdsInLobbyOrGame().isEmpty()) {
      printToConsole(
          "Players now in session: " + String.join(", ", lu.getPlayerDisplayIdsInLobbyOrGame()));
    }
    cacheLobbyIdentities(lu);
    if (lu.getHostPlayerId() != null) {
      this.hostPlayerIdInSession = lu.getHostPlayerId();
      log("Host ID for current session set to: " + this.hostPlayerIdInSession);
      if (isThisClientTheHost()) {
        printToConsole("(You are the HOST of this session)");
      } else {
        printToConsole(
            "(You are a GUEST in this session. Host is: " + this.hostPlayerIdInSession + ")");
      }
    }

    if (lu.isGameStarting()) {
      if (lu.getTasks() != null && !lu.getTasks().isEmpty()) {
        this.currentTasks = lu.getTasks();
      }
      if (listener != null && lu.getCaseInvitation() != null && !lu.getCaseInvitation().isEmpty()) {
        currentState.set(ClientState.SHOWING_INVITATION);
        listener.onReceiveCaseInvitation(lu.getCaseInvitation(), isThisClientTheHost());
      } else if (currentState.get() != ClientState.IN_LOBBY_AWAITING_START) {
        printToConsole("The game session is now ready for the host to type 'start case'.");
        currentState.set(ClientState.IN_LOBBY_AWAITING_START);
      }
    } else if (currentState.get() == ClientState.HOSTING_LOBBY_WAITING
        && lu.getPlayerDisplayIdsInLobbyOrGame().size() >= NetworkConstants.MAX_PLAYERS_PER_GAME) {
      if (isThisClientTheHost()) {
        printToConsole("Your opponent has joined. As host, type 'start case' to begin.");
      } else {
        printToConsole("Lobby is full. Waiting for host to start the case.");
      }
      currentState.set(ClientState.IN_LOBBY_AWAITING_START);
    }
  }

  /**
   * Records the per-player display names + avatar ids carried by a lobby update (positionally
   * paired lists, same index = same player), so the lobby can render each player's real name and
   * portrait.
   */
  private void cacheLobbyIdentities(LobbyUpdateDTO lu) {
    List<String> actualIds = lu.getPlayerIdsInSession();
    List<String> displayIds = lu.getPlayerDisplayIdsInLobbyOrGame();
    List<String> avatarIds = lu.getPlayerAvatarIds();
    if (actualIds == null || actualIds.isEmpty()) {
      return;
    }
    lobbyPlayerOrder = new ArrayList<>(actualIds);
    for (int i = 0; i < actualIds.size(); i++) {
      String pid = actualIds.get(i);
      if (pid == null) {
        continue;
      }
      if (i < displayIds.size() && displayIds.get(i) != null) {
        displayNameByPlayer.put(pid, displayIds.get(i));
      }
      if (i < avatarIds.size() && avatarIds.get(i) != null) {
        avatarByPlayer.put(pid, avatarIds.get(i));
      }
    }
    notifyLobbyIdentitiesUpdated();
  }

  private void handleExamQuestion(ExamQuestionDTO eq) {
    consoleLock.lock();
    try {
      printToConsole(
          "\n--- FINAL EXAM QUESTION "
              + (eq.getQuestionIndex() + 1)
              + " of "
              + eq.getTotalQuestions()
              + " ---");
      printToConsole(eq.getQuestionPrompt());
      for (Map.Entry<String, FinalExamSlotDTO> entry : eq.getSlots().entrySet()) {
        printToConsole("\n" + entry.getKey() + " choices:");
        for (int i = 0; i < entry.getValue().getChoices().size(); i++) {
          printToConsole(
              "  " + (i + 1) + ") " + entry.getValue().getChoices().get(i).getChoiceText());
        }
      }
      printToConsole("\nEnter your choices (e.g., '1,2'):");
      this.currentExamQuestionNumberBeingAnswered = eq.getQuestionIndex() + 1;
    } finally {
      consoleLock.unlock();
    }

    // Phase 1e (.scratch/typed-game-events): the server DTO must drive the GUI, not only the
    // console. Sync the authoritative question index so the next submit carries it (the engine
    // drops a stale index), then render the question — mirroring handleExamResult's showExamResults
    // call. The FX-thread hop lives inside FinalExamViewController.displayQuestion, so the
    // transport
    // layer calls the listener directly. The terminal client has a null listener (console only).
    if (finalExamController != null) {
      finalExamController.syncCurrentQuestionIndex(eq.getQuestionIndex());
    }
    if (listener instanceof client.exam.FinalExamListener finalExamListener) {
      finalExamListener.showQuestion(eq);
    }
  }

  private void handleExamResult(ExamResultDTO er) {
    consoleLock.lock();
    try {
      printToConsole("\n--- FINAL EXAM RESULT ---");
      printToConsole(er.toString());
      printToConsole("[SERVER] --- Final Exam Concluded ---");

      if (listener != null) {
        listener.showExamResults(er);
      }

      if (currentSessionId != null) {
        currentState.set(ClientState.IN_GAME);
        log(
            "Exam concluded. Client state set to IN_GAME. You can continue investigating or type 'exit'.");
        printToConsole("You can now continue investigating or type 'exit' to leave the game.");
      } else {
        currentState.set(ClientState.CONNECTED_IDLE);
      }
    } finally {
      consoleLock.unlock();
    }
  }

  private void handleReturnToLobby(ReturnToLobbyDTO rtl) {
    consoleLock.lock();
    try {
      printToConsole("[SERVER] " + rtl.getMessage());
      this.currentSessionId = null;
      this.hostedGameCode = null;
      this.availableCasesCache = null;
      this.publicGamesCache = null;
      this.hostPlayerIdInSession = null;
      this.intentToHostPublic = true;
      this.currentExamQuestionNumberBeingAnswered = -1;

      if (launchMode == LaunchMode.HOST_ONLY || launchMode == LaunchMode.JOIN_ONLY) {
        stopClient();
        log("Received ReturnToLobbyDTO in HOST_ONLY/JOIN_ONLY mode. Stopping client.");
      } else {
        currentState.set(ClientState.CONNECTED_IDLE);
        if (listener != null) {
          listener.onReturnToMainMenu(rtl.getMessage());
        }
        log(
            "Received ReturnToLobbyDTO. Client state set to CONNECTED_IDLE and onReturnToMainMenu called.");
      }
    } finally {
      consoleLock.unlock();
    }
  }

  private void handleInitiateFinalExam(InitiateFinalExamDTO dto) {
    finalExamController =
        new client.exam.FinalExamController(
            dto.getFinalExam(),
            (client.exam.FinalExamListener) listener,
            this,
            isThisClientTheHost());
    finalExamController.startExam();
  }

  private void handleExamAnswerSelected(ExamAnswerSelectedDTO dto) {
    if (finalExamController != null) {
      finalExamController.updateAnswer(dto.getQuestionIndex(), dto.getSlotId(), dto.getChoiceId());
    }
  }

  private void handleFinalExamRequest(FinalExamRequestDTO dto) {
    if (listener != null) {
      listener.onFinalExamRequest(dto.getRequesterDisplayName());
    }
  }

  private void processServerMessage(Object message) {
    consoleLock.lock();
    try {
      ClientState previousStateForErrorCheck = currentState.get();
      if (message instanceof TextMessage) {
        handleTextMessage((TextMessage) message, previousStateForErrorCheck);
      } else if (message instanceof ChatMessage) {
        handleChatMessage((ChatMessage) message);
      } else if (message instanceof RoomDescriptionDTO) {
        handleRoomDescription((RoomDescriptionDTO) message);
      } else if (message instanceof AvailableCasesDTO) {
        handleAvailableCases((AvailableCasesDTO) message);
      } else if (message instanceof HostGameResponseDTO) {
        handleHostGameResponse((HostGameResponseDTO) message);
      } else if (message instanceof PublicGamesListDTO) {
        handlePublicGamesList((PublicGamesListDTO) message);
      } else if (message instanceof JoinGameResponseDTO) {
        handleJoinGameResponse((JoinGameResponseDTO) message);
      } else if (message instanceof LobbyUpdateDTO) {
        handleLobbyUpdate((LobbyUpdateDTO) message);
      } else if (message instanceof JournalEntryDTO) {
        journalEntries.add((JournalEntryDTO) message);
        if (listener != null) {
          listener.onJournalUpdated();
        }
      } else if (message instanceof DialogueEventDTO) {
        // Resolve a generic Watson response's UI-language key once, before both render sites.
        DialogueEventDTO de = dialogueLocalizer.apply((DialogueEventDTO) message);
        if (listener != null) {
          listener.onDialogueEvent(de);
        }
        // Print to console for terminal history (MP format matches SP cleanup)
        printToConsole(de.getTitle() + ": " + de.getText());
      } else if (message instanceof ExamQuestionDTO) {
        handleExamQuestion((ExamQuestionDTO) message);
      } else if (message instanceof PlayerNameChangedDTO) {
        handlePlayerNameChanged((PlayerNameChangedDTO) message);
      } else if (message instanceof PlayerAvatarChangedDTO) {
        handlePlayerAvatarChanged((PlayerAvatarChangedDTO) message);
      } else if (message instanceof ExamResultDTO) {
        handleExamResult((ExamResultDTO) message);
      } else if (message instanceof InitiateFinalExamDTO) {
        handleInitiateFinalExam((InitiateFinalExamDTO) message);
      } else if (message instanceof ExamAnswerSelectedDTO) {
        handleExamAnswerSelected((ExamAnswerSelectedDTO) message);
      } else if (message instanceof FinalExamRequestDTO) {
        handleFinalExamRequest((FinalExamRequestDTO) message);
      } else if (message instanceof ReturnToLobbyDTO) {
        handleReturnToLobby((ReturnToLobbyDTO) message);
      } else if (message instanceof TaskStateUpdateDTO) {
        handleTaskStateUpdate((TaskStateUpdateDTO) message);
      } else if (message instanceof NpcMovedDTO) {
        handleNpcMoved((NpcMovedDTO) message);
      } else if (message instanceof common.dto.InsightTokenUpdateDTO) {
        common.dto.InsightTokenUpdateDTO tokenDto = (common.dto.InsightTokenUpdateDTO) message;
        log("Received InsightTokenUpdateDTO count=" + tokenDto.getCount());
        if (listener != null) {
          listener.onInsightTokensUpdate(tokenDto.getCount());
        }
      } else if (message instanceof common.dto.DeductionCountUpdateDTO) {
        common.dto.DeductionCountUpdateDTO deduceUpdate =
            (common.dto.DeductionCountUpdateDTO) message;
        if (listener != null) {
          listener.onDeductionCountUpdate(deduceUpdate.getCount());
        }
      } else if (message instanceof common.dto.CommandCooldownUpdateDTO cooldownUpdate) {
        if (listener != null) {
          listener.onCommandCooldownUpdate(
              cooldownUpdate.getCommandType(), cooldownUpdate.getCooldownUntil());
        }
      } else if (message instanceof ClientIdAssignmentDTO idDto) {
        this.playerId = idDto.getPlayerId();
        this.playerDisplayId = idDto.getAssignedDisplayId();
        printToConsole(
            "Server registration complete. Your Player ID: "
                + this.playerId
                + ", Display Name: "
                + this.playerDisplayId);
        announceProfileIdentity();
      } else if (message instanceof UpdatePinboardCommand) {
        if (pinboardUpdateListener != null) {
          pinboardUpdateListener.accept(((UpdatePinboardCommand) message).getUpdate());
        }
      } else if (message instanceof PinboardStateResponseCommand) {
        if (pinboardStateListener != null) {
          pinboardStateListener.accept(((PinboardStateResponseCommand) message).getState());
        }
      } else {
        printToConsole("[UNHANDLED DTO] " + message.getClass().getSimpleName());
      }
    } finally {
      consoleLock.unlock();
    }
  }

  private void handlePlayerNameChanged(PlayerNameChangedDTO pnc) {
    printToConsole("[INFO] " + pnc.toString());
    displayNameByPlayer.put(pnc.getPlayerId(), pnc.getNewDisplayName());
    if (pnc.getPlayerId().equals(this.playerId)) {
      this.playerDisplayId = pnc.getNewDisplayName();
      log("My display name confirmed/updated by server to: " + this.playerDisplayId);
    }
    notifyLobbyIdentitiesUpdated();
  }

  private void handlePlayerAvatarChanged(PlayerAvatarChangedDTO pac) {
    printToConsole("[INFO] " + pac.toString());
    avatarByPlayer.put(pac.getPlayerId(), pac.getNewAvatarId());
    if (pac.getPlayerId().equals(this.playerId)) {
      this.ownAvatarId = pac.getNewAvatarId();
    }
    notifyLobbyIdentitiesUpdated();
  }

  /**
   * Announces the local profile identity to the server once registration completes: the profile
   * display name via {@link UpdateDisplayNameCommand} (skipped when blank, leaving the random name)
   * and the chosen avatar via {@link UpdateAvatarCommand}. Also seeds our own identity into the
   * local cache so the lobby can show us even before any broadcast returns.
   */
  private void announceProfileIdentity() {
    String announcedName = profileDisplayName(initialDisplayName);
    // Seed our own identity locally so the lobby can show us before any broadcast returns.
    if (playerId != null) {
      displayNameByPlayer.put(playerId, announcedName != null ? announcedName : playerDisplayId);
      avatarByPlayer.put(playerId, ownAvatarId);
    }
    if (announcedName != null) {
      this.playerDisplayId = announcedName; // optimistic local update
    }
    for (Command cmd : identityAnnounceCommands(initialDisplayName, ownAvatarId)) {
      sendToServer(cmd);
    }
  }

  /**
   * The profile display name to announce — trimmed, non-blank and within the wire limit — or {@code
   * null} to leave the server-assigned random name in place.
   */
  static String profileDisplayName(String profileName) {
    if (profileName == null || profileName.isBlank()) {
      return null;
    }
    String name = profileName.trim();
    return name.length() <= common.WireLimits.MAX_DISPLAY_NAME_LENGTH ? name : null;
  }

  /**
   * The identity commands a client announces on registration (player-profile feature): an {@link
   * UpdateDisplayNameCommand} carrying the profile name when one is set (so the player never
   * retypes it via {@code /setname}), and always an {@link UpdateAvatarCommand} for the chosen
   * preset (an unknown id falls back to the default). Pure so the feeding behaviour is directly
   * testable.
   */
  static List<Command> identityAnnounceCommands(String profileName, String avatarId) {
    List<Command> commands = new ArrayList<>();
    String name = profileDisplayName(profileName);
    if (name != null) {
      commands.add(new UpdateDisplayNameCommand(new UpdateDisplayNameRequestDTO(name)));
    }
    String avatar =
        common.PlayerAvatars.isValid(avatarId) ? avatarId : common.PlayerAvatars.DEFAULT_ID;
    commands.add(new UpdateAvatarCommand(new UpdateAvatarRequestDTO(avatar)));
    return commands;
  }

  private void notifyLobbyIdentitiesUpdated() {
    if (listener != null) {
      listener.onLobbyIdentitiesUpdated();
    }
  }

  private void handleTaskStateUpdate(TaskStateUpdateDTO ts) {
    taskStates.put(ts.getTaskIndex(), ts.getIsCompleted());
    checkIfAllTasksCompleted();
    if (listener != null) {
      listener.onTaskStateUpdate(ts.getTaskIndex(), ts.getIsCompleted());
    }
  }

  private void checkIfAllTasksCompleted() {
    if (currentTasks == null || currentTasks.isEmpty()) {
      return;
    }
    boolean all = true;
    for (int i = 0; i < currentTasks.size(); i++) {
      if (!taskStates.getOrDefault(i, false)) {
        all = false;
        break;
      }
    }
    // Removed finalExamUnlocked logic. Exam is always available.
    // We can still notify UI if we want a visual indicator, but no unlocking.
    if (all && listener != null) {
      // listener.onFinalExamUnlocked(); // Optional: Keep or remove depending on if
      // we want the pop-up
    }
  }

  private void updateClientStateBeforeSending(Command command) {
    ClientState current = currentState.get();
    ClientState nextState = determineNextStateForOutgoingCommand(command, current);

    if (nextState != current) {
      if (nextState.isPrimarilyWaiting()) {
        this.preWaitingState = current;
        log(
            "Transitioning from "
                + current
                + " to waiting state "
                + nextState
                + " (preWaitingState set to "
                + this.preWaitingState
                + ")");
      } else {
        this.preWaitingState = null;
        log("Transitioning from " + current + " to " + nextState + " (preWaitingState cleared)");
      }
      currentState.set(nextState);
    }
  }

  private ClientState determineNextStateForOutgoingCommand(Command command, ClientState current) {
    if (command instanceof RequestCaseListCommand) {
      return ClientState.REQUESTING_CASE_LIST_FOR_HOST;
    } else if (command instanceof HostGameCommand) {
      return ClientState.SENDING_HOST_REQUEST;
    } else if (command instanceof ListPublicGamesCommand) {
      return ClientState.REQUESTING_PUBLIC_GAMES;
    } else if (command instanceof JoinPublicGameCommand) {
      return ClientState.SENDING_JOIN_PUBLIC_REQUEST;
    } else if (command instanceof JoinPrivateGameCommand) {
      return ClientState.SENDING_JOIN_PRIVATE_REQUEST;
    } else if (command instanceof InitiateFinalExamCommand) {
      return ClientState.ATTEMPTING_FINAL_EXAM;
    } else if (command instanceof SubmitExamAnswerCommand) {
      return ClientState.SUBMITTING_EXAM_ANSWER;
    } else if (command instanceof ExitCommand && currentSessionId != null) {
      // Any exit command while in a session should put us in a waiting state.
      return ClientState.SENDING_HOST_REQUEST; // A generic waiting state is fine.
    }
    return current;
  }

  private void attemptConnect() {
    if (connected.get() || currentState.get() == ClientState.CONNECTING) {
      return;
    }
    currentState.set(ClientState.CONNECTING);
    log(
        "Attempting to connect to server at "
            + host
            + ":"
            + port
            + " (Attempt "
            + (reconnectAttempts + 1)
            + ")");

    try {
      channel = SocketChannel.open();
      channel.configureBlocking(true);
      channel.connect(new InetSocketAddress(host, port));
      connected.set(true);
      reconnectAttempts = 0;

      // New logic to handle LaunchMode
      if (launchMode == LaunchMode.HOST_ONLY) {
        currentState.set(ClientState.SELECTING_HOST_TYPE);
        log(
            "Successfully connected. Launch mode is HOST_ONLY, transitioning to SELECTING_HOST_TYPE.");
      } else if (launchMode == LaunchMode.JOIN_ONLY) {
        log("Successfully connected. Launch mode is JOIN_ONLY, auto-sending join command.");
        // Heuristic: If the ID is short, it's a private code. Otherwise, it's a public
        // session ID.
        if (joinGameId != null && joinGameId.length() <= 6) { // Assuming private codes are short
          sendToServer(
              new JoinPrivateGameCommand(new JoinPrivateGameRequestDTO(joinGameId.toUpperCase())));
          currentState.set(ClientState.SENDING_JOIN_PRIVATE_REQUEST);
        } else {
          sendToServer(new JoinPublicGameCommand(new JoinPublicGameRequestDTO(joinGameId)));
          currentState.set(ClientState.SENDING_JOIN_PUBLIC_REQUEST);
        }
      } else {
        currentState.set(ClientState.CONNECTED_IDLE);
        log("Successfully connected. Launch mode is NORMAL, transitioning to CONNECTED_IDLE.");
      }

      if (networkListenerThread == null || !networkListenerThread.isAlive()) {
        networkListenerThread = new Thread(this::listenToServer, "GameClient-NetworkListener");
        networkListenerThread.setDaemon(true);
        networkListenerThread.start();
      }
    } catch (ConnectException e) {
      log("Connection refused by server at " + host + ":" + port + ". Server might be down.");
      handleDisconnect("Connection refused by server");
    } catch (IOException e) {
      logError("IOException during connection attempt: " + e.getMessage(), e);
      handleDisconnect("I/O error during connection");
    }
  }

  private void listenToServer() {
    log("Network listener started.");
    try {
      while (running.get() && connected.get() && channel != null && channel.isOpen()) {
        Object receivedObject = SerializationUtils.readFramedObject(channel);
        if (receivedObject != null) {
          processServerMessage(receivedObject);
        } else {
          if (connected.get()) {
            log("Server closed the connection (EOF).");
            handleDisconnect("Server closed connection");
          }
          break;
        }
      }
    } catch (IOException e) {
      if (running.get() && connected.get()) {
        logError("IOException in network listener: " + e.getMessage(), null);
        handleDisconnect("Network I/O error");
      }
    } catch (Exception e) {
      if (running.get() && connected.get()) {
        logError("Unexpected error in network listener: " + e.getMessage(), e);
        handleDisconnect("Unexpected listener error");
      }
    } finally {
      log("Network listener thread stopped.");
      if (running.get() && connected.get()) {
        handleDisconnect("Listener terminated unexpectedly");
      }
    }
  }

  private void sendToServer(Serializable object) {
    if (!connected.get() || channel == null || !channel.isOpen()) {
      printToConsole("Not connected to server. Cannot send message. Type 'connect' to try again.");
      return;
    }
    try {
      SerializationUtils.writeFramedObject(channel, object);
    } catch (IOException e) {
      logError("Error sending message to server: " + e.getMessage(), null);
      handleDisconnect("Send I/O error");
    }
  }

  private void handleDisconnect(String reason) {
    if (currentState.get() == ClientState.EXITING) {
      return; // Ignore disconnects during intentional shutdown
    }

    boolean wasConnected = connected.getAndSet(false);
    ClientState oldState = currentState.getAndSet(ClientState.RECONNECTING);

    if (wasConnected) {
      log("Disconnected from server. Reason: " + reason + ". Old state: " + oldState);
      printToConsole("\nConnection to server lost: " + reason);
    } else if (oldState == ClientState.CONNECTING) {
      log("Initial connection attempt failed. Reason: " + reason + ".");
      printToConsole("\nFailed to connect to server: " + reason);
    }

    this.currentSessionId = null;
    this.hostedGameCode = null;
    this.availableCasesCache = null;
    this.publicGamesCache = null;
    this.hostPlayerIdInSession = null;

    if (channel != null && channel.isOpen()) {
      try {
        channel.close();
      } catch (IOException e) {
        logError("IOException closing channel on disconnect", null);
      }
    }
    channel = null;

    if (networkListenerThread != null && networkListenerThread.isAlive()) {
      networkListenerThread.interrupt();
    }
    networkListenerThread = null;
  }

  public void stopClient() {
    log("Client stop requested.");
    running.set(false);
    if (networkListenerThread != null) networkListenerThread.interrupt();
    if (mainThread != null && mainThread != Thread.currentThread()) {
      mainThread.interrupt();
    }
    if (channel != null && channel.isOpen()) {
      try {
        channel.close();
      } catch (IOException e) {
        logError("Exception closing channel on stop", null);
      }
    }
    connected.set(false);
    currentState.set(ClientState.EXITING);
    printToConsole("Exiting client...");
  }

  private void shutdownClientResources() {
    if (networkListenerThread != null) {
      try {
        networkListenerThread.join(500);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    log("Client resources shut down.");
  }

  private void log(String message) {
    logger.info(message);
  }

  private void logError(String message, Throwable t) {
    logger.error(message, t);
  }

  private void printToConsole(String message) {
    consoleLock.lock();
    try {
      consoleWriter.accept(message);
    } finally {
      consoleLock.unlock();
    }
  }

  private void handleNpcMoved(NpcMovedDTO nmd) {
    // Subtle update, no [GAME INFO] prefix
    printToConsole(nmd.toString());
  }

  public List<String> getCurrentCaseTasks() {
    return currentTasks;
  }

  public List<JournalEntryDTO> getJournalEntries() {
    return journalEntries;
  }

  public List<ChatMessage> getChatHistory() {
    return chatHistory;
  }

  public void sendDirectCommand(Command command) {
    if (command != null) {
      log("Sending direct command from GUI: " + command.getClass().getSimpleName());
      sendToServer(command);
    }
  }

  public LaunchMode getLaunchMode() {
    return launchMode;
  }

  public String getCaseTitle() {
    return currentCaseTitle;
  }

  public client.exam.FinalExamController getFinalExamController() {
    return finalExamController;
  }

  // REPLACE this method
  private void handleHostLanguageSelection(String input) {
    if ("0".equals(input)) {
      // Go back to case selection
      this.caseIndexForLanguageSelection = -1;
      currentState.set(ClientState.SELECTING_HOST_CASE);
      // We need to re-display the case list, but without triggering the
      // "unexpectedly" error.
      // The best way is to not call a handler directly. The main loop will reprint
      // the prompt.
      // To show the whole menu again, we can just print it.
      printToConsole("\n--- Select a Case to Host ---");
      for (int i = 0; i < availableCasesCache.size(); i++) {
        JsonDTO.CaseFile currentCase = availableCasesCache.get(i);
        String languages =
            currentCase.getLocalizations().values().stream()
                .map(JsonDTO.CaseFile.LocalizedData::getLanguageName)
                .collect(Collectors.joining(", "));
        printToConsole((i + 1) + ". " + currentCase.getUniversalTitle() + " [" + languages + "]");
      }
      return;
    }

    if (this.caseIndexForLanguageSelection < 0 || this.availableCasesCache == null) {
      printToConsole("Error: Case selection was lost. Returning to main menu.");
      currentState.set(ClientState.CONNECTED_IDLE);
      return;
    }

    try {
      int langNum = Integer.parseInt(input);
      JsonDTO.CaseFile selectedCase = availableCasesCache.get(this.caseIndexForLanguageSelection);
      List<String> langCodes = new ArrayList<>(selectedCase.getLocalizations().keySet());
      Collections.sort(langCodes);

      if (langNum > 0 && langNum <= langCodes.size()) {
        String selectedLangCode = langCodes.get(langNum - 1);
        sendHostRequest(selectedCase.getUniversalTitle(), selectedLangCode);
      } else {
        printToConsole("Invalid language number.");
      }
    } catch (NumberFormatException e) {
      printToConsole("Invalid input. Please enter a number.");
    }
  }

  private void sendHostRequest(String universalTitle, String languageCode) {
    boolean isActualPublicRequest = this.intentToHostPublic;
    printToConsole(
        "Creating "
            + (isActualPublicRequest ? "public" : "private")
            + " game for '"
            + universalTitle
            + "'...");

    HostGameRequestDTO payload =
        new HostGameRequestDTO(universalTitle, isActualPublicRequest, languageCode);
    HostGameCommand command = new HostGameCommand(payload);

    this.currentCaseTitle = universalTitle;
    this.preWaitingState = ClientState.SELECTING_HOST_TYPE;
    updateClientStateBeforeSending(command);
    sendToServer(command);

    // MODIFIED: DO NOT clean up state here. Wait for the server's response.
  }
}

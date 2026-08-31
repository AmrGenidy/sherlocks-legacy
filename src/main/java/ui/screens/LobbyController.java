package ui.screens;

import client.GameClient;
import client.discovery.DiscoveredGame;
import client.discovery.LanGameDiscoveryService;
import client.discovery.UdpLanGameDiscoveryService;
import common.NetworkConstants;
import common.dto.PublicGameInfoDTO;
import java.util.List;
import java.util.stream.Collectors;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import ui.MainController;
import ui.i18n.L10n;
import ui.menu.JoinCodePlate;
import ui.menu.MenuPage;
import ui.menu.PlaceSetting;
import ui.shell.ScreenController;

/**
 * Lobby screen (ADR-0002): everything between the main menu and being in a Game Session — the
 * Multiplayer hub (Host / Join), LAN discovery + join, the Host's embedded {@code GameServer}
 * lifecycle, the server-driven pre-game menus (host options, Case/Language selection), and the
 * two-seat lobby itself. Desktop multiplayer stays peer-hosted (ROADMAP hard constraint 2): the
 * embedded server lives here, in the host's app.
 *
 * <p>Every screen is a full-window {@link MenuPage} built on the shared menu chrome (MENU_DESIGN
 * #3/#4): engraved {@code .menu-plate} controls, a sunken-vellum code well, the host's join code on
 * a large copyable plate, and two engraved detective place-settings in the lobby. Presentation only
 * — the wire protocol (commands/DTOs) is untouched; choices still travel to the server exactly as
 * before, now fired by buttons instead of typed numbers. No dead ends: every screen carries an
 * explicit Back/Leave step and {@link #onEscape()} mirrors it.
 */
public class LobbyController implements ScreenController {

  private static final org.slf4j.Logger logger =
      org.slf4j.LoggerFactory.getLogger(LobbyController.class);

  private enum SubState {
    IDLE,
    HUB,
    JOIN_MENU,
    PROMPT_JOIN_CODE,
    CONNECTING,
    HOST_OPTIONS,
    CASE_SELECTION,
    LANGUAGE_SELECTION,
    HOSTING_LOBBY,
    PRIVATE_GAME_ENTRY,
    PUBLIC_GAMES_LIST,
    IN_LOBBY,
    DISCONNECTED
  }

  private final MainController shell;
  private final StackPane container = new StackPane();
  private SubState subState = SubState.IDLE;

  // The inputs of the lobby page currently shown, cached so an identity change (a peer's
  // name/avatar
  // arriving after the lobby is up) can rebuild the seats in place (player-profile feature).
  private String lastGameCode;
  private String lastInvitation;
  private boolean lastIsHost;
  private boolean lastWaiting;

  // Numbered options of the server-driven menu currently showing ([value, label] pairs), cached
  // when the view is built so terminal autocomplete can offer them
  // (.scratch/terminal-autocomplete issue 03). Volatile: written by listener threads, read on
  // the FX thread while typing.
  private volatile List<String[]> serverMenuOptions = List.of();

  // Join screen pieces kept so the discovered-games list and status line refresh in place.
  private FlowPane discoveredList;
  private Label joinStatus;
  private final LanGameDiscoveryService discoveryService = new UdpLanGameDiscoveryService();

  // Host case picker: the shared CaseSelectionView, the server-provided case list, and the language
  // the host chose locally (sent in answer to the server's follow-up language menu).
  private ui.menu.CaseSelectionView hostCaseView;
  private List<JsonDTO.CaseFile> hostCases;
  private String pendingHostLangCode;

  // --- Embedded Server & Multiplayer Config (host side) ---
  private server.GameServer embeddedServer;
  private Thread embeddedServerThread;
  private boolean embeddedServerRunning = false;
  private String configuredServerHost;
  private int configuredServerPort;

  public LobbyController(MainController shell) {
    this.shell = shell;
  }

  @Override
  public Node getView() {
    return container;
  }

  /**
   * Every lobby screen is a full-window menu page (MENU_DESIGN #3) — no terminal/sidebar chrome.
   */
  @Override
  public boolean usesFullWindow() {
    return true;
  }

  @Override
  public boolean handleTerminalInput(String input) {
    switch (subState) {
      case JOIN_MENU:
        handleJoinGameMenuInput(input);
        return true;
      case PROMPT_JOIN_CODE:
        handleJoinCodeInput(input);
        return true;
      case HOST_OPTIONS:
      case LANGUAGE_SELECTION:
      case HOSTING_LOBBY:
      case PRIVATE_GAME_ENTRY:
      case PUBLIC_GAMES_LIST:
      case IN_LOBBY:
        // Server-driven menus: the numbered options travel the wire as-is.
        sendToServer(input);
        return true;
      case CASE_SELECTION:
        // The host case picker is a full-window visual component; selection is by click, not
        // typing.
        return false;
      case HUB:
      case DISCONNECTED:
      case CONNECTING:
      case IDLE:
      default:
        return false;
    }
  }

  @Override
  public boolean onEscape() {
    switch (subState) {
      case HUB:
        shell.showMainMenuScreen();
        return true;
      case JOIN_MENU:
        stopDiscovery();
        shell.showMainMenuScreen();
        return true;
      case PROMPT_JOIN_CODE:
        showJoinMenu();
        return true;
      case HOST_OPTIONS:
        sendToServer("3"); // server-side "Back" — disconnects to main menu
        return true;
      case CASE_SELECTION:
        // The view steps the dossier back to the gallery, and the gallery back to host options.
        if (hostCaseView != null) {
          return hostCaseView.handleEscape();
        }
        sendToServer("0");
        return true;
      case LANGUAGE_SELECTION:
        sendToServer("0");
        return true;
      case HOSTING_LOBBY:
      case PRIVATE_GAME_ENTRY:
      case IN_LOBBY:
        sendToServer("cancel");
        return true;
      case PUBLIC_GAMES_LIST:
        sendToServer("000");
        return true;
      case CONNECTING:
        // The connecting view has no game buttons; without this, a hung connect attempt is a
        // dead end (navigation-ux-smoothness issue 02). returnToMainMenu marks the
        // disconnect manual, so the client thread's cleanup won't fight the UI.
        shell.returnToMainMenu();
        return true;
      case DISCONNECTED:
        shell.returnToMainMenu();
        return true;
      default:
        return false;
    }
  }

  @Override
  public void onLanguageChanged() {
    // Locally-driven screens are rebuilt here; server-driven ones rebuild on the next callback.
    switch (subState) {
      case HUB:
        showMultiplayerHub();
        break;
      case JOIN_MENU:
        showJoinMenu();
        break;
      case CASE_SELECTION:
        if (hostCaseView != null) {
          hostCaseView.rerender();
        }
        break;
      default:
        break;
    }
  }

  /**
   * The lobby's menu choices as bare completions (.scratch/terminal-autocomplete issue 03): the
   * numbered options the terminal prints for the current sub-state (0/00/000 included), so menu
   * input Tab-completes for anyone driving the lobby from the terminal.
   */
  @Override
  public ui.terminal.CompletionContext completionContext() {
    ui.terminal.CompletionContext.Builder builder = ui.terminal.CompletionContext.builder();
    switch (subState) {
      case JOIN_MENU:
        {
          List<DiscoveredGame> publicGames = discoveredPublicGames();
          for (int i = 0; i < publicGames.size(); i++) {
            builder.bareOption(String.valueOf(i + 1), (i + 1) + ". " + publicGames.get(i));
          }
          builder
              .bareOption("0", L10n.t("lobby.refreshOption"))
              .bareOption("00", L10n.t("lobby.joinByCodeOption"))
              .bareOption("000", L10n.t("lobby.backOption"));
          break;
        }
      case PROMPT_JOIN_CODE:
        builder.bareOption("0", "0. " + L10n.t("common.back"));
        break;
      case HOST_OPTIONS:
        builder
            .bareOption("1", "1. " + L10n.t("lobby.hostPublic"))
            .bareOption("2", "2. " + L10n.t("lobby.hostPrivate"))
            .bareOption("3", "3. " + L10n.t("common.back"));
        break;
      case PUBLIC_GAMES_LIST:
        for (String[] option : serverMenuOptions) {
          builder.bareOption(option[0], option[1]);
        }
        builder
            .bareOption("0", L10n.t("lobby.refreshOption"))
            .bareOption("00", L10n.t("lobby.joinByCodeOption"))
            .bareOption("000", L10n.t("lobby.backOption"));
        break;
      case HOSTING_LOBBY:
      case PRIVATE_GAME_ENTRY:
        builder.bareOption("cancel", L10n.t("common.cancel"));
        break;
      case IN_LOBBY:
        builder.command("start case").bareOption("cancel", L10n.t("common.cancel"));
        break;
      default:
        break;
    }
    return builder.build();
  }

  private void sendToServer(String input) {
    if (shell.getGameClient() != null) {
      shell.getGameClient().enqueueUserInput(input);
    }
  }

  /** Mounts this screen with the given page (FX thread marshalled by the shell). */
  private void mount(Node content, SubState state) {
    Platform.runLater(
        () -> {
          subState = state;
          // Page-turn between lobby sub-states (DESIGN.md §6); the first mount finds an empty
          // container and is carried by the screen-level entrance instead.
          ui.util.Motion.pageTurn(container, content, 1);
          shell.showScreen(this);
        });
  }

  // ====================== Shared menu chrome ======================

  private MenuPage page(String title, String subtitle) {
    return new MenuPage(title, subtitle);
  }

  /** An engraved menu plate; primary actions read as the petrol primary (MENU_DESIGN). */
  private Button plate(String text, boolean primary, Runnable action) {
    Button button = new Button(text);
    button.getStyleClass().add("menu-plate");
    if (primary) {
      button.getStyleClass().add("menu-plate--primary");
    }
    button.setMaxWidth(Double.MAX_VALUE);
    button.setOnAction(
        event -> {
          shell.playSound("click.wav");
          action.run();
        });
    return button;
  }

  /** A centered column of plates/cards with a sensible max width (the menu never scrolls). */
  private VBox centeredStack(Node... children) {
    VBox stack = new VBox(16, children);
    stack.setAlignment(Pos.CENTER);
    stack.setFillWidth(true);
    stack.setMaxWidth(520);
    stack.setMaxHeight(Region.USE_PREF_SIZE);
    return stack;
  }

  /** A bottom strip holding a single Back/Leave plate, left-aligned under the page. */
  private Node bottomStrip(String backTextKey, Runnable back) {
    Button button = plate(L10n.t(backTextKey), false, back);
    button.setMaxWidth(Region.USE_PREF_SIZE);
    HBox strip = new HBox(button);
    strip.setAlignment(Pos.CENTER_LEFT);
    strip.getStyleClass().add("menu-bottom-strip"); // match the other back-strips (gui-g6)
    return strip;
  }

  // ====================== Multiplayer hub ======================

  /**
   * The Host / Join chooser (MENU_DESIGN #3) — the entry page from the main-menu Multiplayer plate.
   */
  public void showMultiplayerHub() {
    MenuPage page = page(L10n.t("mp.hub.title"), L10n.t("mp.hub.subtitle"));
    Button host = plate(L10n.t("menu.hostMultiplayer"), true, this::startHostMultiplayer);
    Button join = plate(L10n.t("menu.joinMultiplayer"), false, this::startJoinMultiplayer);
    page.setContent(centeredStack(host, join));
    page.setBottomStrip(bottomStrip("mp.backToMenu", shell::showMainMenuScreen));
    mount(page, SubState.HUB);
  }

  // ====================== Join flow ======================

  public void startJoinMultiplayer() {
    startDiscovery();
    showJoinMenu();
  }

  public void showJoinMenu() {
    startDiscovery();
    MenuPage page = buildJoinPage();
    mount(page, SubState.JOIN_MENU);
    refreshPublicGamesList();
  }

  private void startDiscovery() {
    if (discoveryService instanceof UdpLanGameDiscoveryService) {
      ((UdpLanGameDiscoveryService) discoveryService).start();
    }
  }

  public void stopDiscovery() {
    if (discoveryService instanceof UdpLanGameDiscoveryService) {
      ((UdpLanGameDiscoveryService) discoveryService).stop();
    }
  }

  private MenuPage buildJoinPage() {
    MenuPage page = page(L10n.t("mp.join.title"), L10n.t("mp.join.subtitle"));

    // Sunken-vellum join-code well + a petrol "Join by code" action (MENU_DESIGN #3).
    Label codeCaption = new Label(L10n.t("mp.join.codeCaption"));
    codeCaption.getStyleClass().add("mp-section-caption");

    TextField codeField = new TextField();
    codeField.getStyleClass().add("mp-code-well");
    codeField.setPromptText(L10n.t("lobby.joinCodePrompt"));
    HBox.setHgrow(codeField, Priority.ALWAYS);

    Button joinByCode =
        plate(L10n.t("lobby.joinByCode"), true, () -> joinGameByCode(codeField.getText()));
    joinByCode.setMaxWidth(Region.USE_PREF_SIZE);
    codeField.setOnAction(event -> joinGameByCode(codeField.getText()));

    HBox codeRow = new HBox(12, codeField, joinByCode);
    codeRow.setAlignment(Pos.CENTER);
    codeRow.setMaxWidth(520);

    joinStatus = new Label();
    joinStatus.getStyleClass().add("mp-status");
    joinStatus.setWrapText(true);
    joinStatus.setManaged(false);
    joinStatus.setVisible(false);

    // Discovered games on the local network — engraved plates that wrap, never a scrollbar.
    Label listCaption = new Label(L10n.t("mp.join.networkCaption"));
    listCaption.getStyleClass().add("mp-section-caption");

    discoveredList = new FlowPane(16, 16);
    discoveredList.setAlignment(Pos.CENTER);
    discoveredList.setColumnHalignment(javafx.geometry.HPos.CENTER);

    Button refresh = plate(L10n.t("lobby.refresh"), false, this::refreshPublicGamesList);
    refresh.setMaxWidth(Region.USE_PREF_SIZE);
    HBox refreshRow = new HBox(refresh);
    refreshRow.setAlignment(Pos.CENTER);

    VBox content =
        centeredStack(codeCaption, codeRow, joinStatus, listCaption, discoveredList, refreshRow);
    content.setMaxWidth(640);
    page.setContent(content);
    page.setBottomStrip(
        bottomStrip(
            "mp.backToMenu",
            () -> {
              stopDiscovery();
              shell.showMainMenuScreen();
            }));
    return page;
  }

  private List<DiscoveredGame> discoveredPublicGames() {
    return discoveryService.getCurrentGames().stream()
        .filter(DiscoveredGame::isPublicGame)
        .collect(Collectors.toList());
  }

  private void refreshPublicGamesList() {
    if (discoveredList == null) {
      return;
    }
    setJoinStatus(null);
    discoveredList.getChildren().setAll(placeholder(L10n.t("lobby.searching")));

    discoveryService.refreshAsync();

    PauseTransition pause = new PauseTransition(Duration.millis(1200));
    pause.setOnFinished(event -> populateDiscoveredList(discoveredPublicGames()));
    pause.play();
  }

  private void populateDiscoveredList(List<DiscoveredGame> publicGames) {
    if (discoveredList == null) {
      return;
    }
    if (publicGames.isEmpty()) {
      discoveredList.getChildren().setAll(placeholder(L10n.t("lobby.noPublicGames")));
      return;
    }
    discoveredList.getChildren().clear();
    for (DiscoveredGame game : publicGames) {
      String label = L10n.t("lobby.hostedBy", game.getGameName(), game.getHostDisplayName());
      Button entry = plate(label, false, () -> joinGameByDiscovery(game));
      entry.setMaxWidth(Region.USE_PREF_SIZE);
      discoveredList.getChildren().add(entry);
    }
  }

  private Label placeholder(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("mp-waiting-caption");
    return label;
  }

  private void setJoinStatus(String text) {
    if (joinStatus == null) {
      return;
    }
    boolean show = text != null && !text.isBlank();
    joinStatus.setText(show ? text : "");
    joinStatus.setManaged(show);
    joinStatus.setVisible(show);
  }

  private void handleJoinGameMenuInput(String input) {
    if (input.equals("0")) {
      refreshPublicGamesList();
    } else if (input.equals("00")) {
      subState = SubState.PROMPT_JOIN_CODE;
    } else if (input.equals("000")) {
      stopDiscovery();
      shell.showMainMenuScreen();
    } else {
      try {
        int gameNum = Integer.parseInt(input);
        List<DiscoveredGame> publicGames = discoveredPublicGames();
        if (gameNum > 0 && gameNum <= publicGames.size()) {
          joinGameByDiscovery(publicGames.get(gameNum - 1));
        } else {
          setJoinStatus(L10n.t("lobby.invalidGameNumber"));
        }
      } catch (NumberFormatException e) {
        setJoinStatus(L10n.t("lobby.invalidJoinInput"));
      }
    }
  }

  private void handleJoinCodeInput(String input) {
    if (input.equalsIgnoreCase("back") || input.equals("0")) {
      showJoinMenu();
    } else {
      joinGameByCode(input);
    }
  }

  public void joinGameByDiscovery(DiscoveredGame game) {
    if (game == null) {
      setJoinStatus(L10n.t("lobby.cannotJoinNull"));
      return;
    }
    this.configuredServerHost = game.getHostIp();
    this.configuredServerPort = game.getPort();
    connect(GameClient.LaunchMode.JOIN_ONLY, game.getSessionId());
  }

  public boolean joinGameByCode(String code) {
    if (code == null || code.trim().isEmpty()) {
      setJoinStatus(L10n.t("lobby.joinCodeEmpty"));
      return false;
    }

    if (discoveryService instanceof UdpLanGameDiscoveryService) {
      java.util.Optional<DiscoveredGame> gameToJoin =
          ((UdpLanGameDiscoveryService) discoveryService).findByCode(code);

      if (gameToJoin.isPresent()) {
        DiscoveredGame game = gameToJoin.get();
        this.configuredServerHost = game.getHostIp();
        this.configuredServerPort = game.getPort();
        connect(GameClient.LaunchMode.JOIN_ONLY, code);
        return true;
      } else {
        setJoinStatus(L10n.t("lobby.noGameWithCode"));
        return false;
      }
    } else {
      setJoinStatus(L10n.t("lobby.joinOnlyUdp"));
      return false;
    }
  }

  // ====================== Host flow ======================

  public void startHostMultiplayer() {
    if (embeddedServerRunning) {
      shell.appendTerminalText("\n" + L10n.t("lobby.serverAlreadyRunning") + "\n");
      return;
    }

    shell.updateStatus(L10n.t("lobby.serverStarting"));

    final java.util.concurrent.CountDownLatch startupLatch =
        new java.util.concurrent.CountDownLatch(1);
    final java.util.concurrent.atomic.AtomicBoolean serverStartedSuccessfully =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    embeddedServerThread =
        new Thread(
            () -> {
              try {
                embeddedServer = new server.GameServer(NetworkConstants.DEFAULT_PORT);
                embeddedServer.startServer(); // This can throw BindException
                serverStartedSuccessfully.set(true);
                startupLatch.countDown(); // Signal success
                embeddedServer.run(); // This starts the server's main loop
              } catch (java.net.BindException e) {
                Platform.runLater(
                    () ->
                        shell.appendTerminalText(
                            "\n"
                                + L10n.t("lobby.serverPortInUse", NetworkConstants.DEFAULT_PORT)
                                + "\n"));
                startupLatch.countDown(); // Signal failure
              } catch (Exception e) {
                Platform.runLater(
                    () ->
                        shell.appendTerminalText(
                            "\n" + L10n.t("lobby.serverStartFailed", e.getMessage()) + "\n"));
                logger.error("Embedded server failed to start", e);
                startupLatch.countDown(); // Signal failure
              }
            },
            "Embedded-GameServer-Thread");

    embeddedServerThread.setDaemon(true);
    embeddedServerThread.start();

    try {
      // Wait for the server to either start successfully or fail
      startupLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      Platform.runLater(
          () -> shell.appendTerminalText("\n" + L10n.t("lobby.serverInterrupted") + "\n"));
      return;
    }

    if (serverStartedSuccessfully.get()) {
      embeddedServerRunning = true;
      // Configure client to connect to the new embedded server
      this.configuredServerHost = "localhost";
      this.configuredServerPort = NetworkConstants.DEFAULT_PORT;
      connect(GameClient.LaunchMode.HOST_ONLY, null);
    } else {
      // Server failed to start, cleanup and return to menu
      embeddedServer = null;
      embeddedServerThread = null;
      Platform.runLater(shell::showMainMenuScreen);
    }
  }

  /** Boots the GameClient against the configured host/port; the shell stays the listener. */
  private void connect(GameClient.LaunchMode launchMode, String joinGameId) {
    shell.resetForMultiplayer();

    GameClient gameClient =
        new GameClient(
            configuredServerHost,
            configuredServerPort,
            // Route MP server output through the SAME single append point as single-player; it owns
            // the FX-thread marshal and the auto-scroll, so no per-call runLater here (.scratch/
            // terminal-scroll-mp DEC-1).
            line -> shell.appendTerminalText(line + "\n"),
            launchMode,
            joinGameId);
    gameClient.setListener(shell);
    // Localize the engine's generic Watson responses to this client's UI language before rendering
    // (.scratch/gui-localized-watson-hints phase 2).
    gameClient.setDialogueLocalizer(ui.i18n.WatsonDialogue::localize);
    // Console-scaffolding labels in the UI language (room blocks etc.); wire content untouched.
    gameClient.setGameTexts(new ui.i18n.L10nGameTexts());
    // Feed the saved profile so the player's name + avatar are announced automatically on connect —
    // no manual /setname (player-profile feature). A blank profile name leaves the random default.
    ui.settings.PlayerProfile profile = shell.getPlayerProfile();
    if (profile != null) {
      gameClient.setInitialIdentity(profile.displayName(), profile.avatarId());
    }

    Thread gameClientThread =
        new Thread(
            () -> {
              try {
                gameClient.run();
              } catch (Exception e) {
                logger.error("GameClient thread terminated with an error", e);
              } finally {
                // If this was a hosted game, shut down the server when the client finishes.
                if (launchMode == GameClient.LaunchMode.HOST_ONLY) {
                  shutdownEmbeddedServer();
                }

                // Only trigger automatic UI update if this wasn't a manual disconnect.
                // This prevents race conditions where returnToMainMenu() and this finally block
                // both try to update the UI simultaneously.
                if (!shell.isManualDisconnect()) {
                  Platform.runLater(
                      () -> {
                        // Ensure cleanup of client reference so the "Back" button works correctly
                        shell.clearGameClient();
                        if (launchMode == GameClient.LaunchMode.JOIN_ONLY) {
                          showJoinMenu();
                        } else {
                          shell.showMainMenuScreen();
                        }
                      });
                }
              }
            },
            "GameClient-Thread");
    gameClientThread.setDaemon(true);
    shell.setGameClient(gameClient, gameClientThread);
    gameClientThread.start();
  }

  /**
   * Tears down hosting on return-to-menu. When the embedded server is running, the shutdown is
   * delayed off-thread so in-flight broadcasts (e.g. ReturnToLobbyDTO) reach clients first.
   */
  public void stopHosting() {
    if (embeddedServerRunning) {
      final server.GameServer serverToStop = embeddedServer;
      final Thread serverThreadToStop = embeddedServerThread;

      // Mark server as not running to prevent re-entry or new connections from UI perspective
      embeddedServerRunning = false;
      embeddedServer = null;
      embeddedServerThread = null;

      // Run the actual shutdown in a background thread with a delay
      new Thread(
              () -> {
                try {
                  // 1 second delay to allow broadcast messages to propagate
                  Thread.sleep(1000);
                } catch (InterruptedException e) {
                  // Ignore interruption
                }

                Platform.runLater(
                    () ->
                        shell.appendTerminalText("\n" + L10n.t("lobby.serverShuttingDown") + "\n"));
                if (serverToStop != null) {
                  serverToStop.stopServer();
                }

                if (serverThreadToStop != null && serverThreadToStop.isAlive()) {
                  try {
                    serverThreadToStop.join(1000);
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                  if (serverThreadToStop.isAlive()) {
                    serverThreadToStop.interrupt();
                  }
                }
                Platform.runLater(
                    () -> shell.appendTerminalText(L10n.t("lobby.serverShutDown") + "\n"));
              })
          .start();
    } else {
      shutdownEmbeddedServer();
    }
  }

  public void shutdownEmbeddedServer() {
    if (embeddedServerRunning && embeddedServer != null) {
      Platform.runLater(
          () -> shell.appendTerminalText("\n" + L10n.t("lobby.serverShuttingDown") + "\n"));

      embeddedServer.stopServer(); // Signal the server to stop
      if (embeddedServerThread != null && embeddedServerThread.isAlive()) {
        try {
          // Give the server a moment to close connections
          embeddedServerThread.join(1000);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          Platform.runLater(
              () ->
                  shell.appendTerminalText(
                      "\n" + L10n.t("lobby.serverShutdownInterrupted") + "\n"));
        }
        if (embeddedServerThread.isAlive()) {
          embeddedServerThread.interrupt(); // Forcefully interrupt if it's stuck
        }
      }
      embeddedServerRunning = false;
      embeddedServer = null;
      embeddedServerThread = null;
      Platform.runLater(() -> shell.appendTerminalText(L10n.t("lobby.serverShutDown") + "\n"));
    }
  }

  // ====================== Server-driven lobby views ======================

  public void showConnecting() {
    MenuPage page = page(L10n.t("mp.connecting.title"), L10n.t("lobby.connecting"));
    Label caption = new Label(L10n.t("mp.connecting.caption"));
    caption.getStyleClass().add("mp-waiting-caption");
    caption.setWrapText(true);
    page.setContent(centeredStack(caption));
    page.setBottomStrip(bottomStrip("mp.backToMenu", shell::returnToMainMenu));
    mount(page, SubState.CONNECTING);
  }

  public void showDisconnected() {
    MenuPage page = page(L10n.t("mp.disconnected.title"), L10n.t("lobby.disconnected"));
    Button reconnect = plate(L10n.t("lobby.reconnect"), true, () -> sendToServer("connect"));
    Button mainMenu = plate(L10n.t("lobby.backToMenu"), false, shell::returnToMainMenu);
    page.setContent(centeredStack(reconnect, mainMenu));
    mount(page, SubState.DISCONNECTED);
  }

  public void showHostOptions() {
    MenuPage page = page(L10n.t("mp.host.title"), L10n.t("mp.host.chooseVisibility"));
    Button publicGame = plate(L10n.t("lobby.hostPublic"), false, () -> sendToServer("1"));
    Button privateGame = plate(L10n.t("lobby.hostPrivate"), true, () -> sendToServer("2"));
    page.setContent(centeredStack(publicGame, privateGame));
    page.setBottomStrip(bottomStrip("common.back", () -> sendToServer("3")));
    mount(page, SubState.HOST_OPTIONS);
  }

  /**
   * The host's case picker — the <b>same</b> {@link ui.menu.CaseSelectionView} single player uses
   * (MENU_DESIGN #2), over the server-provided case list. The component owns case + language
   * locally; on confirm {@link #hostConfirm} drives the existing two-step host protocol (case
   * index, then the language index in answer to {@link #showLanguageSelection}). No add-case tile:
   * the hostable list is the server's, not a local file.
   */
  public void showCaseSelection(List<JsonDTO.CaseFile> cases) {
    this.hostCases = cases;
    this.pendingHostLangCode = null;
    buildHostCaseView();
    mount(hostCaseView, SubState.CASE_SELECTION);
  }

  private void buildHostCaseView() {
    hostCaseView =
        new ui.menu.CaseSelectionView.Builder()
            .cases(hostCases)
            .title("mp.case.title", "mp.case.subtitle")
            .primaryLabel("mp.case.confirm")
            .galleryBackLabel("common.back")
            .solved(shell::isCaseSolved)
            .bestRank(t -> shell.bestRankLabel(t, hostCases).orElse(null))
            .onReview(shell::openReview)
            .playSound(shell::playSound)
            .onBack(() -> sendToServer("0")) // server-side back → host options
            .onConfirm(this::hostConfirm)
            .build();
  }

  /**
   * Confirm in the host case picker: remember the chosen language, then send the case index — the
   * server replies with its language menu, which {@link #showLanguageSelection} auto-answers. These
   * are the exact two numbered inputs the old two-screen host flow sent; the protocol is unchanged.
   */
  private void hostConfirm(JsonDTO.CaseFile caseFile, String langCode) {
    this.pendingHostLangCode = langCode;
    int caseIndex = indexOfCase(caseFile);
    if (caseIndex < 1) {
      sendToServer("0"); // shouldn't happen (case came from this list) — fall back to server back
      return;
    }
    sendToServer(String.valueOf(caseIndex));
  }

  private int indexOfCase(JsonDTO.CaseFile caseFile) {
    if (hostCases == null || caseFile == null) {
      return -1;
    }
    String title = caseFile.getUniversalTitle();
    for (int i = 0; i < hostCases.size(); i++) {
      if (java.util.Objects.equals(hostCases.get(i).getUniversalTitle(), title)) {
        return i + 1;
      }
    }
    return -1;
  }

  /**
   * The server's language menu for a hosted game. The unified picker already chose the language, so
   * this silently answers with that language's index (same numbered input the old language screen
   * sent) and shows no UI — the case picker stays up until the hosting lobby arrives.
   */
  public void showLanguageSelection(JsonDTO.CaseFile caseFile) {
    Platform.runLater(
        () -> {
          subState = SubState.LANGUAGE_SELECTION;
          String lang = pendingHostLangCode;
          pendingHostLangCode = null;
          if (lang != null) {
            int langIndex = languageIndex(caseFile, lang);
            if (langIndex >= 1) {
              sendToServer(String.valueOf(langIndex));
              return;
            }
          }
          // Defensive: no pending language (unexpected) — re-show the case picker rather than hang.
          if (hostCases != null) {
            showCaseSelection(hostCases);
          }
        });
  }

  private static int languageIndex(JsonDTO.CaseFile caseFile, String langCode) {
    if (caseFile.getLocalizations() == null) {
      return -1;
    }
    List<String> codes = new java.util.ArrayList<>(caseFile.getLocalizations().keySet());
    java.util.Collections.sort(codes);
    int idx = codes.indexOf(langCode);
    return idx < 0 ? -1 : idx + 1;
  }

  /**
   * The host's waiting screen — one seat filled, the partner seat awaiting, the join code plate.
   */
  public void showHostingLobby(String gameCode) {
    MenuPage page = buildLobbyPage(gameCode, null, true, true);
    mount(page, SubState.HOSTING_LOBBY);
  }

  public void showPublicGamesList(List<PublicGameInfoDTO> games) {
    List<String[]> options = new java.util.ArrayList<>();
    for (int i = 0; i < games.size(); i++) {
      PublicGameInfoDTO game = games.get(i);
      options.add(
          new String[] {
            String.valueOf(i + 1),
            (i + 1)
                + ". "
                + L10n.t("lobby.hostedBy", game.getCaseTitle(), game.getHostPlayerDisplayId())
          });
    }
    serverMenuOptions = List.copyOf(options);

    MenuPage page = page(L10n.t("mp.join.title"), L10n.t("mp.join.networkCaption"));
    FlowPane shelf = new FlowPane(16, 16);
    shelf.setAlignment(Pos.CENTER);
    shelf.setColumnHalignment(javafx.geometry.HPos.CENTER);
    if (games.isEmpty()) {
      shelf.getChildren().add(placeholder(L10n.t("lobby.noPublicGamesShort")));
    } else {
      for (int i = 0; i < games.size(); i++) {
        final int gameNum = i + 1;
        PublicGameInfoDTO game = games.get(i);
        Button gameButton =
            plate(
                L10n.t("lobby.hostedBy", game.getCaseTitle(), game.getHostPlayerDisplayId()),
                false,
                () -> sendToServer(String.valueOf(gameNum)));
        gameButton.setMaxWidth(Region.USE_PREF_SIZE);
        shelf.getChildren().add(gameButton);
      }
    }

    Button refresh = plate(L10n.t("lobby.refresh"), false, () -> sendToServer("0"));
    refresh.setMaxWidth(Region.USE_PREF_SIZE);
    Button byCode = plate(L10n.t("lobby.joinByCode"), false, () -> sendToServer("00"));
    byCode.setMaxWidth(Region.USE_PREF_SIZE);
    HBox controls = new HBox(12, refresh, byCode);
    controls.setAlignment(Pos.CENTER);

    VBox content = centeredStack(shelf, controls);
    content.setMaxWidth(720);
    page.setContent(content);
    page.setBottomStrip(bottomStrip("mp.backToMenu", () -> sendToServer("000")));
    mount(page, SubState.PUBLIC_GAMES_LIST);
  }

  public void showPrivateGameEntry() {
    MenuPage page = page(L10n.t("mp.join.title"), L10n.t("lobby.enterPrivateCode"));
    TextField codeField = new TextField();
    codeField.getStyleClass().add("mp-code-well");
    codeField.setPromptText(L10n.t("lobby.joinCodePrompt"));
    codeField.setMaxWidth(360);
    codeField.setOnAction(event -> sendToServer(codeField.getText()));
    Button submit =
        plate(L10n.t("lobby.joinByCode"), true, () -> sendToServer(codeField.getText()));
    submit.setMaxWidth(Region.USE_PREF_SIZE);
    HBox row = new HBox(12, codeField, submit);
    row.setAlignment(Pos.CENTER);
    page.setContent(centeredStack(row));
    page.setBottomStrip(bottomStrip("common.back", () -> sendToServer("cancel")));
    mount(page, SubState.PRIVATE_GAME_ENTRY);
  }

  public void setInLobby() {
    subState = SubState.IN_LOBBY;
  }

  // ====================== The two-seat lobby ======================

  /**
   * The lobby table (MENU_DESIGN #4): two engraved detective place-settings (host + partner), the
   * case invitation, and the ready/start controls. Called when both detectives are present — the
   * host gets "Begin investigation", the partner a warm waiting line.
   */
  public void showLobby(String invitation, boolean isHost) {
    MenuPage page = buildLobbyPage(null, invitation, isHost, false);
    mount(page, SubState.IN_LOBBY);
  }

  /**
   * Re-renders the seats when a player's display name or avatar changes (player-profile feature),
   * so a late identity announce is reflected on the already-shown lobby. No-op unless a lobby is
   * up.
   */
  public void onLobbyIdentitiesUpdated() {
    Platform.runLater(
        () -> {
          if (subState == SubState.IN_LOBBY || subState == SubState.HOSTING_LOBBY) {
            MenuPage page = buildLobbyPage(lastGameCode, lastInvitation, lastIsHost, lastWaiting);
            mount(page, subState);
          }
        });
  }

  /** The display name when set, else the localized seat-role fallback label. */
  private static String labelOr(String name, String fallbackKey) {
    return (name != null && !name.isBlank()) ? name : L10n.t(fallbackKey);
  }

  private MenuPage buildLobbyPage(
      String gameCode, String invitation, boolean isHost, boolean waiting) {
    // Remember the render inputs so an identity change (name/avatar) can rebuild the seats in
    // place.
    this.lastGameCode = gameCode;
    this.lastInvitation = invitation;
    this.lastIsHost = isHost;
    this.lastWaiting = waiting;

    String subtitle =
        waiting ? L10n.t("mp.lobby.awaitingSubtitle") : L10n.t("mp.lobby.readySubtitle");
    MenuPage page = page(L10n.t("mp.lobby.title"), subtitle);

    // Each seat shows the player's real display name + chosen avatar (player-profile feature). "me"
    // is the local client; the peer is the other player. The host always sits on the left seat.
    GameClient client = shell.getGameClient();
    String ownName = client != null ? client.getOwnDisplayName() : null;
    String ownAvatar = client != null ? client.getOwnAvatarId() : null;
    String peerName = client != null ? client.getPeerDisplayName() : null;
    String peerAvatar = client != null ? client.getPeerAvatarId() : null;
    String hostName = isHost ? ownName : peerName;
    String hostAvatar = isHost ? ownAvatar : peerAvatar;
    String guestName = isHost ? peerName : ownName;
    String guestAvatar = isHost ? peerAvatar : ownAvatar;

    PlaceSetting hostSeat =
        PlaceSetting.seated(
            labelOr(hostName, "mp.lobby.leadDetective"),
            hostAvatar,
            L10n.t("mp.lobby.hostBadge"),
            false);
    PlaceSetting partnerSeat =
        waiting
            ? PlaceSetting.awaiting(L10n.t("lobby.waitingForPlayer"))
            : PlaceSetting.seated(
                labelOr(guestName, "mp.lobby.partnerName"),
                guestAvatar,
                L10n.t("mp.lobby.readyBadge"),
                true);
    HBox table = new HBox(56, hostSeat, partnerSeat);
    table.setAlignment(Pos.CENTER);

    VBox center = new VBox(24);
    center.setAlignment(Pos.CENTER);
    center.setMaxHeight(Region.USE_PREF_SIZE);
    center.getChildren().add(table);

    if (waiting) {
      if (gameCode != null) {
        center
            .getChildren()
            .add(
                new JoinCodePlate(
                    gameCode,
                    L10n.t("mp.host.joinCodeCaption"),
                    L10n.t("mp.host.copy"),
                    L10n.t("mp.host.copied")));
        Label share = new Label(L10n.t("mp.host.joinCodeShare"));
        share.getStyleClass().add("mp-waiting-caption");
        share.setWrapText(true);
        share.setMaxWidth(360);
        center.getChildren().add(share);
      } else {
        Label note = new Label(L10n.t("mp.host.publicDiscoverable"));
        note.getStyleClass().add("mp-waiting-caption");
        note.setWrapText(true);
        center.getChildren().add(note);
      }
    } else {
      if (invitation != null && !invitation.isBlank()) {
        center.getChildren().add(invitationCard(invitation, page));
      }
      if (isHost) {
        Button begin = plate(L10n.t("mp.lobby.begin"), true, () -> sendToServer("start case"));
        begin.setMaxWidth(Region.USE_PREF_SIZE);
        center.getChildren().add(begin);
      } else {
        Label awaitHost = new Label(L10n.t("mp.lobby.awaitingHost"));
        awaitHost.getStyleClass().add("mp-waiting-caption");
        awaitHost.setWrapText(true);
        center.getChildren().add(awaitHost);
      }
    }

    page.setContent(center);
    page.setBottomStrip(bottomStrip("mp.lobby.leave", () -> sendToServer("cancel")));
    return page;
  }

  /**
   * The case invitation on a vellum dossier card; the letter is the one surface allowed to scroll.
   */
  private Node invitationCard(String text, MenuPage page) {
    Label letter = new Label(text);
    letter.getStyleClass().add("case-letter");
    letter.setWrapText(true);

    ScrollPane scroll = new ScrollPane(letter);
    scroll.setFitToWidth(true);
    scroll.getStyleClass().add("case-letter-scroll");

    VBox card = new VBox(scroll);
    card.getStyleClass().add("case-letter-card");
    card.setMaxWidth(700);
    card.maxHeightProperty().bind(page.heightProperty().multiply(0.32));
    VBox.setVgrow(scroll, Priority.ALWAYS);
    return card;
  }

  public void onJoinGameFailed(String message) {
    Platform.runLater(
        () -> {
          showJoinMenu();
          setJoinStatus(message);
        });
  }
}

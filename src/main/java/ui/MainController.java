package ui;

import client.GameClient;
import client.GameClientStateListener;
import client.exam.FinalExamListener;
import common.dto.ExamQuestionDTO;
import common.dto.ExamResultDTO;
import common.dto.PublicGameInfoDTO;
import common.dto.RoomDescriptionDTO;
import java.util.List;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.shape.Polygon;
import singleplayer.SinglePlayerMain;
import ui.i18n.L10n;
import ui.screens.ExamScreenController;
import ui.screens.GameScreenController;
import ui.screens.MenuController;
import ui.shell.ScreenController;
import ui.util.ImageManager;

/**
 * Navigation shell (ADR-0002): keeps the {@code fx:controller} binding, the FXML widgets, the
 * session state, and the inbound listener interfaces — and delegates every screen-owned callback to
 * the owning {@link ScreenController}. The only remaining legacy direct-pane state is the Case
 * Invitation.
 */
public class MainController
    implements GameClientStateListener, FinalExamListener, client.tutorial.TutorialHost {

  private static final org.slf4j.Logger logger =
      org.slf4j.LoggerFactory.getLogger(MainController.class);

  // Shell minimum pane sizes (8px scale) — must mirror the minHeight/minWidth values in
  // main.fxml; DividerClamp enforces them through every resize sequence (issue 01).
  private static final double MIN_ROOM_PANE_HEIGHT = 240;
  private static final double MIN_TERMINAL_HEIGHT = 160;
  private static final double MIN_CONTENT_WIDTH = 480;
  private static final double MIN_SIDEBAR_WIDTH = 216;

  private final ImageManager imageManager = new ImageManager();

  // Per-case ambient soundtrack (.scratch/per-case-soundtrack): one client-side service for the
  // whole
  // app, holding the persisted master volume/mute. The active case is captured at launch (SP) or
  // resolved by title (MP) so the service can read metadata.soundtrack + the case directory.
  private final ui.audio.SoundtrackService soundtrackService = new ui.audio.SoundtrackService();
  private JsonDTO.CaseFile activeCaseFile;
  // The language the active case is being played in (saved with the solve; Review re-opens in it).
  private String activeCaseLang;

  public ImageManager getImageManager() {
    return imageManager;
  }

  public ui.audio.SoundtrackService getSoundtrackService() {
    return soundtrackService;
  }

  /** Starts the active case's looped soundtrack (SP: captured case; MP: resolved by title). */
  private void startCaseSoundtrack() {
    JsonDTO.CaseFile caseFile = isSinglePlayer ? activeCaseFile : findActiveMultiplayerCase();
    soundtrackService.playForCase(caseFile);
  }

  /** The active case (SP: captured; MP: resolved by title), or null when none is available. */
  private JsonDTO.CaseFile activeResolvedCaseFile() {
    return isSinglePlayer ? activeCaseFile : findActiveMultiplayerCase();
  }

  /**
   * The author-defined assistant name for the active case in the language it is being played in, or
   * {@code null} when the case authored none — the caller then falls back to the localized default
   * ({@code game.watsonSpeaker}). Resolves the per-language {@code localizations.<lang>.helperName}
   * first, then the single {@code metadata.helperName} default.
   */
  public String getActiveHelperName() {
    return resolveCaseName(false);
  }

  /** The author-defined detective name for the active case (per-language, then metadata default). */
  public String getActiveDetectiveName() {
    return resolveCaseName(true);
  }

  /** Shared resolver for the two author-defined names: per-language override first, else metadata. */
  private String resolveCaseName(boolean detective) {
    JsonDTO.CaseFile cf = activeResolvedCaseFile();
    if (cf == null) {
      return null;
    }
    if (activeCaseLang != null && cf.getLocalizations() != null) {
      JsonDTO.CaseFile.LocalizedData loc = cf.getLocalizations().get(activeCaseLang);
      if (loc != null) {
        String v = detective ? loc.getDetectiveName() : loc.getHelperName();
        if (v != null && !v.isBlank()) {
          return v;
        }
      }
    }
    if (cf.getMetadata() != null) {
      String v = detective ? cf.getMetadata().getDetectiveName() : cf.getMetadata().getHelperName();
      if (v != null && !v.isBlank()) {
        return v;
      }
    }
    return null;
  }

  /**
   * Resolves the multiplayer client's current case to its {@link JsonDTO.CaseFile} by matching
   * {@code gameClient.getCaseTitle()} against the locally loadable cases — the same client-side
   * reload {@code getActiveCaseFileBlock()} uses for case metadata (no metadata crosses the wire).
   * Returns null when the case is not locally available, in which case the soundtrack stays silent.
   */
  private JsonDTO.CaseFile findActiveMultiplayerCase() {
    if (gameClient == null || gameClient.getCaseTitle() == null) {
      return null;
    }
    String title = gameClient.getCaseTitle().trim();
    if (title.isEmpty()) {
      return null;
    }
    try {
      for (JsonDTO.CaseFile cf : extractors.CaseLoader.loadCases("cases")) {
        if (cf.getUniversalTitle() != null
            && cf.getUniversalTitle().trim().equalsIgnoreCase(title)) {
          return cf;
        }
        if (cf.getLocalizations() != null) {
          for (JsonDTO.CaseFile.LocalizedData loc : cf.getLocalizations().values()) {
            if (loc != null
                && loc.getTitle() != null
                && loc.getTitle().trim().equalsIgnoreCase(title)) {
              return cf;
            }
          }
        }
      }
    } catch (Exception e) {
      logger.warn("Could not resolve multiplayer case for soundtrack: {}", e.getMessage());
    }
    return null;
  }

  /**
   * Points the {@link ImageManager} at the active case's directory so case-relative image paths
   * resolve at runtime exactly as the CaseValidator resolved them. Bundled cases have no source
   * path (their images ship on the classpath), so this clears the directory to null in that case.
   */
  private void applyCaseImageDirectory(JsonDTO.CaseFile caseFile) {
    java.nio.file.Path dir = null;
    if (caseFile != null
        && caseFile.getSourcePath() != null
        && !caseFile.getSourcePath().isBlank()) {
      try {
        dir = java.nio.file.Paths.get(caseFile.getSourcePath()).getParent();
      } catch (RuntimeException ignored) {
        dir = null;
      }
    }
    imageManager.setCaseDirectory(dir);
  }

  public Image getRoomImage(RoomDescriptionDTO room) {
    if (room == null) return imageManager.getRoomImage((String) null);
    return imageManager.getRoomImage(room.getImagePath());
  }

  public Image getSuspectImage(String name) {
    if (isSinglePlayer && singlePlayerGame != null) {
      singleplayer.GameContextSinglePlayer context = singlePlayerGame.getGameContext();
      if (context != null) {
        Core.Suspect suspect =
            context.getAllSuspects().stream()
                .filter(s -> s.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
        if (suspect != null) {
          return imageManager.getSuspectImage(suspect);
        }
      }
    }
    // Fallback or MP: use name convention
    String path = "images/" + name.toLowerCase().replace(" ", "_") + ".png";
    // Check if image exists, if not getSuspectImage will return default
    return imageManager.getSuspectImage(path);
  }

  /**
   * Whether the suspect has a resolvable authored image. When false, a fallback preset/placeholder is
   * drawn instead — and the room view must render it at a neutral 1.0 scale rather than the suspect's
   * authored sprite scale (which is calibrated for the missing cut-out art and makes the substitute
   * huge). See {@link ImageManager#suspectImageResolves}.
   */
  public boolean isSuspectImageAuthored(String name) {
    if (isSinglePlayer && singlePlayerGame != null) {
      singleplayer.GameContextSinglePlayer context = singlePlayerGame.getGameContext();
      if (context != null) {
        Core.Suspect suspect =
            context.getAllSuspects().stream()
                .filter(s -> s.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
        if (suspect != null) {
          return imageManager.suspectImageResolves(suspect.getImagePath());
        }
      }
    }
    return imageManager.suspectImageResolves(
        "images/" + name.toLowerCase().replace(" ", "_") + ".png");
  }

  /**
   * Resolves a suspect's human display name from its id (.scratch/casefile-tabs issue 02). In
   * single-player it looks the suspect up in the loaded game state ({@code getAllSuspects()}) and
   * returns its authored {@code getName()} (e.g. "Dr. Aris Thorne"); otherwise it falls back to the
   * pure {@link ui.util.DisplayNames#humanizeId(String)} humanizer so a tab never shows a raw id.
   */
  public String getSuspectDisplayName(String suspectId) {
    if (suspectId == null) {
      return "";
    }
    if (isSinglePlayer && singlePlayerGame != null) {
      singleplayer.GameContextSinglePlayer context = singlePlayerGame.getGameContext();
      if (context != null) {
        for (Core.Suspect s : context.getAllSuspects()) {
          if (suspectId.equalsIgnoreCase(s.getId()) || suspectId.equalsIgnoreCase(s.getName())) {
            return s.getName();
          }
        }
      }
    }
    return ui.util.DisplayNames.humanizeId(suspectId);
  }

  public Image getObjectImage(String name) {
    if (isSinglePlayer && singlePlayerGame != null) {
      singleplayer.GameContextSinglePlayer context = singlePlayerGame.getGameContext();
      if (context != null) {
        Core.Room currentRoom = context.getCurrentRoomForPlayer(null);
        if (currentRoom != null) {
          Core.GameObject obj = currentRoom.getObject(name);
          if (obj != null) {
            return imageManager.getObjectImage(obj);
          }
        }
      }
    }
    // Fallback or MP
    String path = "images/" + name.toLowerCase().replace(" ", "_") + ".png";
    return imageManager.getObjectImage(path);
  }

  /** Whether the object has a resolvable authored image (else a fallback preset is drawn at 1.0). */
  public boolean isObjectImageAuthored(String name) {
    if (isSinglePlayer && singlePlayerGame != null) {
      singleplayer.GameContextSinglePlayer context = singlePlayerGame.getGameContext();
      if (context != null) {
        Core.Room currentRoom = context.getCurrentRoomForPlayer(null);
        if (currentRoom != null) {
          Core.GameObject obj = currentRoom.getObject(name);
          if (obj != null) {
            return imageManager.objectImageResolves(obj.getImagePath());
          }
        }
      }
    }
    return imageManager.objectImageResolves(
        "images/" + name.toLowerCase().replace(" ", "_") + ".png");
  }

  public Image getWatsonImage() {
    if (isSinglePlayer && singlePlayerGame != null) {
      singleplayer.GameContextSinglePlayer context = singlePlayerGame.getGameContext();
      if (context != null) {
        String path = context.getWatsonImagePath();
        return imageManager.getWatsonImage(path);
      }
    }
    // MP or Fallback (no specific case metadata yet in MP DTOs)
    return imageManager.getWatsonImage(null);
  }

  /**
   * Whether the assistant has a resolvable authored portrait. When false, the room view renders the
   * fallback preset at a neutral 1.0 scale rather than the case's authored Watson scale (same
   * rationale as {@link #isSuspectImageAuthored}).
   */
  public boolean isWatsonImageAuthored() {
    if (isSinglePlayer && singlePlayerGame != null) {
      singleplayer.GameContextSinglePlayer context = singlePlayerGame.getGameContext();
      if (context != null) {
        return imageManager.watsonImageResolves(context.getWatsonImagePath());
      }
    }
    return false; // MP has no case metadata client-side — treat as fallback art.
  }

  private enum UIState {
    MENU,
    CASE_INVITATION,
    GAME_SINGLE,
    GAME_MULTI
  }

  @FXML private BorderPane mainBorderPane;
  @FXML private Button tasksButton;
  @FXML private Button journalButton;
  @FXML private Button pinboardButton;
  @FXML private Button caseFileButton;
  @FXML private Button chatButton;
  @FXML private Button finalExamButton;
  @FXML private Button helpButton;
  @FXML private Button settingsButton;
  @FXML private Button exitButton;
  @FXML private Label unreadChatLabel;
  @FXML private StackPane roomPane;
  @FXML private VBox rightInfoPanel;
  @FXML private VBox neighboringRoomsContainer;
  @FXML private ui.terminal.TerminalView terminalView;
  @FXML private VBox terminalPanel;
  @FXML private TextField terminalInputField;
  @FXML private javafx.scene.layout.HBox suggestionStrip;
  @FXML private Label statusLabel;
  @FXML private SplitPane mainHorizontalSplitPane;
  @FXML private SplitPane mainVerticalSplitPane;
  @FXML private Label insightTokensLabel;
  @FXML private Label deductionCountLabel;
  @FXML private HBox cooldownRow;
  @FXML private Label cooldownTimerLabel;
  private javafx.animation.Timeline cooldownTimeline;
  @FXML private Label brandLabel;
  @FXML private Label terminalTitleLabel;
  @FXML private Label roomInfoTitleLabel;
  @FXML private Label insightTokensCaptionLabel;
  @FXML private Label deductionsCaptionLabel;
  @FXML private Label neighboringRoomsCaptionLabel;
  @FXML private Label statusTitleLabel;

  private GameClient gameClient;
  private Thread gameClientThread;

  // Anti-abuse cooldown for the Final Exam (too many rapid submissions -> 5-min freeze with a live
  // countdown). Reset between cases/sessions via teardownInGameSurfaces(). See ui.exam package.
  private final ui.exam.FinalExamCooldown examCooldown = new ui.exam.FinalExamCooldown();
  private javafx.animation.Timeline examFreezeTicker;

  // The single local player profile (player-profile feature): loaded once, fed into the multiplayer
  // identity, edited by the Profile screen. Best-effort/offline-safe (PlayerProfileStore swallows
  // IO).
  private final ui.settings.PlayerProfileStore profileStore = new ui.settings.PlayerProfileStore();
  private ui.settings.PlayerProfile playerProfile = profileStore.load();

  private List<String> launchArgs;
  private HostServices hostServices;
  private UIState currentState = UIState.MENU;

  // Navigation shell (ADR-0002): the screen currently owning the content pane. Null while a
  // not-yet-extracted legacy state has taken the pane over directly.
  private MenuController menuController;
  private ui.screens.LobbyController lobbyController;
  private GameScreenController gameScreenController;
  private ExamScreenController examScreenController;
  private ScreenController currentScreen;

  // Full-window screen mount (.scratch/main-menu DEC-1): the in-game layout normally lives in the
  // BorderPane center with the toolbar on top. A full-window screen (the main menu) temporarily
  // replaces the center with its own view and hides the toolbar, so no terminal/sidebar shows.
  // Captured from the FXML in initialize(); null in headless tests that never call initialize().
  private javafx.scene.Node gameCenter;
  private javafx.scene.Node topChrome;

  // Terminal autocomplete (.scratch/terminal-autocomplete): the strip lives in the terminal
  // panel; the completion context comes from the mounted screen.
  private ui.terminal.TerminalAutocomplete terminalAutocomplete;

  private SinglePlayerMain singlePlayerGame;
  private Thread singlePlayerGameThread;
  private boolean isSinglePlayer;
  private boolean isHostPlayer;
  // True while a solved case is open as a non-destructive Review Session (gui-review-enter-case):
  // gameplay mutations + the Final Exam are off and the saved record is never re-written.
  private boolean reviewModeActive;
  private volatile boolean manualDisconnect = false;

  // Local Completed-Case Records for the case-selection wax-seal stamp (MENU_DESIGN #2) and
  // read-only
  // Review (docs/SAVE_AND_PROFILE.md). Best-effort, like the tutorial progress store; a read/write
  // failure never blocks play. Supersedes the old boolean CaseProgressStore (migrated on first
  // run).
  private final client.profile.CompletedCaseStore completedCaseStore =
      new client.profile.CompletedCaseStore();

  // Latest session Deduction count, fed by both the SP engine and the MP DeductionCountUpdateDTO
  // via
  // onDeductionCountUpdate. Captured into the Completed-Case Record on a solve.
  private int latestDeductionCount = 0;

  // Persisted UI language + theme (MENU_DESIGN #6); audio persists separately via
  // SoundtrackService.
  private final ui.settings.AppSettingsStore appSettingsStore = new ui.settings.AppSettingsStore();
  private ui.settings.AppSettings appSettings = ui.settings.AppSettings.defaults();

  // Tutorial System
  /**
   * Tutorial-only sentinel that a Final Exam answer submitted via the GUI feeds to the step machine
   * to advance the matching {@code AWAIT_COMMAND} step (.scratch/gui-exam-tutorial). The player
   * never types it — terminal answers advance through the normal routed-command path; this is the
   * GUI equivalent. Must equal the {@code expectedCommand} of the exam tutorial's GUI step in
   * {@code tutorials.json}.
   */
  public static final String TUTORIAL_EXAM_GUI_ANSWER = "answer submitted in the exam window";

  /**
   * Tutorial-only sentinel fed to the step machine when the player draws a link on the Pinboard
   * (.scratch/gui-pinboard-tutorial). Never typed; matches the link step's {@code expectedCommand}.
   */
  public static final String TUTORIAL_PINBOARD_LINKED = "linked on the pinboard";

  private client.tutorial.TutorialManager tutorialManager;
  private client.tutorial.TutorialOrchestrator tutorialOrchestrator;
  private StackPane tutorialOverlayPane;
  private Label tutorialOverlayLabel;
  private javafx.scene.layout.VBox tutorialOverlayCard;
  private javafx.scene.control.Button tutorialCloseButton;
  // "Continue" button shown ONLY on the final ("type continue") bubble, beside the typed path:
  // clicking it advances/ends the tutorial through the exact same route as typing "continue".
  private javafx.scene.control.Button tutorialContinueButton;
  private javafx.scene.layout.Region tutorialOverlayRule;
  private Polygon tutorialArrow;
  private boolean tutorialOverlayVisible = false;
  // The current step's arrow target, remembered so the card can be re-positioned when a result
  // popup
  // appears/closes (.scratch/gui-tutorial-bubble-position).
  private String tutorialArrowTarget = "NONE";
  // Whether a result popup is currently taking center stage. While a popup is up the guidance card
  // hugs the bottom edge so the two never overlap in the short room pane.
  private boolean resultPopupShowing = false;
  // Normalized (0–1) arrow anchor; margins are re-derived from the live pane size (issue 02).
  private double tutorialArrowNormX = 0.5;
  private double tutorialArrowNormY = 0.5;

  @FXML
  public void initialize() {
    // Load persisted app settings (MENU_DESIGN #6): apply the saved UI language before the first
    // render so the menu builds in it. The theme needs the scene, applied later via
    // applySavedTheme.
    appSettings = appSettingsStore.load();
    if (appSettings.language() != null && !appSettings.language().isBlank()) {
      L10n.setLanguage(appSettings.language());
    }
    // Canvas chrome reads ui.util.Palette (not CSS), so flip it to the saved theme before any
    // render; record the active theme so any sub-window built before the scene exists installs it.
    ui.util.Palette.applyTheme(appSettings.theme());
    ui.util.Theme.setActive(appSettings.theme());

    if (insightTokensLabel != null) {
      insightTokensLabel.setText(L10n.t("label.tokens", 0));
    }

    // Terminal auto-scroll (.scratch/ui-immersion issue 01, re-expressed in
    // ui.terminal.TerminalView
    // for .scratch/ingame-terminal-polish DEC-5): the view follows new output to the bottom while
    // the player is pinned there, and leaves their position alone once they scroll up to read
    // history, until they return to the bottom or submit a command (repinTerminalToBottom).

    terminalInputField.setOnAction(event -> handleTerminalInput());
    tasksButton.setOnAction(
        event -> {
          playSound("click.wav");
          routeToTutorialIfActive("tasks");
          openTasksWindow();
        });
    journalButton.setOnAction(
        event -> {
          playSound("pageflip.mp3");
          routeToTutorialIfActive("journal");
          openJournalWindow();
        });

    // Pinboard Button Initialization
    pinboardButton.setOnAction(
        event -> {
          playSound("click.wav");
          routeToTutorialIfActive("pinboard");
          openPinboardWindow();
        });

    caseFileButton.managedProperty().bind(caseFileButton.visibleProperty());
    caseFileButton.setOnAction(
        event -> {
          playSound("pageflip.mp3");
          routeToTutorialIfActive("case file");
          openCaseFileWindow();
        });

    chatButton.setOnAction(
        event -> {
          playSound("click.wav");
          openChatWindow();
        });

    finalExamButton.setOnAction(
        event -> {
          playSound("click.wav");
          // Anti-abuse: refuse to (re)start the exam while it is frozen, and (re)show the
          // countdown.
          if (refuseFinalExamIfFrozen("final exam")) {
            return;
          }
          sendCommand("final exam");
        });
    // Ensure final exam button is always enabled
    finalExamButton.setDisable(false);

    helpButton.setOnAction(
        event -> {
          playSound("click.wav");
          openHelpWindow();
        });

    settingsButton.setOnAction(
        event -> {
          playSound("click.wav");
          // The Final Exam keeps the toolbar; when it is showing, raise Settings over the exam
          // paper (its own container) rather than the detached game screen (.scratch/exam-settings).
          if (currentScreen == examScreenController) {
            examScreenController.showInGameSettings();
          } else {
            gameScreenController.showInGameSettings();
          }
        });

    exitButton.setOnAction(
        event -> {
          playSound("click.wav");
          sendCommand("exit");
        });

    updateStatus(L10n.t("status.ready"));
    unreadChatLabel.setVisible(false);

    // Terminal-never-disappears guard (.scratch/responsive-resizing issue 01): SplitPane divider
    // positions are proportional and not reliably re-validated against content min sizes through
    // programmatic/window resizes. Re-clamp both dividers on every resize/move.
    ui.util.DividerClamp.install(mainVerticalSplitPane, MIN_ROOM_PANE_HEIGHT, MIN_TERMINAL_HEIGHT);
    ui.util.DividerClamp.install(mainHorizontalSplitPane, MIN_CONTENT_WIDTH, MIN_SIDEBAR_WIDTH);

    // Remember the in-game layout (center) and toolbar (top) so a full-window screen can swap them
    // out and restore them (.scratch/main-menu DEC-1).
    gameCenter = mainBorderPane.getCenter();
    topChrome = mainBorderPane.getTop();

    menuController = new MenuController(this);
    lobbyController = new ui.screens.LobbyController(this);
    gameScreenController = new GameScreenController(this);
    examScreenController = new ExamScreenController(this);
    terminalAutocomplete =
        new ui.terminal.TerminalAutocomplete(
            terminalInputField,
            suggestionStrip,
            () ->
                currentScreen != null
                    ? currentScreen.completionContext()
                    : ui.terminal.CompletionContext.empty());
    setupButtonIcons();
    applyChromeTexts();
    applyLanguageStyleClass();
    applyContentScaleStyleClass();
    installResponsiveFontScaling();
    L10n.onChange(this::onUiLanguageChanged);

    // First frame must be the main menu, never the FXML in-game layout (.scratch/gui-startup-menu-
    // first). We are on the FX thread inside initialize() (during loader.load(), BEFORE the scene
    // is
    // built and shown), so mount the menu SYNCHRONOUSLY here. updateUIVisibility() defers the mount
    // to a Platform.runLater that fires AFTER primaryStage.show(), flashing the toolbar + room view
    // +
    // terminal for one frame. mountFullWindow sets the menu as the center and hides the toolbar, so
    // the scene is built with the menu already in place.
    currentState = UIState.MENU;
    menuController.showMainMenu();
    showScreenNow(menuController);
    if (deductionCountLabel != null) {
      deductionCountLabel.setText(L10n.t("label.deductionsEmpty"));
    }
    if (insightTokensLabel != null) {
      insightTokensLabel.setText(L10n.t("label.tokensEmpty"));
    }

    // Initial Focus
    Platform.runLater(() -> terminalInputField.requestFocus());

    // Global Escape chain (ADR-0002): close open sub-windows/overlays first; otherwise the
    // current screen steps back one level. Never exits the app silently.
    Platform.runLater(
        () -> {
          if (mainBorderPane.getScene() != null) {
            mainBorderPane
                .getScene()
                .setOnKeyPressed(
                    event -> {
                      if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                        // ADR-0002 Escape chain: suggestion strip first, then sub-windows,
                        // then the screen's own step-back.
                        if (terminalAutocomplete != null
                            && terminalAutocomplete.isShowingSuggestions()) {
                          terminalAutocomplete.dismiss();
                        } else if (!closeAllSubWindows()) {
                          if (currentScreen != null) {
                            currentScreen.onEscape();
                          } else if (currentState == UIState.CASE_INVITATION && !isHostPlayer) {
                            // Case Invitation is still shell-owned: a guest's Escape behaves
                            // like the Cancel button; the Host must explicitly start or exit
                            // (navigation-ux-smoothness issue 02).
                            sendCommand("cancel");
                          }
                        }
                      }
                    });
          }
        });

    // Initialize Tutorial Manager. Step copy resolves through the active-language bundle.
    this.tutorialManager = new client.tutorial.TutorialManager(this, ui.i18n.L10n::t);
  }

  /** Applies localized text to the static window chrome; re-run on every language switch. */
  private void applyChromeTexts() {
    tasksButton.setText(L10n.t("toolbar.tasks"));
    journalButton.setText(L10n.t("toolbar.journal"));
    pinboardButton.setText(L10n.t("toolbar.pinboard"));
    caseFileButton.setText(L10n.t("toolbar.caseFile"));
    chatButton.setText(L10n.t("toolbar.chat"));
    finalExamButton.setText(L10n.t("toolbar.finalExam"));
    helpButton.setText(L10n.t("toolbar.help"));
    settingsButton.setText(L10n.t("toolbar.settings"));
    exitButton.setText(L10n.t("toolbar.exit"));
    if (brandLabel != null) {
      brandLabel.setText(L10n.t("app.brand"));
    }
    if (terminalTitleLabel != null) {
      terminalTitleLabel.setText(L10n.t("terminal.title"));
    }
    if (roomInfoTitleLabel != null) {
      roomInfoTitleLabel.setText(L10n.t("sidebar.roomInformation"));
    }
    if (insightTokensCaptionLabel != null) {
      insightTokensCaptionLabel.setText(L10n.t("sidebar.insightTokens"));
    }
    if (deductionsCaptionLabel != null) {
      deductionsCaptionLabel.setText(L10n.t("sidebar.deductionsUsed"));
    }
    if (neighboringRoomsCaptionLabel != null) {
      neighboringRoomsCaptionLabel.setText(L10n.t("sidebar.neighboringRooms"));
    }
    if (statusTitleLabel != null) {
      statusTitleLabel.setText(L10n.t("sidebar.status"));
    }
    terminalInputField.setPromptText(L10n.t("terminal.prompt"));
    setupTooltips();
  }

  /**
   * Tags the scene root with the active {@code lang-<code>} style class so the per-language
   * typefaces apply (.scratch/ui-localization). The layout itself is always left-to-right in every
   * UI language — deliberate, no RTL mirroring; Arabic and Russian text render correctly inside the
   * LTR layout (JavaFX handles bidi text within labels).
   */
  private void applyLanguageStyleClass() {
    ui.i18n.LocaleStyling.apply(mainBorderPane);
  }

  /**
   * Tags the scene root with BOTH text-size bucket classes (.scratch/gui-typography-readability):
   * {@code term-scale-NNN} (the terminal transcript/input/prompt + chips, absolute px) and {@code
   * read-scale-NNN} (re-bases the root font size, so EVERYTHING sized in em — the whole interface
   * except the terminal — follows). Also records the reading multiplier as the active one, so every
   * window/dialog built afterwards tags itself via {@code Theme.install}.
   */
  private void applyContentScaleStyleClass() {
    ui.util.ContentScaleStyling.setActiveReadingScale(appSettings.readingTextScale());
    ui.util.ContentScaleStyling.apply(
        mainBorderPane, ui.util.ContentScale.TERMINAL_PREFIX, appSettings.terminalTextScale());
    ui.util.ContentScaleStyling.apply(
        mainBorderPane, ui.util.ContentScale.READING_PREFIX, appSettings.readingTextScale());
    applyResponsiveRootFont();
  }

  // 100% reading base = the .root font size (px); every reading-family size is an em ratio of it.
  private static final double BASE_ROOT_FONT_PX = 12.0;
  // Windowed never enlarges past fullscreen (1.0) and never shrinks below this floor (legibility).
  private static final double MIN_WINDOW_FONT_RATIO = 0.62;

  /**
   * Installs a listener so the root font-size re-computes whenever the window is resized or toggled
   * between fullscreen and windowed. Set up once; the scene may not exist yet at initialize() time,
   * so it hooks the scene as soon as it attaches.
   */
  private void installResponsiveFontScaling() {
    javafx.beans.value.ChangeListener<Number> onSize = (obs, a, b) -> applyResponsiveRootFont();
    mainBorderPane
        .sceneProperty()
        .addListener(
            (obs, oldScene, newScene) -> {
              if (newScene != null) {
                newScene.heightProperty().addListener(onSize);
                newScene.widthProperty().addListener(onSize);
                applyResponsiveRootFont();
              }
            });
    if (mainBorderPane.getScene() != null) {
      mainBorderPane.getScene().heightProperty().addListener(onSize);
      mainBorderPane.getScene().widthProperty().addListener(onSize);
    }
    applyResponsiveRootFont();
  }

  /**
   * Sets the root font-size (which every em-sized element follows) to the chosen Reading text size
   * <b>ratioed to the current window height against the fullscreen height</b>. So a size chosen in
   * fullscreen scales down proportionally in a smaller window instead of staying oversized — the
   * whole interface adapts to windowed mode. Applied as an inline style on the scene root, so it
   * overrides the static CSS base. The terminal (absolute-px, its own slider) is unaffected.
   */
  private void applyResponsiveRootFont() {
    if (mainBorderPane == null) {
      return;
    }
    double reading = appSettings.readingTextScale();
    double screenH = javafx.stage.Screen.getPrimary().getVisualBounds().getHeight();
    javafx.scene.Scene scene = mainBorderPane.getScene();
    double windowH = (scene != null && scene.getHeight() > 0) ? scene.getHeight() : screenH;
    double ratio = screenH > 0 ? windowH / screenH : 1.0;
    ratio = Math.max(MIN_WINDOW_FONT_RATIO, Math.min(1.0, ratio));
    double px = BASE_ROOT_FONT_PX * reading * ratio;
    mainBorderPane.setStyle(
        "-fx-font-size: " + String.format(java.util.Locale.US, "%.2f", px) + "px;");
  }

  private void onUiLanguageChanged() {
    Platform.runLater(
        () -> {
          applyChromeTexts();
          applyLanguageStyleClass();
          gameScreenController.disposeCachedSubWindows();
          if (currentScreen != null) {
            currentScreen.onLanguageChanged();
          }
        });
  }

  private void setupTooltips() {
    tasksButton.setTooltip(new Tooltip(L10n.t("tooltip.tasks")));
    journalButton.setTooltip(new Tooltip(L10n.t("tooltip.journal")));
    pinboardButton.setTooltip(new Tooltip(L10n.t("tooltip.pinboard")));
    caseFileButton.setTooltip(new Tooltip(L10n.t("tooltip.caseFile")));
    chatButton.setTooltip(new Tooltip(L10n.t("tooltip.chat")));
    finalExamButton.setTooltip(new Tooltip(L10n.t("tooltip.finalExam")));
    helpButton.setTooltip(new Tooltip(L10n.t("tooltip.help")));
    settingsButton.setTooltip(new Tooltip(L10n.t("tooltip.settings")));
    exitButton.setTooltip(new Tooltip(L10n.t("tooltip.exit")));
  }

  /**
   * @return true if any sub-window or overlay was actually open (and is now closed).
   */
  private boolean closeAllSubWindows() {
    return gameScreenController != null && gameScreenController.closeAllSubWindows();
  }

  /**
   * Tears down EVERY transient in-game surface so nothing stale carries into the next tutorial or
   * session (.scratch/gui-tutorial-exit-cleanup): the statement/dialogue popups + every sub-window
   * + the pause/Settings overlays (via {@code GameScreenController}), plus the shell-owned tutorial
   * overlay. Invoked on tutorial start/exit and on leaving/starting a game session.
   */
  private void teardownInGameSurfaces() {
    if (gameScreenController != null) {
      gameScreenController.closeAllTransientSurfaces();
    }
    if (examScreenController != null) {
      // Also drops the "Case solved" victory popup + exam state so neither carries into the next
      // tutorial/session (.scratch/gui-tutorial-exit-cleanup).
      examScreenController.reset();
    }
    // A new case/session clears the Final Exam rapid-submission freeze.
    resetFinalExamCooldown();
    hideTutorialOverlay();
  }

  private void showCaseInvitation(String invitationText, boolean isHost) {
    VBox invitationBox = new VBox(20);
    invitationBox.setAlignment(Pos.CENTER);
    invitationBox.getStyleClass().add("overlay-container");

    Label titleLabel = new Label(L10n.t("invitation.title"));
    titleLabel.getStyleClass().add("overlay-title");

    TextArea invitationTextArea = new TextArea(invitationText);
    invitationTextArea.setEditable(false);
    invitationTextArea.setWrapText(true);
    invitationTextArea.getStyleClass().add("case-invitation-text");
    // Size to the viewport, not fixed 600×400 pixels (.scratch/responsive-resizing issue 02).
    ui.util.ViewportSizing.bindMaxToViewport(invitationTextArea, roomPane, 0.7, 320, 0.6, 240);
    VBox.setVgrow(invitationTextArea, javafx.scene.layout.Priority.ALWAYS);

    Button startButton = new Button(L10n.t("invitation.start"));
    startButton.setOnAction(event -> handleStartCase());

    invitationBox.getChildren().addAll(titleLabel, invitationTextArea, startButton);

    if (!isHost) {
      Button cancelButton = new Button(L10n.t("invitation.cancel"));
      cancelButton.setOnAction(event -> sendCommand("cancel"));
      invitationBox.getChildren().add(cancelButton);
    }

    Platform.runLater(
        () -> {
          legacyViewTakeover();
          roomPane.getChildren().clear();
          roomPane.getChildren().add(invitationBox);
          currentState = UIState.CASE_INVITATION;
          updateUIVisibility();
        });
  }

  /**
   * A not-yet-extracted legacy state is about to own the content pane directly (FX thread only).
   * Detaches the current screen so it stops receiving terminal input and Escape.
   */
  private void legacyViewTakeover() {
    if (currentScreen != null) {
      ScreenController previous = currentScreen;
      currentScreen = null;
      previous.onHide();
    }
  }

  /** Mounts a screen in the content pane from any thread. See ADR-0002. */
  public void showScreen(ScreenController screen) {
    if (Platform.isFxApplicationThread()) {
      showScreenNow(screen);
    } else {
      Platform.runLater(() -> showScreenNow(screen));
    }
  }

  /** Mounts a screen in the content pane (FX thread only). See ADR-0002. */
  private void showScreenNow(ScreenController screen) {
    boolean changed = currentScreen != screen;
    if (changed) {
      ScreenController previous = currentScreen;
      currentScreen = screen;
      if (previous != null) {
        previous.onHide();
      }
      // Lock the neighboring-Room move buttons while the Final Exam paper is showing; unlock on
      // return to the in-game screen (.scratch/exam-command-lockout). The engine authority also
      // refuses the move, but a disabled button is the honest affordance.
      if (gameScreenController != null) {
        if (screen == examScreenController) {
          gameScreenController.setNeighborMovesDisabled(true);
        } else if (screen == gameScreenController) {
          gameScreenController.setNeighborMovesDisabled(false);
        }
      }
    }
    applyChromeForScreen(screen);
    if (changed) {
      // onShow builds the screen's content and may set a sub-state (and call relayoutScreen).
      screen.onShow();
    }
    mountCurrentScreen();
  }

  /**
   * Re-mounts the current screen in the correct host (.scratch/main-menu DEC-2). A screen with
   * sub-states (the menu) calls this after a sub-state render so the shell moves the view between
   * the full-window center and the in-game content pane when {@link
   * ScreenController#usesFullWindow} flips. Idempotent: a no-op when the view is already in the
   * right place.
   */
  public void relayoutScreen() {
    if (Platform.isFxApplicationThread()) {
      mountCurrentScreen();
    } else {
      Platform.runLater(this::mountCurrentScreen);
    }
  }

  private void mountCurrentScreen() {
    if (currentScreen == null) {
      return;
    }
    Node view = currentScreen.getView();
    if (currentScreen.usesFullWindow()) {
      mountFullWindow(view);
    } else {
      mountInContentPane(view);
    }
  }

  /** Full-window mount: the screen's view becomes the BorderPane center and the toolbar hides. */
  private void mountFullWindow(Node view) {
    setTopChromeVisible(false);
    roomPane.getChildren().remove(view); // in case it was last mounted in the content pane
    if (mainBorderPane.getCenter() != view) {
      mainBorderPane.setCenter(view);
      // Page-turn entrance (DESIGN.md §6, MENU_DESIGN): fade in with a small directional slide so
      // the new full-window screen turns into view like a page.
      ui.util.Motion.pageTurnIn(view, 1);
    }
    // Wire the engraved-plate hover/press/focus lift onto whatever this screen (or its current
    // sub-state) just built — after CSS resolves so the style-class lookups match. Idempotent.
    Platform.runLater(() -> ui.util.Motion.animatePlates(view));
  }

  /** Restores the in-game layout (center + toolbar) and mounts the view into the room pane. */
  private void mountInContentPane(Node view) {
    setTopChromeVisible(true);
    if (gameCenter != null && mainBorderPane.getCenter() != gameCenter) {
      mainBorderPane.setCenter(gameCenter);
    }
    Node current = roomPane.getChildren().isEmpty() ? null : roomPane.getChildren().get(0);
    if (current == view) {
      return; // already mounted
    }
    // Page-settle transition (DESIGN.md §6, .scratch/ui-immersion issue 04): cross-fade plus a
    // small
    // translate so the incoming screen settles into place like a turning page. The onShown hook
    // re-asserts the tutorial overlay after the swap mounts.
    ui.util.Motion.crossFadeReplace(roomPane, current, view, this::reassertTutorialOverlay);
  }

  private void setTopChromeVisible(boolean visible) {
    if (topChrome != null) {
      topChrome.setVisible(visible);
      topChrome.setManaged(visible);
    }
  }

  /**
   * Re-attaches the tutorial overlay after a screen mount. {@link ui.util.Motion#crossFadeReplace}
   * defers its {@code setAll(incoming)} to the end of the fade-out; an overlay added by {@link
   * #showTutorialOverlay} while that transition is mid-flight would otherwise be wiped when the
   * incoming screen finally mounts. That race is why the first tutorial step's instruction never
   * appeared until the player typed a command (which re-added the overlay with no pending swap).
   * Runs after the incoming screen is mounted; no-ops unless an overlay is currently meant to show.
   */
  private void reassertTutorialOverlay() {
    if (tutorialOverlayVisible && tutorialOverlayPane != null) {
      addTutorialOverlayToRoomPane();
    }
  }

  private void applyChromeForScreen(ScreenController screen) {
    boolean game = screen.showsGameChrome();
    tasksButton.setVisible(game);
    journalButton.setVisible(game);
    pinboardButton.setVisible(game);
    caseFileButton.setVisible(game);
    chatButton.setVisible(game && !isSinglePlayer);
    // The Final Exam is hidden while reviewing a solved case — review is a walkthrough, not a
    // retry.
    finalExamButton.setVisible(game && !reviewModeActive);
    helpButton.setVisible(game);
    settingsButton.setVisible(game);
    exitButton.setVisible(game);
    rightInfoPanel.setVisible(game);
  }

  private void handleStartCase() {
    playSound("click.wav");
    if (isSinglePlayer) {
      // Run in a background thread to avoid freezing the UI
      new Thread(
              () -> {
                singlePlayerGame.processCommand("start case");
                Platform.runLater(
                    () -> {
                      showGameScreen();
                      startCaseSoundtrack();
                    });
              })
          .start();
    } else {
      sendCommand("start case");
      // The UI will be updated by the server's response
    }
  }

  public void setLaunchArgs(List<String> args) {
    this.launchArgs = args;
  }

  public void setHostServices(HostServices hostServices) {
    this.hostServices = hostServices;
  }

  private void setupButtonIcons() {
    setButtonIcon(tasksButton, "/icons/tasks.png");
    setButtonIcon(journalButton, "/icons/journal.png");
    setButtonIcon(
        pinboardButton, "/icons/journal.png"); // Reuse journal icon or use pinboard specific
    setButtonIcon(chatButton, "/icons/chat.png");
  }

  private void setButtonIcon(Button button, String iconPath) {
    try {
      Image icon = new Image(getClass().getResourceAsStream(iconPath));
      ImageView iconView = new ImageView(icon);
      iconView.setFitHeight(20);
      iconView.setFitWidth(20);
      button.setGraphic(iconView);
    } catch (Exception e) {
      logger.warn("Could not load icon: {}", iconPath);
    }
  }

  public void playSound(String soundFile) {
    try {
      String soundPath = getClass().getResource("/sounds/" + soundFile).toExternalForm();
      Media sound = new Media(soundPath);
      MediaPlayer mediaPlayer = new MediaPlayer(sound);
      mediaPlayer.play();
    } catch (Exception e) {
      logger.warn("Could not play sound: {}", soundFile);
    }
  }

  /** {@link client.tutorial.TutorialHost} entry — also the Tutorials button target. */
  public void showTutorialsMenu() {
    Platform.runLater(
        () -> {
          // Leaving a tutorial: tear down every transient surface (statement popups, sub-windows,
          // pause/Settings + the tutorial overlay) so re-entering a tutorial is clean
          // (.scratch/gui-tutorial-exit-cleanup).
          teardownInGameSurfaces();
          menuController.showTutorials();
          showScreenNow(menuController);
        });
  }

  /**
   * Resets flags a tutorial's {@code showGameView()} may have left behind so post-tutorial input is
   * not routed at a stale game state. Called by the menu before rendering tutorials.
   */
  public void prepareTutorialsMenu() {
    currentState = UIState.MENU;
    isSinglePlayer = false;
    isHostPlayer = false;
    // Drop the tutorial-mode SP game; next tutorial will boot a fresh one. The game was also
    // registered as the active singlePlayerGame (so the Journal/Pinboard could sync), so clear that
    // too — a real case start reassigns it, but don't leave a stale practice game behind.
    tutorialOrchestrator = null;
    singlePlayerGame = null;
  }

  /** Boots a fresh tutorial-mode SP game on the practice case and starts the given script. */
  public void startTutorial(String tutorialId) {
    if (tutorialManager == null) {
      return;
    }
    // Start from a clean slate: tear down any popup/sub-window/overlay left over from a previous
    // tutorial or session so nothing stale is visible (.scratch/gui-tutorial-exit-cleanup).
    teardownInGameSurfaces();
    // No result popup is up at the start of a fresh tutorial; clear any stale flag so the first
    // step's guidance image is not wrongly suppressed.
    resultPopupShowing = false;
    String startRoom = tutorialManager.startRoomFor(tutorialId);
    // Silent pre-seed (run before the GUI sink is wired) so a tutorial can begin with evidence
    // already examined/questioned — no long visible setup (.scratch/gui-pinboard-tutorial).
    tutorialOrchestrator =
        client.tutorial.TutorialOrchestrator.bootstrap(
            tutorialManager, startRoom, tutorialManager.seedCommandsFor(tutorialId));

    // Register the tutorial's engine as the active single-player game so EVERY pull-based reader —
    // the Journal window, the Pinboard evidence sync, the sprite-image/tasks getters — resolves the
    // live tutorial state, exactly as in a real case. Without this the terminal/room view (pushed
    // by
    // the sink) worked but those windows read getSinglePlayerGame() == null and stayed empty
    // (.scratch/gui-journal-pinboard-sync). Cleared in prepareTutorialsMenu().
    singlePlayerGame = tutorialOrchestrator.getGame();

    // Wire the same typed-event seams real play uses, so the engine drives the terminal and room
    // view for real (no faked scene). Then push the starting room to the GUI with a 'look'.
    singleplayer.GameContextSinglePlayer ctx = tutorialOrchestrator.getGame().getGameContext();
    if (ctx != null) {
      ctx.setStateListener(this);
      ctx.setOutputSink(new GuiGameOutputSink(this));
      ctx.setPinboardUpdateHandler(this::applyPinboardUpdate);
      ctx.setGameTexts(new ui.i18n.L10nGameTexts());
    }
    tutorialOrchestrator.getGame().processCommand("look");

    tutorialManager.startTutorial(tutorialId);
  }

  /** Whether the player has completed a given tutorial (drives the menu's done badge). */
  public boolean isTutorialCompleted(String tutorialId) {
    return tutorialManager != null && tutorialManager.isCompleted(tutorialId);
  }

  /**
   * Whether the player has solved this case before — drives the wax-seal "Solved" stamp on its
   * casebook cover (MENU_DESIGN #2). Keyed by the stable {@code universal_title}.
   */
  public boolean isCaseSolved(String universalTitle) {
    return completedCaseStore.isSolved(universalTitle);
  }

  /**
   * The casebook seal's rank label: the monotonic <em>Best Result</em>
   * (.scratch/completed-case-records DEC-9). Rank-tier names are localized, so the stored
   * language-independent strength ({@code bestDeductionsUsed}) is re-evaluated against the case's
   * tiers in the current UI language via {@link Core.util.RankEvaluator} — the seal always shows
   * the best rank, correctly localized. Falls back to the stored {@code bestRankName} when the case
   * can't be located in {@code cases} or the strength is unknown (a migrated stub); empty when the
   * case is unsolved. Shared by the single-player ({@link MenuController}) and multiplayer-host
   * ({@link ui.screens.LobbyController}) case-selection seals.
   */
  public java.util.Optional<String> bestRankLabel(
      String universalTitle, java.util.List<JsonDTO.CaseFile> cases) {
    java.util.Optional<common.dto.save.CompletedCaseRecord> found =
        completedCaseStore.find(universalTitle);
    if (found.isEmpty()) {
      return java.util.Optional.empty();
    }
    common.dto.save.CompletedCaseRecord record = found.get();
    Integer bestDeductions = record.getBestDeductionsUsed();
    if (bestDeductions != null && cases != null) {
      for (JsonDTO.CaseFile caseFile : cases) {
        if (caseFile != null && universalTitle.equals(caseFile.getUniversalTitle())) {
          try {
            JsonDTO.LocalizedCaseFile localized =
                new JsonDTO.LocalizedCaseFile(caseFile, ui.i18n.L10n.language());
            Core.Rank rank = Core.util.RankEvaluator.evaluate(bestDeductions, localized);
            if (rank != null && rank.getRankName() != null) {
              return java.util.Optional.of(rank.getRankName());
            }
          } catch (RuntimeException e) {
            // Localized re-derivation failed (malformed tiers) — fall back to the stored label.
          }
          break;
        }
      }
    }
    return java.util.Optional.ofNullable(record.getBestRankName());
  }

  /** The Completed-Case Record for a case, if solved — used to open read-only Review. */
  public java.util.Optional<common.dto.save.CompletedCaseRecord> completedRecordFor(
      String universalTitle) {
    return completedCaseStore.find(universalTitle);
  }

  /**
   * "Review investigation" (docs/SAVE_AND_PROFILE.md): enters the solved case as a non-destructive
   * {@link #launchReviewSession Review Session} when the record carries detail; a migrated
   * (detail-less) record has no journal/pinboard to walk, so it falls back to the read-only summary
   * window. No-op if the case has no record.
   */
  public void openReview(JsonDTO.CaseFile caseFile, String langCode) {
    if (caseFile == null) {
      return;
    }
    completedCaseStore
        .find(caseFile.getUniversalTitle())
        .ifPresent(
            record -> {
              if (record.hasDetail()) {
                // Re-open the case in the language it was SOLVED in, so the saved Journal/Pinboard
                // text matches the world around it. Older records carry no language — fall back to
                // the language the player picked, exactly as before.
                String solved = record.getLanguageCode();
                boolean caseStillOffersIt =
                    solved != null
                        && !solved.isBlank()
                        && caseFile.getLocalizations() != null
                        && caseFile.getLocalizations().containsKey(solved);
                launchReviewSession(caseFile, caseStillOffersIt ? solved : langCode, record);
              } else {
                new ui.review.CaseReviewWindow(ui.i18n.CaseTitles.displayTitle(caseFile), record)
                    .show();
              }
            });
  }

  /**
   * Loads a solved case as a navigable, non-destructive Review Session (gui-review-enter-case): a
   * normal in-process single-player session whose engine is flipped to review mode <em>after</em>
   * {@code start case} builds the world, with the Journal seeded from the saved record and the
   * Pinboard handed to the game screen. Gameplay mutations + the Final Exam are gated off by the
   * engine, so nothing the player does re-writes the record.
   */
  public void launchReviewSession(
      JsonDTO.CaseFile caseFile, String langCode, common.dto.save.CompletedCaseRecord record) {
    startSinglePlayerSession();
    // prepareSinglePlayerCase resets reviewModeActive to false (the normal-start default), so
    // re-arm
    // Review mode AFTER it — this is the one path that runs the session read-only.
    prepareSinglePlayerCase(caseFile, langCode);
    reviewModeActive = true;
    playSound("pageflip.mp3");
    new Thread(
            () -> {
              singlePlayerGame.processCommand("start case");
              singleplayer.GameContextSinglePlayer ctx = singlePlayerGame.getGameContext();
              if (ctx != null) {
                ctx.setReviewMode(true); // now gameplay mutations + the exam are refused
                ctx.seedReviewJournal(record.getJournal());
              }
              Platform.runLater(
                  () -> {
                    gameScreenController.enterReviewMode(record);
                    showGameScreen();
                    startCaseSoundtrack();
                  });
            })
        .start();
  }

  /**
   * Records the currently active case as solved, assembling a {@link
   * common.dto.save.CompletedCaseRecord} from the Final Exam result, the latest Deduction count,
   * and the final Journal and Pinboard, then persisting it under "keep the best" (best-effort).
   *
   * <p>Multiplayer: <b>each player records their own solve</b> (docs/SAVE_AND_PROFILE.md). This
   * runs on every client when its {@code ExamResultDTO} arrives, so host AND guest each write a
   * record to their OWN local store, keyed to their OWN profile, built from that client's cached
   * Journal ({@link #currentJournalEntries}) and synced Pinboard ({@link
   * GameScreenController#getCurrentPinboardState}) — the guest never depends on the host. So the
   * "Solved" seal + Review/Play-again dialog appear independently on each player's case selection.
   * Single-player is unchanged (the one local player records).
   */
  public void recordActiveCaseSolved(common.dto.ExamResultDTO result) {
    // Review Sessions are read-only by construction (the engine blocks the exam), but guard the
    // write hook too so a Review can never re-write or re-evaluate the saved record. A tutorial's
    // practice-case exam must NEVER be recorded either (.scratch/gui-exam-tutorial) — and this also
    // guards a stale activeCaseFile left from a prior real case.
    if (activeCaseFile == null || reviewModeActive || isTutorialActive()) {
      return;
    }
    common.dto.save.CompletedCaseRecord record =
        common.dto.save.CompletedCaseRecord.fromExamResult(
            activeCaseFile.getUniversalTitle(),
            result,
            latestDeductionCount,
            currentJournalEntries(),
            gameScreenController != null ? gameScreenController.getCurrentPinboardState() : null,
            System.currentTimeMillis(),
            activeCaseLang); // remember the language it was solved in, for Review
    completedCaseStore.save(record);
  }

  /** The session's Journal entries (SP: engine getters; MP: client-cached entries). */
  private java.util.List<common.dto.JournalEntryDTO> currentJournalEntries() {
    java.util.List<common.dto.JournalEntryDTO> entries = null;
    if (isSinglePlayer) {
      if (singlePlayerGame != null && singlePlayerGame.getGameContext() != null) {
        entries = singlePlayerGame.getGameContext().getJournalEntries(null);
      }
    } else if (gameClient != null) {
      entries = gameClient.getJournalEntries();
    }
    return entries != null ? new java.util.ArrayList<>(entries) : java.util.List.of();
  }

  /**
   * Whether a tutorial is currently running. The result popup uses this to dodge the tutorial
   * guidance card (pinned BOTTOM_CENTER) so the two never overlap.
   */
  public boolean isTutorialActive() {
    return tutorialManager != null && tutorialManager.isActive();
  }

  /**
   * Advances the exam tutorial's GUI-answer step when a Final Exam answer is submitted through the
   * exam window (.scratch/gui-exam-tutorial). The terminal path advances via the normal routed
   * command; this feeds the GUI equivalent {@link #TUTORIAL_EXAM_GUI_ANSWER} sentinel straight to
   * the step machine (no engine round-trip — the answer already reached the engine via the GUI
   * submit).
   */
  public void notifyTutorialExamAnsweredViaGui() {
    if (tutorialManager != null && tutorialManager.isActive()) {
      tutorialManager.processInput(TUTORIAL_EXAM_GUI_ANSWER);
    }
  }

  /**
   * Which input method the current Final-Exam tutorial step requires
   * (.scratch/gui-exam-tutorial-input-enforce): a terminal-answer step (expected command like
   * {@code 1,1}) is TERMINAL_ONLY; the GUI-answer step (the {@link #TUTORIAL_EXAM_GUI_ANSWER}
   * sentinel) is GUI_ONLY; anything else (or no tutorial) is ANY. So each exam question teaches
   * exactly one method and the wrong method can't silently answer it and stall the step.
   */
  public enum ExamInputMode {
    ANY,
    TERMINAL_ONLY,
    GUI_ONLY
  }

  // A terminal exam answer is the choice numbers for the blanks, e.g. "2" or "1,1".
  private static final java.util.regex.Pattern EXAM_ANSWER_PATTERN =
      java.util.regex.Pattern.compile("\\d+(\\s*,\\s*\\d+)*");

  /** Classifies a tutorial step's expectedCommand into the exam input method it teaches. Pure. */
  static ExamInputMode classifyExamInputMode(String expectedCommand) {
    if (expectedCommand == null) {
      return ExamInputMode.ANY;
    }
    if (expectedCommand.equals(TUTORIAL_EXAM_GUI_ANSWER)) {
      return ExamInputMode.GUI_ONLY;
    }
    if (EXAM_ANSWER_PATTERN.matcher(expectedCommand.trim()).matches()) {
      return ExamInputMode.TERMINAL_ONLY;
    }
    return ExamInputMode.ANY;
  }

  /** The exam input method enforced right now (ANY unless a tutorial step demands one). */
  public ExamInputMode tutorialExamInputMode() {
    if (tutorialManager == null || !tutorialManager.isActive()) {
      return ExamInputMode.ANY;
    }
    return classifyExamInputMode(tutorialManager.currentExpectedCommand());
  }

  /** True if the GUI dropdowns/submit may answer the current exam question. */
  public boolean isExamGuiAnswerAllowed() {
    return tutorialExamInputMode() != ExamInputMode.TERMINAL_ONLY;
  }

  /** In-world nudge shown when the player tries the GUI on a terminal-taught exam question. */
  public void promptExamUseTerminal() {
    appendTerminalText("\n" + L10n.t("exam.tutorial.useTerminal") + "\n");
  }

  /**
   * Advances the pinboard tutorial's link step when the player draws a thread on the board
   * (.scratch/gui-pinboard-tutorial). Drawing a link emits no command, so the board notifies here
   * and we feed the {@link #TUTORIAL_PINBOARD_LINKED} sentinel to the step machine.
   */
  public void notifyTutorialPinboardLinked() {
    if (tutorialManager != null && tutorialManager.isActive()) {
      tutorialManager.processInput(TUTORIAL_PINBOARD_LINKED);
    }
  }

  /**
   * Told by the game screen when a result popup appears/closes. The room pane is short (top ~70% of
   * a vertical split), so while a popup is up the tutorial guidance card hugs the bottom edge —
   * that leaves the popup a clear band up top with no overlap.
   */
  public void setResultPopupShowing(boolean showing) {
    resultPopupShowing = showing;
    if (tutorialOverlayCard == null) {
      return;
    }
    // Re-pin the guidance card for the current step: dodge the top result popup while it's up, then
    // restore the target-aware placement when it closes (.scratch/gui-tutorial-bubble-position).
    Platform.runLater(() -> positionTutorialCard(tutorialArrowTarget));
  }

  public void showAddCaseWindow() {
    new ui.windows.AddCaseWindow(this).show();
  }

  /**
   * Refreshes the single-player case-selection gallery after a case is imported through the
   * Add-a-case window, so the newly copied case shows up without leaving the screen.
   */
  public void refreshCaseSelectionAfterImport() {
    if (menuController != null) {
      Platform.runLater(menuController::refreshCaseSelectionIfChoosing);
    }
  }

  // The Case Maker is a SINGLE reusable window owned by the shell — both "Create Case" and "Open
  // case" operate on this one Stage, so there is never a second Case Maker window.
  private ui.casemaker.CaseMakerWindow caseMakerWindow;

  /**
   * Opens the Case Maker — the visual case-authoring tool (Phase 4b; .scratch/case-maker). If one
   * is already open, brings it to the front and focuses it instead of spawning another.
   */
  public void showCaseMaker() {
    if (caseMakerWindow != null && caseMakerWindow.isShowing()) {
      caseMakerWindow.toFront();
      caseMakerWindow.requestFocus();
      return;
    }
    showSingleCaseMaker(null);
  }

  /**
   * Loads {@code draft} into the ONE Case Maker window. The views are bound to a final draft, so
   * this disposes the currently-open window and shows a fresh one on the loaded draft — leaving
   * exactly one Case Maker window. Called from the Case Maker's "Open case" flow (after its
   * unsaved-changes guard).
   */
  public void replaceCaseMaker(ui.casemaker.model.CaseDraft draft) {
    showSingleCaseMaker(draft);
  }

  private void showSingleCaseMaker(ui.casemaker.model.CaseDraft draft) {
    if (caseMakerWindow != null) {
      ui.casemaker.CaseMakerWindow previous = caseMakerWindow;
      caseMakerWindow = null; // so its onHidden handler doesn't clear the new reference below
      previous.close();
    }
    ui.casemaker.CaseMakerWindow window =
        draft == null
            ? new ui.casemaker.CaseMakerWindow(this)
            : new ui.casemaker.CaseMakerWindow(this, draft);
    // Drop the reference when the author closes it, so the next "Create Case" opens a fresh one.
    window.setOnHidden(
        e -> {
          if (caseMakerWindow == window) {
            caseMakerWindow = null;
          }
        });
    caseMakerWindow = window;
    window.show();
  }

  public void clearTerminal() {
    terminalView.clear();
  }

  /**
   * Re-pin the terminal to the bottom and scroll there — the single user-action reset, called from
   * every user-initiated command seam (typed input, {@code sendCommand} buttons, and the shared
   * in-game {@code GameScreenController.dispatch}) so SP and MP behave identically
   * (.scratch/terminal-scroll-mp DEC-2).
   */
  public void repinTerminalToBottom() {
    terminalView.repinToBottom();
  }

  private void updateUIVisibility() {
    Platform.runLater(
        () -> {
          switch (currentState) {
            case CASE_INVITATION:
              tasksButton.setVisible(false);
              journalButton.setVisible(false);
              pinboardButton.setVisible(false);
              caseFileButton.setVisible(false);
              chatButton.setVisible(false);
              finalExamButton.setVisible(false);
              helpButton.setVisible(false);
              settingsButton.setVisible(false);
              exitButton.setVisible(isHostPlayer); // Only host can exit at this stage
              rightInfoPanel.setVisible(false);
              return;
            case MENU:
              menuController.showMainMenu();
              showScreenNow(menuController);
              // Reset UI labels
              if (deductionCountLabel != null)
                deductionCountLabel.setText(L10n.t("label.deductionsEmpty"));
              if (insightTokensLabel != null)
                insightTokensLabel.setText(L10n.t("label.tokensEmpty"));
              return;
            case GAME_SINGLE:
            case GAME_MULTI:
              showScreenNow(gameScreenController);
              return;
            default:
              return;
          }
        });
  }

  /** Mounts the in-game screen and records the matching session state (any thread). */
  public void showGameScreen() {
    currentState = isSinglePlayer ? UIState.GAME_SINGLE : UIState.GAME_MULTI;
    updateUIVisibility();
  }

  @Override
  public void onInsightTokensUpdate(int count) {
    Platform.runLater(
        () -> {
          if (insightTokensLabel != null) {
            insightTokensLabel.setText(L10n.t("label.tokens", count));
          }
        });
  }

  @Override
  public void onDeductionCountUpdate(int count) {
    latestDeductionCount = count; // captured into the Completed-Case Record on a solve
    Platform.runLater(
        () -> {
          if (deductionCountLabel != null) {
            deductionCountLabel.setText(L10n.t("label.deductions", count));
          }
        });
  }

  @Override
  public void onCommandCooldownUpdate(String commandType, long cooldownUntil) {
    Platform.runLater(
        () -> {
          startCooldownCountdown(commandType, cooldownUntil);
          // Also float it above the Pinboard when the board is open (it would otherwise hide the
          // sidebar countdown, being a separate window on top).
          if (gameScreenController != null) {
            gameScreenController.showCommandCooldown(commandType, cooldownUntil);
          }
        });
  }

  /**
   * Runs the right-panel (under Status) cooldown countdown: shows the row and ticks it down once a
   * second from {@code cooldownUntil} (epoch millis), hiding it when the lock elapses. Must be
   * called on the FX thread.
   */
  private void startCooldownCountdown(String commandType, long cooldownUntil) {
    if (cooldownTimerLabel == null) {
      return;
    }
    if (cooldownTimeline != null) {
      cooldownTimeline.stop();
    }
    String label =
        commandType == null || commandType.isBlank()
            ? "Locked"
            : Character.toUpperCase(commandType.charAt(0)) + commandType.substring(1);
    Runnable tick =
        () -> {
          long remainingMs = cooldownUntil - System.currentTimeMillis();
          if (remainingMs <= 0) {
            hideCooldownCountdown();
            return;
          }
          long totalSeconds =
              (remainingMs + 999) / 1000; // ceil so it never shows 0:00 while locked
          cooldownTimerLabel.setText(
              label + "  " + (totalSeconds / 60) + ":" + String.format("%02d", totalSeconds % 60));
          setCooldownRowVisible(true);
        };
    tick.run();
    cooldownTimeline =
        new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), e -> tick.run()));
    cooldownTimeline.setCycleCount(javafx.animation.Timeline.INDEFINITE);
    cooldownTimeline.play();
  }

  /** Stops the sidebar countdown and hides its row. Safe to call when nothing is showing. */
  private void hideCooldownCountdown() {
    if (cooldownTimeline != null) {
      cooldownTimeline.stop();
      cooldownTimeline = null;
    }
    setCooldownRowVisible(false);
  }

  private void setCooldownRowVisible(boolean visible) {
    if (cooldownRow != null) {
      cooldownRow.setVisible(visible);
      cooldownRow.setManaged(visible);
    }
  }

  @Override
  public void onFinalExamUnlocked() {
    // The Final Exam button is always enabled; nothing to unlock in the chrome.
  }

  /**
   * Resets session state for a fresh single-player run and creates the SP engine wrapper. The menu
   * screen drives Case/Language selection from the returned instance.
   */
  public SinglePlayerMain startSinglePlayerSession() {
    isSinglePlayer = true;
    isHostPlayer = true;
    reviewModeActive = false; // a fresh session (incl. "Play again") is record-eligible
    latestDeductionCount = 0;
    // A fresh session must not inherit any open popup/sub-window/overlay from a previous game
    // (.scratch/gui-tutorial-exit-cleanup).
    teardownInGameSurfaces();
    hideCooldownCountdown();
    gameScreenController.resetForNewSession();
    examScreenController.reset();

    // Clear the room view to prevent state bleeding from multiplayer
    updateRoomView(null);
    updateStatus(L10n.t("status.startingSinglePlayer"));
    currentState = UIState.MENU;
    singlePlayerGame = new SinglePlayerMain();
    return singlePlayerGame;
  }

  public SinglePlayerMain getSinglePlayerGame() {
    return singlePlayerGame;
  }

  /**
   * Whether a saved game exists to resume (.scratch/main-menu DEC-5). The main menu shows the
   * petrol "Continue" primary only when this is true; otherwise Single player is the primary.
   *
   * <p>There is no save/load persistence yet, so this returns false and Continue stays hidden. The
   * seam is here so the menu wiring is correct the day a save system flips it to true.
   */
  public boolean hasResumableGame() {
    return false;
  }

  /** Resumes the saved game (.scratch/main-menu DEC-5). Wired for {@link #hasResumableGame()}. */
  public void resumeGame() {
    if (hasResumableGame()) {
      showGameScreen();
    }
  }

  public boolean isSinglePlayerMode() {
    return isSinglePlayer;
  }

  public boolean isHostPlayer() {
    return isHostPlayer;
  }

  /** True while a solved case is open as a read-only Review Session (gui-review-enter-case). */
  public boolean isReviewModeActive() {
    return reviewModeActive;
  }

  /**
   * Wires the typed-event seams (state listener + {@link GuiGameOutputSink}), points the
   * ImageManager at the case directory, initializes the Case, and shows its Invitation.
   */
  public void launchSinglePlayerCase(JsonDTO.CaseFile caseFile, String langCode) {
    JsonDTO.LocalizedCaseFile localizedCase = prepareSinglePlayerCase(caseFile, langCode);
    showCaseInvitation(localizedCase.getInvitation(), true);
  }

  /**
   * Selects + initializes a single-player case (the shared setup behind both the legacy invitation
   * screen and the new menu dossier): wires the typed-event seams, points the ImageManager at the
   * case directory, captures the case for the soundtrack service, and initializes the engine.
   */
  private JsonDTO.LocalizedCaseFile prepareSinglePlayerCase(
      JsonDTO.CaseFile caseFile, String langCode) {
    // Every single-player case start is a fresh, record-eligible, fully-interactive session by
    // default — clear any leftover Review state so "Play again" (and any normal start) isn't gated
    // like Review (.scratch/gui-review-enter-case). The Review path re-arms it AFTER this call.
    reviewModeActive = false;
    JsonDTO.LocalizedCaseFile localizedCase =
        singlePlayerGame.selectCaseAndLanguage(caseFile, langCode);
    if (singlePlayerGame.getGameContext() != null) {
      singlePlayerGame.getGameContext().setStateListener(this);
      singlePlayerGame.getGameContext().setOutputSink(new GuiGameOutputSink(this));
      // Pinboard syncs (the red contradiction link) route to the board, not the terminal.
      singlePlayerGame.getGameContext().setPinboardUpdateHandler(this::applyPinboardUpdate);
      // Scaffolding announcements in the UI language; case content stays as selected above.
      singlePlayerGame.getGameContext().setGameTexts(new ui.i18n.L10nGameTexts());
    }
    applyCaseImageDirectory(caseFile);
    // Captured for the soundtrack service (metadata.soundtrack + case dir); playback starts when
    // the
    // player actually begins the case ("start case").
    this.activeCaseFile = caseFile;
    // The language this case is being played in — saved with the solve so Review can re-open the
    // case in the same language its Journal/Pinboard text was written in.
    this.activeCaseLang = langCode;
    applyCaseScriptClass(langCode);
    singlePlayerGame.initializeCase(localizedCase);
    return localizedCase;
  }

  /**
   * Gives the terminal a face that covers the CASE's script. The {@code .lang-<code>} faces follow
   * the interface language, but a case can be read in another language (English UI, Russian case) —
   * and the terminal's typewriter face has no Cyrillic/Arabic/CJK glyphs, so JavaFX substitutes them
   * one glyph at a time and the advances come out wide and gap-ridden. Tagging the terminal panel
   * with the case's {@code .lang-name-<code>} (the same per-script seam the cross-language banner
   * uses) renders case text in a font that actually covers it.
   */
  private void applyCaseScriptClass(String langCode) {
    if (terminalPanel == null) {
      return;
    }
    terminalPanel.getStyleClass().removeIf(c -> c.startsWith("lang-name-"));
    if (langCode != null && !langCode.isBlank()) {
      terminalPanel.getStyleClass().add("lang-name-" + langCode);
    }
  }

  /**
   * Begins a single-player case straight from the menu's invitation dossier (MENU_DESIGN #2): the
   * dossier already presents the invitation letter and the "Begin investigation" primary action, so
   * this skips the legacy in-game invitation screen and starts the case immediately — the same
   * {@code start case} the legacy "Start case" button fired. The "start case" command runs off the
   * FX thread so the UI never freezes.
   */
  public void beginSinglePlayerCase(JsonDTO.CaseFile caseFile, String langCode) {
    prepareSinglePlayerCase(caseFile, langCode);
    playSound("pageflip.mp3");
    new Thread(
            () -> {
              singlePlayerGame.processCommand("start case");
              Platform.runLater(
                  () -> {
                    showGameScreen();
                    startCaseSoundtrack();
                  });
            })
        .start();
  }

  /** Re-entry to SP Case selection (ReturnToCaseSelectionDTO from the engine). */
  public void showCaseSelectionMenu() {
    Platform.runLater(
        () -> {
          soundtrackService.stop(); // left the case for selection → silence
          gameScreenController.resetCaseContent();
          // The Final Exam rapid-submission freeze is per-case anti-abuse; leaving the case for
          // selection ends it so the next case starts unfrozen (returnToMainMenu already does this,
          // but the SP "return to case selection" path did not — the freeze leaked into the next
          // case).
          resetFinalExamCooldown();
          currentState = UIState.MENU;
          menuController.showCaseSelection();
          showScreenNow(menuController);
        });
  }

  public void returnToMultiplayerMenu() {
    Platform.runLater(
        () -> {
          // This will be called by the parser when the host cancels
          onMainMenu();
        });
  }

  public void returnToMainMenu() {
    manualDisconnect = true;
    soundtrackService.stop(); // leaving the case → silence the ambient track
    GameClient.LaunchMode lastLaunchMode = gameClient != null ? gameClient.getLaunchMode() : null;

    // If we are hosting, the lobby delays the server shutdown slightly so in-flight
    // messages (like ReturnToLobbyDTO) are flushed to clients first.
    lobbyController.stopHosting();

    if (gameClient != null) {
      try {
        gameClient.stopClient();
      } catch (Exception e) {
        logger.warn("Error stopping client: {}", e.getMessage());
      }
      gameClient = null;
    }
    if (gameClientThread != null) {
      if (gameClientThread.isAlive()) {
        gameClientThread.interrupt();
      }
      gameClientThread = null;
    }
    lobbyController.stopDiscovery();

    // Tear down every transient in-game surface so no stale popup/sub-window/overlay survives the
    // return to the menu and reappears in the next session (.scratch/gui-tutorial-exit-cleanup).
    teardownInGameSurfaces();

    // Clear discovered evidence and the Pinboard on exit
    gameScreenController.resetCaseContent();

    // Reset token display to avoid stale state
    onInsightTokensUpdate(0);

    // Navigate to the appropriate menu based on launch mode
    if (lastLaunchMode == GameClient.LaunchMode.JOIN_ONLY) {
      lobbyController.showJoinMenu();
    } else {
      showMainMenuScreen();
    }
  }

  /** Shows the main menu screen (FX-thread marshalled by updateUIVisibility). */
  public void showMainMenuScreen() {
    currentState = UIState.MENU;
    updateUIVisibility();
  }

  // --- Settings, theme & language (MENU_DESIGN #6) ---

  /** Opens the full-window Settings dossier (MENU_DESIGN #6); the Back step returns via onBack. */
  public void showSettings(Runnable onBack) {
    showScreen(new ui.screens.SettingsController(this, onBack));
  }

  /** The active theme name ("light"/"dark") for the Settings toggle. */
  public String getThemeName() {
    return appSettings.theme();
  }

  /**
   * Switches light/dark theme (DESIGN.md §8) and persists it: recolours the CSS (the scene
   * stylesheet) and the canvas palette, disposes the cached sub-windows so they rebuild in-theme on
   * next open, and re-renders the visible screen so its canvas chrome redraws immediately.
   */
  public void setTheme(String theme) {
    appSettings = appSettings.withTheme(theme);
    appSettingsStore.save(appSettings);
    ui.util.Palette.applyTheme(appSettings.theme());
    ui.util.Theme.apply(getScene(), appSettings.theme());
    gameScreenController.disposeCachedSubWindows();
    if (currentScreen != null) {
      currentScreen.onThemeChanged();
    }
  }

  /** Applies the saved theme to the window scene once it exists (called from the launcher). */
  public void applySavedTheme() {
    ui.util.Theme.apply(getScene(), appSettings.theme());
  }

  /** Switches the UI language live (re-renders via the L10n onChange listener) and persists it. */
  public void setUiLanguage(String code) {
    appSettings = appSettings.withLanguage(code);
    appSettingsStore.save(appSettings);
    L10n.setLanguage(code);
  }

  /** The terminal text-size multiplier (Settings "Terminal text size" slider; clamped 0.9–1.4). */
  public double getTerminalTextScale() {
    return appSettings.terminalTextScale();
  }

  /** The reading text-size multiplier (Settings "Reading text size" slider; clamped 0.9–1.4). */
  public double getReadingTextScale() {
    return appSettings.readingTextScale();
  }

  /**
   * Sets the TERMINAL text size live (.scratch/gui-typography-readability, Phase 2): persists it
   * and re-tags the scene root so the terminal transcript/input/prompt resize immediately. Scoped
   * to the terminal only.
   */
  public void setTerminalTextScale(double scale) {
    appSettings = appSettings.withTerminalTextScale(scale);
    appSettingsStore.save(appSettings);
    applyContentScaleStyleClass();
  }

  /**
   * Sets the READING text size live (.scratch/gui-typography-readability): persists it, re-tags the
   * scene root so the whole main window (menus, popups, sidebar, in-scene exam — everything but the
   * terminal) resizes immediately, and disposes the cached sub-windows so the Journal/Case
   * File/Pinboard rebuild at the new size on next open.
   */
  public void setReadingTextScale(double scale) {
    appSettings = appSettings.withReadingTextScale(scale);
    appSettingsStore.save(appSettings);
    applyContentScaleStyleClass();
    if (gameScreenController != null) {
      gameScreenController.disposeCachedSubWindows();
    }
  }

  private javafx.scene.Scene getScene() {
    return mainBorderPane.getScene();
  }

  // --- Pause menu (MENU_DESIGN #7) ---

  /** Opens the in-game pause menu (dimmed game + dossier card). No-op outside a game. */
  public void showPauseMenu() {
    if (currentState == UIState.GAME_SINGLE || currentState == UIState.GAME_MULTI) {
      gameScreenController.showPauseMenu();
    }
  }

  /** From the pause menu's Settings: return to the paused game and re-raise the pause card. */
  public void showSettingsFromPause() {
    showSettings(
        () -> {
          showGameScreen();
          gameScreenController.showPauseMenu();
        });
  }

  // --- Lobby façade: menu buttons route here; the lobby screen owns the flows. ---

  /**
   * Opens the full-window Multiplayer hub (Host / Join) — the main-menu Multiplayer plate routes
   * here.
   */
  public void showMultiplayerHub() {
    lobbyController.showMultiplayerHub();
  }

  public void startHostMultiplayer() {
    lobbyController.startHostMultiplayer();
  }

  public void startJoinMultiplayer() {
    lobbyController.startJoinMultiplayer();
  }

  // --- Multiplayer session plumbing shared between the lobby screen and the shell ---

  /** Resets per-session state ahead of a multiplayer connection (the lobby calls this). */
  public void resetForMultiplayer() {
    isSinglePlayer = false;
    isHostPlayer = false; // Guest by default, updated by server
    manualDisconnect = false;
    reviewModeActive = false; // never carry an SP Review lockout into a multiplayer session
    gameScreenController.resetForNewSession();

    updateStatus(L10n.t("status.startingMultiplayer"));
    currentState = UIState.GAME_MULTI;
  }

  public void setGameClient(GameClient client, Thread clientThread) {
    this.gameClient = client;
    this.gameClientThread = clientThread;
  }

  public void clearGameClient() {
    this.gameClient = null;
    this.gameClientThread = null;
  }

  public boolean isManualDisconnect() {
    return manualDisconnect;
  }

  public void shutdown() {
    lobbyController.stopDiscovery();
    lobbyController.shutdownEmbeddedServer(); // Ensure server is stopped on app exit
    logger.info("Shutting down application...");
    if (gameClient != null) {
      gameClient.stopClient();
    }
    if (gameClientThread != null && gameClientThread.isAlive()) {
      try {
        gameClientThread.join(2000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    Platform.exit();
    System.exit(0);
  }

  @FXML
  private void handleTerminalInput() {
    String input = terminalInputField.getText().trim();
    if (!input.isEmpty()) {
      // Submitting a command always returns the player to the newest output (issue 01).
      repinTerminalToBottom();
      if (tutorialManager != null && tutorialManager.isActive()) {
        // Enforce the taught input method: on a GUI-taught exam question, a typed exam answer must
        // not slip through to the engine (it would advance the exam but not the GUI step, stalling
        // the tutorial). Nudge the player to the dropdowns instead
        // (.scratch/gui-exam-tutorial-input-enforce).
        if (currentScreen == examScreenController
            && tutorialExamInputMode() == ExamInputMode.GUI_ONLY
            && EXAM_ANSWER_PATTERN.matcher(input).matches()) {
          appendTerminalText("\n" + L10n.t("exam.tutorial.useDropdowns") + "\n");
          terminalInputField.clear();
          return;
        }
        routeToTutorialIfActive(input);
        terminalInputField.clear();
        return;
      }

      // ADR-0002: the mounted screen gets the line first; legacy states fall through.
      if (currentScreen != null && currentScreen.handleTerminalInput(input)) {
        terminalInputField.clear();
        return;
      }

      if (currentState == UIState.CASE_INVITATION && !isSinglePlayer) {
        gameClient.enqueueUserInput(input);
      } else if (currentState == UIState.CASE_INVITATION && isSinglePlayer) {
        if (input.equalsIgnoreCase("start case")) {
          handleStartCase();
        }
      }
      terminalInputField.clear();
    }
  }

  // --- In-game services: the shell keeps the seams, the game screen keeps the logic. ---

  public void openCaseFileWindow() {
    gameScreenController.openCaseFileWindow();
  }

  public void openTasksWindow() {
    gameScreenController.openTasksWindow();
  }

  public void openJournalWindow() {
    gameScreenController.openJournalWindow();
  }

  public void openPinboardWindow() {
    gameScreenController.openPinboardWindow();
  }

  public void openChatWindow() {
    gameScreenController.openChatWindow();
  }

  public void openHelpWindow() {
    gameScreenController.openHelpWindow();
  }

  public JsonDTO.LocalizedCaseFile.LocalizedCaseFileBlock getActiveCaseFileBlock() {
    if (isSinglePlayer && singlePlayerGame != null && singlePlayerGame.getGameContext() != null) {
      return singlePlayerGame.getGameContext().getSelectedCase().getCaseFile();
    } else if (!isSinglePlayer && gameClient != null && gameClient.getCaseTitle() != null) {
      String title = gameClient.getCaseTitle().trim();
      if (!title.isEmpty()) {
        // To fetch the CaseFileBlock from Localized cases, we can use CaseLoader
        List<JsonDTO.CaseFile> loadedCases =
            null; // We could use GameClient's cache if exposed, but loading is
        // fast
        try {
          loadedCases = extractors.CaseLoader.loadCases("cases");
          for (JsonDTO.CaseFile cf : loadedCases) {
            boolean matchFound = false;
            String matchedLang = "en"; // Default

            // Check universal title first
            if (cf.getUniversalTitle() != null
                && cf.getUniversalTitle().trim().equalsIgnoreCase(title)) {
              matchFound = true;
              // Find the first available language, preferably English
              if (cf.getLocalizations().containsKey("en")) {
                matchedLang = "en";
              } else if (!cf.getLocalizations().isEmpty()) {
                matchedLang = cf.getLocalizations().keySet().iterator().next();
              }
            }

            // Check localized titles
            if (!matchFound) {
              for (java.util.Map.Entry<String, JsonDTO.CaseFile.LocalizedData> entry :
                  cf.getLocalizations().entrySet()) {
                if (entry.getValue().getTitle() != null
                    && entry.getValue().getTitle().trim().equalsIgnoreCase(title)) {
                  matchFound = true;
                  matchedLang = entry.getKey();
                  break;
                }
              }
            }

            if (matchFound) {
              JsonDTO.LocalizedCaseFile locFile = new JsonDTO.LocalizedCaseFile(cf, matchedLang);
              if (locFile.getCaseFile() != null) {
                return locFile.getCaseFile();
              } else {
                logger.warn(
                    "CaseFile block was null for resolved case: {}", cf.getUniversalTitle());
              }
            }
          }
          logger.warn("Failed to match GameClient case title: '{}' against loaded cases.", title);
        } catch (Exception e) {
          logger.error("Failed to resolve GameClient case file", e);
        }
      }
    }
    return null;
  }

  public void updateTaskState(String task, boolean isCompleted) {
    gameScreenController.updateTaskState(task, isCompleted);
  }

  public void exitFinalExamMode() {
    examScreenController.exitFinalExamMode();
  }

  public void incrementUnreadChat() {
    gameScreenController.incrementUnreadChat();
  }

  /** Writes the unread-chat badge in the toolbar chrome; the game screen owns the count. */
  public void setUnreadChatBadge(int count) {
    if (count > 0) {
      unreadChatLabel.setText(String.valueOf(count));
      unreadChatLabel.setVisible(true);
    } else {
      unreadChatLabel.setVisible(false);
    }
  }

  public void updateStatus(String status) {
    if (statusLabel != null) {
      statusLabel.setText(status);
    }
  }

  // ===================== Final Exam cooldown (anti-abuse) =====================

  /**
   * True while the Final Exam is frozen for rapid re-submission; submit + the button are locked.
   */
  public boolean isFinalExamFrozen() {
    return examCooldown.isFrozen();
  }

  /**
   * Records one Final Exam submission; if the rapid-submission threshold is exceeded this trips the
   * 5-minute freeze and starts the live countdown (disables the Final exam button + status
   * message). Called from the exam-result path (single-player and multiplayer).
   */
  public void recordFinalExamSubmission() {
    // Keep all cooldown state + Timeline UI on the FX thread (MP results arrive off-thread).
    if (!Platform.isFxApplicationThread()) {
      Platform.runLater(this::recordFinalExamSubmission);
      return;
    }
    if (examCooldown.recordSubmission()) {
      beginExamFreezeCountdown();
    }
  }

  /** Clears the cooldown + any active freeze (called on session/case change). */
  public void resetFinalExamCooldown() {
    examCooldown.reset();
    endExamFreeze();
  }

  /**
   * If {@code command} would (re)start the Final Exam while it is frozen for rapid re-submission,
   * refuses it — prints the lockout message, (re)starts the countdown — and returns true. Used by
   * both the toolbar button and the typed-command path so neither can bypass the freeze.
   */
  public boolean refuseFinalExamIfFrozen(String command) {
    if (examCooldown.isFrozen() && isFinalExamInitiation(command)) {
      appendTerminalText(finalExamLockedMessage() + "\n");
      beginExamFreezeCountdown();
      return true;
    }
    return false;
  }

  /** Whether {@code command} is one of the accepted Final Exam initiation phrasings. */
  private static boolean isFinalExamInitiation(String command) {
    if (command == null) {
      return false;
    }
    String c = command.trim().toLowerCase();
    return c.equals("final exam")
        || c.equals("initiate final exam")
        || c.equals("request final exam");
  }

  /** The lockout message with the live remaining time, e.g. "… Try again in 4:59." */
  private String finalExamLockedMessage() {
    return L10n.t("finalExam.locked", formatCooldown(examCooldown.remainingMillis()));
  }

  /**
   * Starts (or restarts) the per-second countdown that maintains the freeze UI until it elapses.
   */
  private void beginExamFreezeCountdown() {
    finalExamButton.setDisable(true);
    if (examFreezeTicker != null) {
      examFreezeTicker.stop();
    }
    examFreezeTicker =
        new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.ZERO, e -> tickExamFreeze()),
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1)));
    examFreezeTicker.setCycleCount(javafx.animation.Animation.INDEFINITE);
    examFreezeTicker.play();
    tickExamFreeze(); // show the countdown immediately, don't wait a second
  }

  /** One countdown tick: refresh the status message, or lift the freeze once it has elapsed. */
  private void tickExamFreeze() {
    if (examCooldown.isFrozen()) {
      finalExamButton.setDisable(true);
      updateStatus(finalExamLockedMessage());
    } else {
      endExamFreeze();
    }
  }

  /** Stops the countdown and re-enables Final Exam access. */
  private void endExamFreeze() {
    boolean wasCountingDown = examFreezeTicker != null;
    if (examFreezeTicker != null) {
      examFreezeTicker.stop();
      examFreezeTicker = null;
    }
    if (finalExamButton != null) {
      finalExamButton.setDisable(false);
    }
    if (wasCountingDown) {
      updateStatus(L10n.t("finalExam.unlocked"));
    }
  }

  /** Formats a remaining-cooldown duration as {@code m:ss}. */
  private static String formatCooldown(long millis) {
    long totalSeconds = (millis + 999) / 1000; // round up so it never shows 0:00 while still frozen
    return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
  }

  @Override
  public void appendTerminalText(String text) {
    appendTerminalText(text, ui.terminal.TerminalLineKind.NORMAL);
  }

  /**
   * The single terminal append point for BOTH single-player and multiplayer
   * (.scratch/terminal-scroll-mp DEC-1): every line — the SP {@code GuiGameOutputSink}, the MP
   * {@code GameClient} console writer, menu/lobby/exam/game-screen prompts, tutorials — funnels
   * here, so the history-aware auto-scroll in {@link ui.terminal.TerminalView#appendLine} runs
   * identically for all of them. This method owns the FX-thread marshal, so off-thread callers (the
   * MP network thread) need not wrap it themselves. The single-arg overload above keeps
   * non-classified writers on the NORMAL kind.
   */
  public void appendTerminalText(String text, ui.terminal.TerminalLineKind kind) {
    if (!Platform.isFxApplicationThread()) {
      Platform.runLater(() -> terminalView.appendLine(text, kind));
      return;
    }
    terminalView.appendLine(text, kind);
  }

  /**
   * Centralised dispatch for inputs that may be tutorial-bound. When a tutorial is active and the
   * orchestrator is initialised, routes the command through both the engine and the tutorial
   * observer.
   */
  private void routeToTutorialIfActive(String command) {
    if (tutorialManager != null && tutorialManager.isActive() && tutorialOrchestrator != null) {
      tutorialOrchestrator.handleUserInput(command);
    }
  }

  public StackPane getRoomPane() {
    return roomPane;
  }

  public VBox getRightInfoPanel() {
    return rightInfoPanel;
  }

  public VBox getNeighboringRoomsContainer() {
    return neighboringRoomsContainer;
  }

  // ========== Tutorial System Methods ==========

  public void showTutorialOverlay(String message, String arrowTarget, boolean dismissible) {
    Platform.runLater(
        () -> {
          if (tutorialOverlayPane == null) {
            // Create the overlay pane. Not mouse-transparent (so the close button is clickable) but
            // pickOnBounds=false so its transparent areas still pass clicks through to the room;
            // only
            // the small opaque card intercepts (.scratch/gui-tutorial-bubble-polish).
            tutorialOverlayPane = new StackPane();
            tutorialOverlayPane.setPickOnBounds(false);
            tutorialOverlayPane.getStyleClass().add("tutorial-overlay-pane");
            // Re-derive the arrow position from the live pane size on every resize (issue 02).
            tutorialOverlayPane
                .widthProperty()
                .addListener((obs, oldVal, newVal) -> repositionTutorialArrow());
            tutorialOverlayPane
                .heightProperty()
                .addListener((obs, oldVal, newVal) -> repositionTutorialArrow());
          }

          // Remove old overlay content
          tutorialOverlayPane.getChildren().clear();

          // Create the tutorial label (the reading face on the card's vellum).
          if (tutorialOverlayLabel == null) {
            tutorialOverlayLabel = new Label();
            tutorialOverlayLabel.getStyleClass().add("tutorial-overlay-label");
            // Tip card width follows the pane, not a fixed 600px (issue 02).
            ui.util.ViewportSizing.bindMaxWidthToViewport(
                tutorialOverlayLabel, tutorialOverlayPane, 0.6, 320);
          }
          tutorialOverlayLabel.setText(message);
          tutorialOverlayLabel.setWrapText(true);

          // Small ochre dossier rule (DESIGN.md §5).
          if (tutorialOverlayRule == null) {
            tutorialOverlayRule = new javafx.scene.layout.Region();
            tutorialOverlayRule.getStyleClass().add("tutorial-overlay-rule");
          }

          // Top-right close (×) — dismisses only the current message; the next step re-shows the
          // bubble. Omitted on the final ("type continue") overlay (.scratch/gui-tutorial-bubble-
          // polish).
          if (tutorialCloseButton == null) {
            tutorialCloseButton = new javafx.scene.control.Button("×");
            tutorialCloseButton.getStyleClass().add("tutorial-overlay-close");
            tutorialCloseButton.setTooltip(new Tooltip(L10n.t("common.close")));
            tutorialCloseButton.setFocusTraversable(false);
            tutorialCloseButton.setOnAction(e -> hideTutorialOverlay());
          }

          // Guidance card: an on-brand dossier card (vellum + ink contour + ochre rule) pinned
          // bottom-center. Capped to PREF size so the centering StackPane can't stretch it to cover
          // the room (.scratch/gui-tutorial-overlay-transparent). Not mouse-transparent so the
          // close
          // button works; the pane's pickOnBounds=false keeps the rest of the view clickable.
          if (tutorialOverlayCard == null) {
            tutorialOverlayCard = new javafx.scene.layout.VBox(6);
            tutorialOverlayCard.getStyleClass().add("tutorial-overlay-card");
            tutorialOverlayCard.setAlignment(Pos.CENTER_LEFT);
            tutorialOverlayCard.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
            tutorialOverlayCard.setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
          }
          // The final ("type continue") bubble is the non-dismissible one: give it a Continue
          // button so the player can EITHER type "continue" OR click — both take the identical path
          // (routeToTutorialIfActive → the tutorial's meta-advance handling for "continue").
          if (tutorialContinueButton == null) {
            tutorialContinueButton = new javafx.scene.control.Button(L10n.t("tutorial.continue"));
            // No extra style class → inherits the app's standard themed .button (petrol,
            // dark-aware).
            tutorialContinueButton.setOnAction(e -> routeToTutorialIfActive("continue"));
          }

          tutorialOverlayCard.getChildren().clear();
          if (dismissible) {
            Region closeSpacer = new Region();
            HBox.setHgrow(closeSpacer, Priority.ALWAYS);
            HBox closeRow = new HBox(closeSpacer, tutorialCloseButton);
            closeRow.setAlignment(Pos.CENTER_RIGHT);
            tutorialOverlayCard.getChildren().add(closeRow);
          }
          tutorialOverlayCard.getChildren().addAll(tutorialOverlayRule, tutorialOverlayLabel);
          if (!dismissible) {
            Region continueSpacer = new Region();
            HBox.setHgrow(continueSpacer, Priority.ALWAYS);
            HBox continueRow = new HBox(continueSpacer, tutorialContinueButton);
            continueRow.setAlignment(Pos.CENTER_RIGHT);
            tutorialOverlayCard.getChildren().add(continueRow);
          }
          // Offset the card AWAY from the pointed-at target so the referenced object/control is
          // never
          // covered (.scratch/gui-tutorial-bubble-position).
          tutorialArrowTarget = arrowTarget;
          positionTutorialCard(arrowTarget);

          // Create/update the arrow
          if (tutorialArrow == null) {
            tutorialArrow = new Polygon();
            // Fill/stroke come from CSS (.tutorial-arrow → -sl-ochre/-sl-oxblood) so they flip with
            // the theme (DESIGN.md §8). Decorative — never intercept clicks (the pane is now
            // pickable
            // so the close button works).
            tutorialArrow.getStyleClass().add("tutorial-arrow");
            tutorialArrow.setStrokeWidth(1);
            tutorialArrow.setMouseTransparent(true);
          }

          // Position and rotate arrow based on target
          double arrowX, arrowY;
          double rotation = 0;

          switch (arrowTarget) {
            case "TERMINAL":
              arrowX = 0.5;
              arrowY = 0.85; // Points down to terminal
              rotation = 0;
              break;
            case "RIGHT_PANEL":
              arrowX = 0.9;
              arrowY = 0.5; // Points right to exits panel
              rotation = 90;
              break;
            case "CENTER":
              arrowX = 0.5;
              arrowY = 0.5; // Points to room center
              rotation = 180;
              break;
            case "TOP_BAR":
              arrowX = 0.5;
              arrowY = 0.15; // Points up to top bar
              rotation = 180;
              break;
            case "NONE":
            default:
              // Hide the arrow
              tutorialArrow.setVisible(false);
              tutorialOverlayPane.getChildren().addAll(tutorialOverlayCard);
              tutorialOverlayVisible = true;
              addTutorialOverlayToRoomPane();
              return;
          }

          // Create arrow pointing down (base points up)
          tutorialArrow.getPoints().clear();
          tutorialArrow
              .getPoints()
              .addAll(
                  0.0, 40.0, // Bottom left
                  20.0, 0.0, // Top center (tip)
                  40.0, 40.0 // Bottom right
                  );
          tutorialArrow.setVisible(true);
          tutorialArrow.setRotate(rotation);

          // Position arrow from the normalized anchor against the live pane size — no
          // hard-coded ±200/±300px margins (issue 02). Logical insets mirror with
          // NodeOrientation, so the arrow tracks the mirrored layout in Arabic.
          StackPane.setAlignment(tutorialArrow, Pos.CENTER);
          tutorialArrowNormX = arrowX;
          tutorialArrowNormY = arrowY;
          repositionTutorialArrow();

          // Add to overlay
          tutorialOverlayPane.getChildren().addAll(tutorialArrow, tutorialOverlayCard);
          tutorialOverlayVisible = true;

          addTutorialOverlayToRoomPane();
        });
  }

  /**
   * Centers the arrow on its normalized (0–1) anchor by offsetting the StackPane margins as
   * fractions of the live overlay-pane size: offset = (norm - 0.5) × paneSize on each axis. Called
   * on every overlay-pane resize while the tutorial is showing.
   */
  private void repositionTutorialArrow() {
    if (tutorialArrow == null || !tutorialArrow.isVisible() || tutorialOverlayPane == null) {
      return;
    }
    double paneW = tutorialOverlayPane.getWidth();
    double paneH = tutorialOverlayPane.getHeight();
    if (paneW <= 0 || paneH <= 0) {
      // Not laid out yet: the room pane is the overlay's viewport, use its size.
      paneW = roomPane.getWidth();
      paneH = roomPane.getHeight();
    }
    double offsetX = (tutorialArrowNormX - 0.5) * paneW;
    double offsetY = (tutorialArrowNormY - 0.5) * paneH;
    StackPane.setMargin(tutorialArrow, new Insets(offsetY, -offsetX, -offsetY, offsetX));
  }

  /**
   * Pins the guidance card AWAY from the pointed-at target so the referenced object/sprite/control
   * is never covered (.scratch/gui-tutorial-bubble-position): a room object (CENTER) sits below, so
   * the card goes to the TOP with the arrow pointing down to it; the exits sidebar (RIGHT_PANEL) is
   * on the right, so the card goes LEFT; otherwise the bottom is clear. While a result popup
   * occupies the top band during a tutorial, the card dodges to the bottom regardless so the two
   * never overlap.
   */
  private void positionTutorialCard(String arrowTarget) {
    if (tutorialOverlayCard == null) {
      return;
    }
    Pos pos =
        tutorialCardAlignment(
            arrowTarget, resultPopupShowing, currentScreen == examScreenController);
    Insets margin;
    if (pos == Pos.TOP_CENTER) {
      margin = new Insets(20, 0, 0, 0);
    } else if (pos == Pos.CENTER_LEFT) {
      margin = new Insets(0, 0, 0, 20);
    } else {
      margin = new Insets(0, 0, resultPopupShowing ? 12 : 80, 0);
    }
    StackPane.setAlignment(tutorialOverlayCard, pos);
    StackPane.setMargin(tutorialOverlayCard, margin);
  }

  /**
   * Where the guidance card sits so it never covers the content it references or the control the
   * player needs (.scratch/gui-tutorial-bubble-position). Pure.
   *
   * <p>On the Final Exam screen the card always goes to the TOP — over the "Final exam" title,
   * clear of the question + answer area (Q steps) and clear of the centred "Case solved" victory
   * card and its close button (the final step). Otherwise: a result popup occupies the top band, so
   * dodge to the bottom; a room object (CENTER) sits below, so go above it; the exits sidebar
   * (RIGHT_PANEL) is on the right, so go beside it; else the bottom is clear.
   */
  static Pos tutorialCardAlignment(
      String arrowTarget, boolean resultPopupShowing, boolean examScreen) {
    if (examScreen) {
      return Pos.TOP_CENTER;
    }
    if (resultPopupShowing) {
      return Pos.BOTTOM_CENTER;
    }
    if ("CENTER".equals(arrowTarget)) {
      return Pos.TOP_CENTER;
    }
    if ("RIGHT_PANEL".equals(arrowTarget)) {
      return Pos.CENTER_LEFT;
    }
    return Pos.BOTTOM_CENTER; // TERMINAL / TOP_BAR / NONE
  }

  private void addTutorialOverlayToRoomPane() {
    if (!roomPane.getChildren().contains(tutorialOverlayPane)) {
      roomPane.getChildren().add(tutorialOverlayPane);
    }
  }

  public void hideTutorialOverlay() {
    Platform.runLater(
        () -> {
          if (tutorialOverlayPane != null) {
            roomPane.getChildren().remove(tutorialOverlayPane);
          }
          tutorialOverlayVisible = false;
        });
  }

  public void showGameView() {
    Platform.runLater(
        () -> {
          // Transition to game view state for tutorial
          currentState = UIState.GAME_SINGLE;
          isSinglePlayer = true;
          isHostPlayer = true;
          showScreenNow(gameScreenController);
        });
  }

  public void showLauncherMenu() {
    Platform.runLater(
        () -> {
          // Return to main menu
          currentState = UIState.MENU;
          hideTutorialOverlay();
          updateUIVisibility();
        });
  }

  // ========== End Tutorial System Methods ==========

  public void sendCommand(String command) {
    // The author may rename the assistant; `ask <helperName>` is rewritten to the canonical
    // `ask watson` here so the custom name works while `ask watson` always keeps working.
    command = rewriteHelperAlias(command);
    // Any user-initiated command (typed, or a room-nav / toolbar button) snaps the terminal back to
    // the newest line and re-engages auto-scroll — the "scrolled up" exception only suppresses
    // passive/background output, not the player's own actions (.scratch/ingame-fixes-3 issue 02).
    repinTerminalToBottom();
    if (tutorialManager != null && tutorialManager.isActive()) {
      routeToTutorialIfActive(command);
      return;
    }

    boolean examShowing = currentScreen == examScreenController;
    // While the Final Exam is in progress, a routed gameplay command (e.g. a neighboring-Room move
    // button) is refused here unless it is a reference tool the detective keeps open
    // (.scratch/exam-command-lockout). The engine authority is the real backstop; this avoids a
    // wasted round-trip and a room-view flicker behind the exam. Once the result is showing,
    // navigation works unchanged.
    if (examShowing
        && examScreenController.isExamInProgress()
        && !isExamReferenceCommand(command)) {
      appendTerminalText(L10n.t("exam.commandBlocked") + "\n");
      return;
    }
    if ((currentState == UIState.GAME_MULTI
            || currentScreen == lobbyController
            || (examShowing && !isSinglePlayer)
            || (currentState == UIState.CASE_INVITATION && !isSinglePlayer))
        && gameClient != null) {
      gameClient.enqueueUserInput(command);
    } else if ((currentState == UIState.GAME_SINGLE
            || (examShowing && isSinglePlayer)
            || (currentState == UIState.CASE_INVITATION && isSinglePlayer))
        && singlePlayerGame != null) {
      singlePlayerGame.processCommand(command);
    }
  }

  /**
   * Rewrites a leading {@code ask <helperName>} to the canonical {@code ask watson} so the author's
   * assistant name invokes the assistant. The stable {@code ask watson} keyword always works too.
   * Case-insensitive; any trailing target (e.g. {@code ask hastings torn_letter}) is preserved. A
   * no-op when the case authored no custom helper name or the command is not an ask.
   */
  public String rewriteHelperAlias(String command) {
    if (command == null) {
      return null;
    }
    String helper = getActiveHelperName();
    if (helper == null || helper.isBlank()) {
      return command;
    }
    String trimmed = command.stripLeading();
    String lower = trimmed.toLowerCase();
    String prefix = "ask " + helper.trim().toLowerCase();
    if (lower.equals(prefix)) {
      return "ask watson";
    }
    if (lower.startsWith(prefix + " ")) {
      return "ask watson " + trimmed.substring(prefix.length()).trim();
    }
    return command;
  }

  /**
   * The reference tools (chat, Journal, Pinboard) the detective may still invoke from the terminal
   * while a Final Exam is in progress (.scratch/exam-command-lockout). Everything else is locked
   * out.
   */
  private static boolean isExamReferenceCommand(String command) {
    if (command == null || command.isBlank()) {
      return false;
    }
    String verb = command.trim().split("\\s+", 2)[0].toLowerCase();
    return verb.equals("chat") || verb.equals("journal") || verb.equals("pinboard");
  }

  public GameClient getGameClient() {
    return gameClient;
  }

  /** The current local player profile (player-profile feature); never null. */
  public ui.settings.PlayerProfile getPlayerProfile() {
    return playerProfile;
  }

  /** Persists an edited profile and keeps the in-memory copy in sync (best-effort). */
  public void savePlayerProfile(ui.settings.PlayerProfile profile) {
    if (profile == null) {
      return;
    }
    this.playerProfile = profile;
    profileStore.save(profile);
  }

  public void updateRoomView(RoomDescriptionDTO roomDescription) {
    if (gameScreenController != null) {
      gameScreenController.updateRoomView(roomDescription);
    }
  }

  public void refreshRoomView() {
    if (gameScreenController != null) {
      gameScreenController.refreshRoomView();
    }
  }

  public List<String> getWatsonTargets() {
    return gameScreenController != null ? gameScreenController.getWatsonTargets() : List.of();
  }

  public void refreshJournalWindow() {
    if (gameScreenController != null) {
      gameScreenController.refreshJournalWindow();
    }
  }

  @Override
  public void onDisconnected() {
    if (gameClient != null && gameClient.getLaunchMode() == GameClient.LaunchMode.HOST_ONLY) {
      // If the host disconnects, it's usually because they are shutting down.
      // Go directly back to the main menu.
      Platform.runLater(this::returnToMainMenu);
      return;
    }
    lobbyController.showDisconnected();
  }

  @Override
  public void onConnecting() {
    lobbyController.showConnecting();
  }

  @Override
  public void onConnected() {
    // This will shortly be followed by onMainMenu
  }

  @Override
  public void onReturnToMainMenu(String message) {
    Platform.runLater(
        () -> {
          appendTerminalText("\n" + message + "\n", ui.terminal.TerminalLineKind.NORMAL);
          returnToMainMenu();
        });
  }

  @Override
  public void onMainMenu() {
    gameScreenController.clearTaskStates();
    // This method is now a router. Based on the launch mode, it will either
    // show host options or join options, bypassing the old multiplayer menu.
    if (gameClient != null && gameClient.getLaunchMode() == GameClient.LaunchMode.HOST_ONLY) {
      onHostGameOptions();
    } else {
      // Default to join options for NORMAL and JOIN_ONLY modes in the GUI flow
      onJoinGameOptions();
    }
  }

  @Override
  public void onHostGameOptions() {
    lobbyController.showHostOptions();
  }

  @Override
  public void onCaseSelection(List<JsonDTO.CaseFile> cases) {
    lobbyController.showCaseSelection(cases);
  }

  @Override
  public void onLanguageSelection(JsonDTO.CaseFile caseFile) {
    lobbyController.showLanguageSelection(caseFile);
  }

  @Override
  public void onHostingLobby(String gameCode) {
    lobbyController.showHostingLobby(gameCode);
  }

  @Override
  public void onJoinGameOptions() {
    lobbyController.showJoinMenu();
  }

  @Override
  public void onPublicGamesList(List<PublicGameInfoDTO> games) {
    lobbyController.showPublicGamesList(games);
  }

  @Override
  public void onPrivateGameEntry() {
    lobbyController.showPrivateGameEntry();
  }

  @Override
  public void onLobby() {
    lobbyController.setInLobby();
  }

  @Override
  public void onLobbyIdentitiesUpdated() {
    lobbyController.onLobbyIdentitiesUpdated();
  }

  @Override
  public void onEnterGame(RoomDescriptionDTO initialRoom) {
    // Wrap everything to ensure thread safety for UI component creation
    // (PinboardController)
    Platform.runLater(
        () -> {
          isSinglePlayer = false;
          currentState = UIState.GAME_MULTI;
          gameScreenController.onEnterGame(initialRoom);
          // The host's case choice drives it; each client resolves + plays it locally.
          startCaseSoundtrack();
        });
  }

  @Override
  public void onUpdateRoom(RoomDescriptionDTO newRoom) {
    updateRoomView(newRoom);
    // If we are in the Final Exam view (e.g., as a guest waiting for host),
    // and we receive a room update (host pressed Continue), we should exit the exam UI.
    if (currentScreen == examScreenController) {
      examScreenController.exitFinalExamUI();
    }
  }

  @Override
  public void onJoinGameFailed(String message) {
    lobbyController.onJoinGameFailed(message);
  }

  @Override
  public void onReceiveCaseInvitation(String invitation, boolean isHost) {
    this.isHostPlayer = isHost;
    // Both detectives are present: show the two-seat lobby (host + partner, case invitation,
    // ready/start) on the menu chrome instead of the legacy shell-owned invitation pane.
    lobbyController.showLobby(invitation, isHost);
  }

  @Override
  public void onJournalUpdated() {
    Platform.runLater(this::refreshJournalWindow);
  }

  @Override
  public void onDialogueEvent(common.dto.DialogueEventDTO event) {
    showDialogueBubble(event);
  }

  public void showDialogueBubble(common.dto.DialogueEventDTO event) {
    gameScreenController.showDialogueBubble(event);
  }

  /**
   * Applies a single-player pinboard sync (e.g. the red contradiction link) to the in-game board.
   * Wired into {@code GameContextSinglePlayer} as its pinboard-update handler so an {@code
   * UpdatePinboardCommand} broadcast reaches the board instead of being printed to the terminal.
   */
  public void applyPinboardUpdate(common.dto.pinboard.PinboardUpdateDTO update) {
    if (gameScreenController != null) {
      gameScreenController.applyPinboardUpdate(update);
    }
  }

  @Override
  public void onChatMessageReceived(common.dto.ChatMessage message) {
    gameScreenController.onChatMessageReceived(message);
  }

  @Override
  public void onTaskStateUpdate(int taskIndex, boolean isCompleted) {
    gameScreenController.onTaskStateUpdate(taskIndex, isCompleted);
  }

  // --- Final Exam: the shell stays the FinalExamListener; the exam screen owns the view. ---

  @Override
  public void showQuestion(ExamQuestionDTO questionDTO) {
    examScreenController.showQuestion(questionDTO);
  }

  @Override
  public void showExamResults(ExamResultDTO resultDTO) {
    soundtrackService.stop(); // exam-end → the verdict plays out in silence
    examScreenController.showExamResults(resultDTO);
  }

  @Override
  public void showExamView() {
    examScreenController.showExamView();
  }

  @Override
  public void notifyUnansweredQuestions() {
    examScreenController.notifyUnansweredQuestions();
  }

  @Override
  public void onFinalExamRequest(String requesterDisplayName) {
    examScreenController.onFinalExamRequest(requesterDisplayName);
  }

  public void onSinglePlayerExamStarted() {
    examScreenController.onSinglePlayerExamStarted();
  }

  public void onSinglePlayerQuestionUpdate() {
    examScreenController.onSinglePlayerQuestionUpdate();
  }

  public void onSinglePlayerExamResult() {
    soundtrackService.stop(); // exam-end → the verdict plays out in silence
    examScreenController.onSinglePlayerExamResult();
  }
}

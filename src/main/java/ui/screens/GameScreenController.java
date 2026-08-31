package ui.screens;

import common.commands.UpdateTaskStateCommand;
import common.dto.RoomDescriptionDTO;
import java.util.List;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import ui.MainController;
import ui.i18n.L10n;
import ui.pinboard.PinboardController;
import ui.shell.ScreenController;
import ui.util.RoomView;
import ui.windows.ChatWindow;
import ui.windows.HelpWindow;
import ui.windows.JournalWindow;
import ui.windows.TasksWindow;

/**
 * In-game screen (ADR-0002): the {@link RoomView} plate, dialogue bubbles, the neighboring-rooms
 * panel, discovered-evidence tracking, the investigation sub-windows (Journal, Chat, Tasks, Help,
 * Case File, Pinboard), and the Watson/Deduce cost confirmations on terminal input. The shell stays
 * the listener seam; this screen owns the rendering and sub-window state.
 */
public class GameScreenController implements ScreenController {

  private static final org.slf4j.Logger logger =
      org.slf4j.LoggerFactory.getLogger(GameScreenController.class);

  private final MainController shell;
  private final StackPane container = new StackPane();
  private RoomView roomView;

  // Sub-windows, cached across opens; disposed on language switch.
  private JournalWindow journalWindow;
  private ChatWindow chatWindow;
  private TasksWindow tasksWindow;
  private HelpWindow helpWindow;
  private ui.windows.CaseFileWindow caseFileWindow;
  private PinboardController pinboardController;
  // Board content saved across a language-switch disposal (single player only; in
  // multiplayer the server restores the board via a state request).
  private common.dto.pinboard.PinboardStateDTO savedPinboardState;
  private int unreadChatCount = 0;

  private final java.util.Map<String, Boolean> taskStates = new java.util.HashMap<>();
  private final java.util.Set<String> discoveredSuspects = new java.util.HashSet<>();
  private final java.util.Set<String> discoveredObjects = new java.util.HashSet<>();

  // Last room received (SP engine push or MP RoomDescriptionDTO) — the live source for terminal
  // autocomplete domains (.scratch/terminal-autocomplete issue 03). Volatile: written by the
  // game thread, read on the FX thread while typing.
  private volatile RoomDescriptionDTO lastRoom;

  // Pending cost confirmations: an `ask watson`/`deduce` line is held here until the
  // player confirms the Insight Token cost (Y/N) on the next terminal line.
  private String pendingWatsonCommand;
  private String pendingDeduceCommand;

  // While a Final Exam is in progress the neighboring-Room move buttons are disabled so movement
  // can't bypass the exam (.scratch/exam-command-lockout). The engine authority also refuses the
  // move; this keeps the disabled affordance correct even if the panel is rebuilt mid-exam.
  private boolean neighborMovesDisabled;

  // Cooldown countdown shown while a command (contradict/combine) is locked after too many failed
  // attempts. The shell renders it in the right panel (under Status); this popup exists only to
  // float the same countdown ABOVE the Pinboard when the board is open (a separate Stage on top that
  // would otherwise hide the sidebar), mirroring the proven reveal-over-pinboard routing.
  private javafx.scene.control.Label cooldownStageLabel; // label inside the over-board popup
  private javafx.stage.Stage cooldownStage; // over-board popup, owned by the Pinboard stage
  private javafx.animation.Timeline cooldownTimeline;

  public GameScreenController(MainController shell) {
    this.shell = shell;
    this.roomView = new RoomView(shell);
    container.getChildren().add(roomView);
    this.inGameSettings = new InGameSettingsOverlay(shell, container);
  }

  @Override
  public Node getView() {
    return container;
  }

  @Override
  public boolean showsGameChrome() {
    return true;
  }

  /**
   * Shows (or retargets) the command-cooldown countdown and ticks it down once a second until
   * {@code cooldownUntil} (epoch millis), then tears it down. Driven by {@code
   * CommandCooldownUpdateDTO} on both the single-player and multiplayer paths, so it behaves
   * identically in either mode. When the Pinboard is open the countdown floats in a small popup
   * above the board (the board is a separate Stage on top of the game screen, so an in-window badge
   * would be hidden behind it — exactly why the terminal is invisible there); otherwise it shows as
   * a bottom-right badge on the game screen. Must be called on the FX thread.
   *
   * @param commandType the locked command, e.g. {@code "contradict"} or {@code "combine"}
   * @param cooldownUntil the epoch-millis instant the lock ends
   */
  public void showCommandCooldown(String commandType, long cooldownUntil) {
    // The shell's right-panel countdown (under Status) covers the normal case; this popup only
    // exists to float the countdown ABOVE the Pinboard while it is open. Nothing to do otherwise.
    if (pinboardController == null || !pinboardController.isShowing()) {
      hideCommandCooldown();
      return;
    }
    if (cooldownTimeline != null) {
      cooldownTimeline.stop();
    }
    String label =
        commandType == null || commandType.isBlank()
            ? "Locked"
            : Character.toUpperCase(commandType.charAt(0)) + commandType.substring(1);

    javafx.scene.control.Label target = ensureCooldownStage();

    // Tick immediately, then every second, recomputing from the target instant so the display stays
    // truthful even if a frame is dropped; tear the countdown down when the lock elapses.
    Runnable tick =
        () -> {
          long remainingMs = cooldownUntil - System.currentTimeMillis();
          if (remainingMs <= 0) {
            hideCommandCooldown();
            return;
          }
          long totalSeconds = (remainingMs + 999) / 1000; // ceil so it never shows 0:00 while locked
          long minutes = totalSeconds / 60;
          long seconds = totalSeconds % 60;
          target.setText(label + " locked  " + minutes + ":" + String.format("%02d", seconds));
          positionCooldownStageOverBoard();
        };
    tick.run();
    cooldownTimeline =
        new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), e -> tick.run()));
    cooldownTimeline.setCycleCount(javafx.animation.Timeline.INDEFINITE);
    cooldownTimeline.play();
  }

  /**
   * Creates (once) and shows the over-board countdown popup, returning its label. Mirrors {@link
   * #showRevealOverPinboard}: an undecorated, transparent, always-on-top {@link javafx.stage.Stage}
   * owned by the Pinboard stage so it stacks above the board and closes with it; the active theme is
   * installed on its Scene so the {@code .cooldown-badge} looked-up colours resolve.
   */
  private javafx.scene.control.Label ensureCooldownStage() {
    if (cooldownStage == null) {
      cooldownStageLabel = new javafx.scene.control.Label();
      cooldownStageLabel.getStyleClass().add("cooldown-badge");

      // Root sized to the badge itself (not the whole board) so the always-on-top popup only
      // occupies the corner and never blocks interaction with the board beneath it.
      StackPane root = new StackPane(cooldownStageLabel);
      root.setMouseTransparent(true);
      root.setStyle("-fx-background-color: transparent;");

      javafx.scene.Scene scene = new javafx.scene.Scene(root);
      scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
      ui.util.Theme.install(scene);

      cooldownStage = new javafx.stage.Stage();
      cooldownStage.initOwner(pinboardController.getStage());
      cooldownStage.initStyle(javafx.stage.StageStyle.TRANSPARENT);
      cooldownStage.setAlwaysOnTop(true);
      cooldownStage.setResizable(false);
      ui.util.AppIcon.applyTo(cooldownStage);
      cooldownStage.setScene(scene);
      // Pin to the board's bottom-right once measured, and keep pinned as its text width changes.
      cooldownStage.setOnShown(e -> positionCooldownStageOverBoard());
    }
    if (!cooldownStage.isShowing()) {
      cooldownStage.show();
    }
    positionCooldownStageOverBoard();
    return cooldownStageLabel;
  }

  /** Keeps the small over-board countdown popup pinned to the board's bottom-right corner. */
  private void positionCooldownStageOverBoard() {
    if (cooldownStage == null || pinboardController == null || !pinboardController.isShowing()) {
      return;
    }
    javafx.stage.Stage board = pinboardController.getStage();
    double margin = 18;
    cooldownStage.setX(board.getX() + board.getWidth() - cooldownStage.getWidth() - margin);
    cooldownStage.setY(board.getY() + board.getHeight() - cooldownStage.getHeight() - margin);
    cooldownStage.toFront();
  }

  private void dismissCooldownStage() {
    if (cooldownStage != null) {
      cooldownStage.close();
      cooldownStage = null;
      cooldownStageLabel = null;
    }
  }

  /** Removes the over-board cooldown popup and stops its ticker. Safe to call any time. */
  public void hideCommandCooldown() {
    if (cooldownTimeline != null) {
      cooldownTimeline.stop();
      cooldownTimeline = null;
    }
    dismissCooldownStage();
  }

  /**
   * Escape in a game (MENU_DESIGN #7): the shell has already closed any open bubble/sub-window
   * before delegating here, so with nothing left to close Escape toggles the pause menu. Never
   * falls through to an app exit.
   */
  @Override
  public boolean onEscape() {
    // The in-game Settings overlay steps back first (no dead-ends), then the pause toggle.
    if (isInGameSettingsShowing()) {
      hideInGameSettings();
      return true;
    }
    if (isPauseShowing()) {
      hidePauseMenu();
    } else {
      showPauseMenu();
    }
    return true;
  }

  /**
   * Re-shown after a detour — most importantly the pause-menu → Settings → Back round-trip, where
   * the light/dark theme may have been toggled while this screen was hidden. The CSS chrome
   * recolours itself, but the {@link RoomView} plate is a Palette-painted canvas that keeps its
   * pixels, so repaint it from the last room (DESIGN.md §8). A no-op until a room has loaded.
   */
  @Override
  public void onShow() {
    if (lastRoom != null) {
      updateRoomView(lastRoom);
    }
  }

  /**
   * Live theme toggle while the game is the active screen: same redraw as {@link #onShow()} so the
   * RoomView canvas flips to candlelight immediately rather than holding stale daylight pixels.
   */
  @Override
  public void onThemeChanged() {
    if (lastRoom != null) {
      updateRoomView(lastRoom);
    }
  }

  // ====================== Terminal input ======================

  @Override
  public boolean handleTerminalInput(String input) {
    // Anti-abuse: a typed "final exam" must not bypass the rapid-submission freeze that the toolbar
    // button enforces.
    if (shell.refuseFinalExamIfFrozen(input)) {
      return true;
    }
    if (pendingWatsonCommand != null) {
      if (input.equalsIgnoreCase("y") || input.equalsIgnoreCase("yes")) {
        shell.appendTerminalText(L10n.t("game.consultingWatson") + "\n");
        dispatch(pendingWatsonCommand);
      } else {
        shell.appendTerminalText(L10n.t("game.watsonCanceled") + "\n");
      }
      pendingWatsonCommand = null;
      return true;
    }
    if (pendingDeduceCommand != null) {
      if (input.equalsIgnoreCase("y") || input.equalsIgnoreCase("yes")) {
        shell.appendTerminalText(L10n.t("game.deducing") + "\n");
        dispatch(pendingDeduceCommand);
      } else {
        shell.appendTerminalText(L10n.t("game.deduceCanceled") + "\n");
      }
      pendingDeduceCommand = null;
      return true;
    }

    // Rewrite the author's assistant name to the canonical `ask watson` BEFORE the cost-confirmation
    // detection below, so a targeted `ask <helperName> <target>` still triggers the Insight Token
    // prompt. `ask watson` always keeps working; a no-op when no custom helper name is authored.
    input = shell.rewriteHelperAlias(input);
    String lowerInput = input.toLowerCase();
    if (lowerInput.startsWith("ask watson ") && lowerInput.substring(11).trim().length() > 0) {
      pendingWatsonCommand = input;
      shell.appendTerminalText("\n" + L10n.t("game.watsonCostPrompt") + "\n");
      shell.appendTerminalText(L10n.t("game.confirmYN") + "\n");
      return true;
    }
    if (lowerInput.startsWith("deduce ") && lowerInput.substring(7).trim().length() > 0) {
      pendingDeduceCommand = input;
      shell.appendTerminalText("\n" + L10n.t("game.deduceCostPrompt") + "\n");
      shell.appendTerminalText(L10n.t("game.confirmYN") + "\n");
      return true;
    }

    dispatch(input);
    return true;
  }

  private void dispatch(String command) {
    // The shared in-game command funnel for BOTH modes — so any user-initiated command (or room
    // change) snaps the terminal back to the newest line and re-engages auto-scroll identically in
    // single-player and multiplayer (.scratch/terminal-scroll-mp DEC-2).
    shell.repinTerminalToBottom();
    if (shell.isSinglePlayerMode()) {
      if (shell.getSinglePlayerGame() != null) {
        shell.getSinglePlayerGame().processCommand(command);
      }
    } else if (shell.getGameClient() != null) {
      shell.getGameClient().enqueueUserInput(command);
    }
  }

  // ====================== Terminal autocomplete ======================

  /**
   * Live completion context (.scratch/terminal-autocomplete issue 03), built from the same state
   * this screen renders: the cached {@link RoomDescriptionDTO} (objects, Suspects present, exit
   * directions), the Journal entry IDs, and the canonical command vocabulary shared by the SP and
   * MP parsers ({@code CommandFactorySinglePlayer} / {@code CommandFactoryClient}). SP and MP feed
   * the same provider; only the alias table differs.
   */
  @Override
  public ui.terminal.CompletionContext completionContext() {
    // A pending Watson/Deduce cost confirmation expects only y/n.
    if (pendingWatsonCommand != null || pendingDeduceCommand != null) {
      return ui.terminal.CompletionContext.builder()
          .bareOption("y", "y")
          .bareOption("n", "n")
          .build();
    }

    RoomDescriptionDTO room = lastRoom;
    List<String> objects = room != null ? room.getObjectNames() : List.of();
    List<String> suspects = suspectsPresent(room);
    List<String> exits =
        room != null
            ? room.getExits().keySet().stream()
                .map(direction -> direction.toLowerCase(java.util.Locale.ROOT))
                .sorted()
                .toList()
            : List.of();
    List<String> deduceTargets = new java.util.ArrayList<>(objects);
    deduceTargets.addAll(suspects);
    List<String> journalIds = journalEntryIds();

    ui.terminal.CompletionContext.Builder builder =
        ui.terminal.CompletionContext.builder()
            .command("look")
            .commandWithArgs("move", exits)
            .commandWithArgs("examine", objects)
            .commandWithArgs("question", suspects)
            .commandWithArgs("deduce", deduceTargets)
            .command("journal")
            .commandWithFreeArgs("journal add")
            .commandWithArgs("ask watson", getWatsonTargets())
            // The Contradiction verb both parsers execute: canonical "contradict <evidence> with
            // <suspect>", with "present" accepted as a back-compat alias
            // (.scratch/gui-contradict-syntax).
            .commandWithCompoundArgs("contradict", journalIds, "with", suspects, "present")
            // combine <noteId1> <noteId2> — two whitespace-separated Journal IDs.
            .commandWithCompoundArgs("combine", journalIds, null, journalIds)
            .command("tasks")
            .command("help")
            .command("exit");
    // Also offer the author's assistant name as an `ask` alias (rewritten to `ask watson` on
    // dispatch); `ask watson` stays available above. Skipped when unnamed or literally "watson".
    String helperName = shell.getActiveHelperName();
    if (helperName != null
        && !helperName.isBlank()
        && !helperName.trim().equalsIgnoreCase("watson")) {
      builder.commandWithArgs("ask " + helperName.trim().toLowerCase(), getWatsonTargets());
    }
    if (shell.isSinglePlayerMode()) {
      builder.command("final exam");
    } else {
      builder.command("final exam", "initiate final exam").commandWithFreeArgs("/setname");
    }
    return builder.build();
  }

  /** Suspects in the room: occupants minus other detectives, Watson, and the Host marker. */
  private static List<String> suspectsPresent(RoomDescriptionDTO room) {
    if (room == null || room.getOccupantNames() == null) {
      return List.of();
    }
    return room.getOccupantNames().stream()
        .filter(
            occupant ->
                !occupant.startsWith("Player")
                    && !occupant.equals("Dr. Watson")
                    && !occupant.equals("The Host"))
        .toList();
  }

  /** Journal entry IDs for present/combine (SP: engine getters; MP: client-cached entries). */
  private List<String> journalEntryIds() {
    List<common.dto.JournalEntryDTO> entries = null;
    if (shell.isSinglePlayerMode()) {
      if (shell.getSinglePlayerGame() != null
          && shell.getSinglePlayerGame().getGameContext() != null) {
        entries = shell.getSinglePlayerGame().getGameContext().getJournalEntries(null);
      }
    } else if (shell.getGameClient() != null) {
      entries = shell.getGameClient().getJournalEntries();
    }
    if (entries == null) {
      return List.of();
    }
    return new java.util.ArrayList<>(entries)
        .stream()
            .map(common.dto.JournalEntryDTO::getId)
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .toList();
  }

  // ====================== Session lifecycle ======================

  /**
   * Multiplayer game start: wires the Pinboard to the connection, mounts a fresh {@link RoomView}
   * (no stale state from a previous Game Session), and shows this screen.
   */
  public void onEnterGame(RoomDescriptionDTO initialRoom) {
    if (shell.getGameClient() != null) {
      initializePinboardNetworking();
      shell.getGameClient().sendPinboardStateRequest();
    }
    roomView = new RoomView(shell);
    container.getChildren().setAll(roomView);
    shell.showScreen(this);
    updateRoomView(initialRoom);
  }

  /** Clears per-session investigation state ahead of a fresh SP run or MP connection. */
  public void resetForNewSession() {
    taskStates.clear();
    discoveredSuspects.clear();
    discoveredObjects.clear();
    lastRoom = null;
    savedPinboardState = null;
    hideCommandCooldown();
    if (pinboardController != null) {
      pinboardController.reset();
    }
  }

  /** Clears per-case content when returning to Case selection or the main menu. */
  public void resetCaseContent() {
    taskStates.clear();
    discoveredSuspects.clear();
    discoveredObjects.clear();
    lastRoom = null;
    savedPinboardState = null;
    if (pinboardController != null) {
      pinboardController.resetContent();
    }
  }

  public void clearTaskStates() {
    taskStates.clear();
  }

  /**
   * The current Pinboard state for the Completed-Case Record: the live board if the Pinboard window
   * is open, otherwise the last snapshot taken when it was closed ({@code savedPinboardState}). May
   * be {@code null} if the player never opened the Pinboard.
   */
  public common.dto.pinboard.PinboardStateDTO getCurrentPinboardState() {
    return pinboardController != null ? pinboardController.getState() : savedPinboardState;
  }

  /**
   * Enters Review Session presentation for a solved case (gui-review-enter-case): seeds the in-game
   * Pinboard from the saved board (applied when the player opens it), shows the "Reviewing — read
   * only" badge, and prints the saved solve's summary (rank / deductions / score / date) to the
   * transcript as an accessible info panel. The engine has already been flipped to review mode by
   * the shell, so gameplay mutations + the Final Exam are refused and nothing re-writes the record.
   */
  public void enterReviewMode(common.dto.save.CompletedCaseRecord record) {
    savedPinboardState = record != null ? record.getPinboard() : null;
    if (pinboardController != null && savedPinboardState != null) {
      pinboardController.applyState(savedPinboardState);
    }
    shell.updateStatus(L10n.t("review.badge"));
    shell.appendTerminalText(reviewSummary(record));
  }

  /**
   * The saved solve's at-a-glance summary, reusing the read-only {@link ui.review.CaseReviewModel}.
   */
  private static String reviewSummary(common.dto.save.CompletedCaseRecord record) {
    ui.review.CaseReviewModel m = new ui.review.CaseReviewModel(record);
    StringBuilder sb = new StringBuilder(L10n.t("review.badge")).append("\n");
    if (m.hasDetail()) {
      sb.append(L10n.t("review.summary.rank", reviewText(m.getRankName()))).append("\n");
      sb.append(L10n.t("review.summary.deductions", reviewText(m.getDeductionsUsed())))
          .append("\n");
      sb.append(
              L10n.t(
                  "review.summary.score",
                  reviewText(m.getFinalExamScore()),
                  reviewText(m.getFinalExamTotal())))
          .append("\n");
      sb.append(L10n.t("review.summary.date", reviewDate(m)));
    }
    return sb.append("\n").toString();
  }

  private static String reviewText(Object value) {
    return value == null ? L10n.t("review.unknown") : String.valueOf(value);
  }

  private static String reviewDate(ui.review.CaseReviewModel model) {
    Long epoch = model.getDateSolvedEpochMillis();
    if (epoch == null) {
      return L10n.t("review.unknown");
    }
    return java.text.DateFormat.getDateInstance(
            java.text.DateFormat.MEDIUM, new java.util.Locale(L10n.language()))
        .format(new java.util.Date(epoch));
  }

  // ====================== Room rendering ======================

  public void updateRoomView(RoomDescriptionDTO roomDescription) {
    lastRoom = roomDescription;
    if (roomView != null) {
      Platform.runLater(
          () -> {
            if (roomDescription != null) {
              roomView.loadRoom(roomDescription);
              updateRightPanel(roomDescription);
              // Rooms display by their per-language Display Name everywhere (falls back to the
              // Universal name); .scratch/gui-localized-case-names.
              shell.updateStatus(L10n.t("status.currentRoom", roomDescription.getDisplayName()));

              // Add discovered suspects to list (persists even if pinboard closed)
              if (roomDescription.getOccupantNames() != null) {
                for (String occupant : roomDescription.getOccupantNames()) {
                  if (!occupant.startsWith("Player")
                      && !occupant.equals("Dr. Watson")
                      && !occupant.equals("The Host")) {
                    discoveredSuspects.add(occupant);
                    // Also push to controller if active
                    if (pinboardController != null) {
                      pinboardController.addSuspectName(occupant);
                    }
                  }
                }
              }

              // Add discovered objects to list
              if (roomDescription.getObjectNames() != null) {
                discoveredObjects.addAll(roomDescription.getObjectNames());
              }
            } else {
              roomView.clear();
              updateRightPanel(null);
              shell.updateStatus(L10n.t("status.noActiveGame"));
            }
          });
    }
  }

  public void refreshRoomView() {
    if (shell.isSinglePlayerMode()
        && shell.getSinglePlayerGame() != null
        && shell.getSinglePlayerGame().getGameContext() != null) {
      singleplayer.GameContextSinglePlayer context = shell.getSinglePlayerGame().getGameContext();
      Core.Room currentRoom = context.getCurrentRoomForPlayer(null);
      if (currentRoom != null) {
        RoomDescriptionDTO dto = context.createRoomDescriptionDTO(currentRoom, null);
        updateRoomView(dto);
      }
    }
  }

  private void updateRightPanel(RoomDescriptionDTO roomDescription) {
    shell.getNeighboringRoomsContainer().getChildren().clear();

    if (roomDescription == null || roomDescription.getExits() == null) {
      return;
    }

    for (java.util.Map.Entry<String, String> entry : roomDescription.getExits().entrySet()) {
      String direction = entry.getKey();
      String roomName = entry.getValue();
      String buttonText = direction + ": " + roomName;

      Button roomButton = new Button(buttonText);
      roomButton.setPrefWidth(Double.MAX_VALUE); // Make buttons fill the width
      roomButton.setDisable(neighborMovesDisabled); // locked out during the Final Exam

      roomButton.setOnAction(
          event -> {
            // Single click (or keyboard activation) moves in this direction.
            String command = "move " + direction;
            shell.sendCommand(command);
          });

      Tooltip tooltip = new Tooltip(L10n.t("sidebar.moveTooltip", roomName));
      roomButton.setTooltip(tooltip);

      shell.getNeighboringRoomsContainer().getChildren().add(roomButton);
    }
  }

  /**
   * Locks or unlocks the neighboring-Room move buttons while a Final Exam is in progress
   * (.scratch/exam-command-lockout). Applies to the buttons already on screen and is remembered so
   * a room rebuild during an active exam keeps them disabled.
   */
  public void setNeighborMovesDisabled(boolean disabled) {
    this.neighborMovesDisabled = disabled;
    Platform.runLater(
        () ->
            shell
                .getNeighboringRoomsContainer()
                .getChildren()
                .forEach(node -> node.setDisable(disabled)));
  }

  public List<String> getWatsonTargets() {
    if (shell.isSinglePlayerMode()
        && shell.getSinglePlayerGame() != null
        && shell.getSinglePlayerGame().getGameContext() != null) {
      singleplayer.GameContextSinglePlayer context = shell.getSinglePlayerGame().getGameContext();
      if (context.getCaseFile() != null) {
        // Collect all suspects and objects from the entire case
        java.util.Set<String> allTargets = new java.util.HashSet<>();

        // Add suspects
        if (context.getCaseFile().getSuspects() != null) {
          context.getCaseFile().getSuspects().forEach(s -> allTargets.add(s.getName()));
        }

        // Add objects from all rooms
        if (context.getCaseFile().getRooms() != null) {
          context
              .getCaseFile()
              .getRooms()
              .forEach(
                  room -> {
                    if (room.getObjects() != null) {
                      room.getObjects().forEach(obj -> allTargets.add(obj.getName()));
                    }
                  });
        }

        // Sort
        List<String> sorted = new java.util.ArrayList<>(allTargets);
        java.util.Collections.sort(sorted);
        return sorted;
      }
    }

    // Multiplayer or fallback: return discovered items
    java.util.Set<String> allDiscovered = new java.util.HashSet<>();
    allDiscovered.addAll(discoveredSuspects);
    allDiscovered.addAll(discoveredObjects);
    List<String> sorted = new java.util.ArrayList<>(allDiscovered);
    java.util.Collections.sort(sorted);
    return sorted;
  }

  // ====================== Dialogue bubble ======================

  /**
   * Builds the result popup as a framed plate (DESIGN.md §5): a single VBox of vertically-flowed,
   * NON-overlapping rows — title, an ochre rule, the optional subject image in its own bounded
   * band, the body, then the close affordance. No StackPane / absolute layout, so nothing is ever
   * drawn on top of the words.
   *
   * <p>The body must ALWAYS show in full (.scratch/gui-popup-text-wrap): the content {@code Label}
   * wraps ({@code wrapText}) to the card width, and the bubble has NO bound max height (the caller
   * binds max WIDTH only). So the card stretches vertically to fit however many lines the statement
   * needs — it grows dynamically, never clips with "…", and never scrolls. Static so it is
   * unit-testable without the full controller (the body-truncation regression seam).
   */
  static javafx.scene.layout.VBox buildDialogueBubble(
      String modifier,
      String title,
      javafx.scene.image.Image subjectImage,
      String bodyText,
      String closeLabel,
      Runnable onClose) {
    javafx.scene.layout.VBox bubble = new javafx.scene.layout.VBox(10);
    bubble.setId("dialogueBubble");
    bubble.setFillWidth(true);
    bubble.setPadding(new Insets(15));
    bubble.setAlignment(Pos.TOP_LEFT);
    // Hug the content height: a VBox's default max height is unbounded, which would let the
    // centering
    // StackPane stretch the card to fill the whole room pane (empty vellum below the text). Capping
    // max height to the PREFERRED size makes the card size to its rows and stretch only as the
    // wrapped body needs (.scratch/gui-popup-text-wrap) — the caller binds max WIDTH separately.
    bubble.setMaxHeight(Region.USE_PREF_SIZE);
    bubble.getStyleClass().addAll("dialogue-bubble", "dialogue-bubble--" + modifier);

    // Row 1 — title.
    Label titleLabel = new Label(title);
    titleLabel.setWrapText(true);
    titleLabel
        .getStyleClass()
        .addAll("dialogue-bubble-title", "dialogue-bubble-title--" + modifier);

    // Row 2 — a thin ochre rule under the title (DESIGN.md §5 dialogs).
    javafx.scene.layout.Region rule = new javafx.scene.layout.Region();
    rule.getStyleClass().add("dialogue-bubble-rule");

    bubble.getChildren().addAll(titleLabel, rule);

    // Row 3 — the subject image, in its OWN band, bounded and centered, only when the event
    // actually
    // has one (examine an object / question a suspect). Omitted otherwise so there is no empty gap.
    if (subjectImage != null) {
      javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView(subjectImage);
      imageView.setPreserveRatio(true);
      imageView.setFitWidth(200);
      imageView.setFitHeight(160);
      javafx.scene.layout.HBox imageRow = new javafx.scene.layout.HBox(imageView);
      imageRow.setAlignment(Pos.CENTER);
      imageRow.getStyleClass().add("dialogue-bubble-image");
      bubble.getChildren().add(imageRow);
    }

    // Row 4 — the body. Wraps to the card width and is added directly (no scroll): with no bound
    // max
    // height on the bubble, the card stretches vertically to fit every wrapped line, so a long
    // statement is shown in FULL (.scratch/gui-popup-text-wrap).
    Label contentLabel = new Label(bodyText);
    contentLabel.setWrapText(true);
    contentLabel.setMaxWidth(Double.MAX_VALUE);
    contentLabel.getStyleClass().add("dialogue-bubble-content");
    bubble.getChildren().add(contentLabel);

    // Row 5 — close, pinned to the card's bottom-right so it is never covered.
    Button closeButton = new Button(closeLabel);
    closeButton.setOnAction(e -> onClose.run());
    javafx.scene.layout.HBox closeRow = new javafx.scene.layout.HBox(closeButton);
    closeRow.setAlignment(Pos.CENTER_RIGHT);
    bubble.getChildren().add(closeRow);

    return bubble;
  }

  public void showDialogueBubble(common.dto.DialogueEventDTO event) {
    Platform.runLater(
        () -> {
          // Only one reveal is ever visible at a time: drop any floating pinboard popup left over
          // from a prior reveal before deciding where this one goes.
          dismissRevealPopup();

          // A reveal (contradiction / combine / deduction result) triggered while the Pinboard is
          // open would be added to THIS window's container and hidden behind the Pinboard — a
          // separate Stage that sits on top. viewOrder/toFront only reorder within a window, not
          // across windows. So while the board is open, float the reveal in a top-level popup that
          // stacks above it instead (.scratch/gui-pinboard-reveal-float); otherwise keep the
          // normal in-window bubble unchanged.
          // Only float the reveal over the board when the board is the window the player is actually
          // looking at. If the Pinboard is open but in the BACKGROUND (the player is at the terminal
          // in the main window), an owned always-on-top popup would drag the board to the front and
          // show the statement inside it — so in that case keep the normal in-window bubble.
          if (pinboardController != null
              && pinboardController.isShowing()
              && pinboardController.getStage().isFocused()) {
            // Clear any stale in-window bubble so it isn't left behind the board.
            container
                .getChildren()
                .removeIf(node -> node.getId() != null && node.getId().equals("dialogueBubble"));
            shell.setResultPopupShowing(false);
            showRevealOverPinboard(event);
            return;
          }

          // Remove existing bubble if any
          container
              .getChildren()
              .removeIf(node -> node.getId() != null && node.getId().equals("dialogueBubble"));

          // Styling is class-based; modifier is derived from the event type.
          String modifier =
              event.getType() != null ? event.getType().name().toLowerCase() : "narrative";

          // The speaker name is UI-language framing, not case content: the engine labels a Watson
          // hint "Dr. Watson" (one string for all clients), so the client renders its own localized
          // speaker name here (.scratch/gui-localized-watson-hints).
          String title =
              event.getType() == common.dto.DialogueType.WATSON
                  ? resolveHelperSpeaker()
                  : event.getTitle();

          javafx.scene.layout.VBox bubble =
              buildDialogueBubble(
                  modifier,
                  title,
                  resolveDialogueImage(event),
                  event.getText(),
                  L10n.t("common.close"),
                  this::removeDialogueBubble);

          // Placement (.scratch/responsive-resizing issue 02 — percent-bound width, 8px-scale
          // floor): a centered plate in normal play; while a tutorial runs, pinned to the TOP band
          // so it never collides with the tutorial guidance card (pinned BOTTOM_CENTER in
          // MainController). The room pane is short (top ~70% of a vertical split), so the
          // guidance card also sheds its own image while this popup is up (see
          // setResultPopupShowing) — together that keeps the two in clearly separate bands.
          // Max WIDTH only is bound (the text wraps to it); the HEIGHT stays content-driven so the
          // card stretches vertically to fit the whole statement (.scratch/gui-popup-text-wrap) —
          // no max-height clamp, no clipping, no scroll.
          if (shell.isTutorialActive()) {
            ui.util.ViewportSizing.bindMaxWidthToViewport(bubble, container, 0.6, 320);
            StackPane.setAlignment(bubble, Pos.TOP_CENTER);
            StackPane.setMargin(bubble, new Insets(10, 0, 0, 0));
            shell.setResultPopupShowing(true);
          } else {
            ui.util.ViewportSizing.bindMaxWidthToViewport(bubble, container, 0.6, 320);
            StackPane.setAlignment(bubble, Pos.CENTER);
            StackPane.setMargin(bubble, Insets.EMPTY);
          }

          container.getChildren().add(bubble);
          // The result popup must read clearly ABOVE the room plate and every suspect/object
          // sprite (which live in the RoomView below it in `container`). A negative viewOrder
          // renders the bubble's whole subtree in front of RoomView (viewOrder 0) regardless of
          // child-list order or any later room re-render; toFront() is belt-and-suspenders. Its
          // opaque vellum + 2px contour backing (.dialogue-bubble, DESIGN.md §5, no shadow) keeps
          // the text legible whatever art sits behind it.
          bubble.setViewOrder(-1);
          bubble.toFront();

          // Ink settling onto the page (DESIGN.md §6): fade in with a small upward translate.
          ui.util.Motion.settleIn(bubble);
        });
  }

  /** Removes the result popup and lets the tutorial guidance card restore its own image. */
  private void removeDialogueBubble() {
    container.getChildren().removeIf(n -> "dialogueBubble".equals(n.getId()));
    shell.setResultPopupShowing(false);
  }

  /**
   * The speaker name for an assistant (Watson) dialogue bubble: the case's author-defined helper
   * name when it authored one, otherwise the localized default ({@code game.watsonSpeaker}). Keeps
   * the localized "Dr. Watson" for cases that don't rename the helper, while honouring a custom name
   * (a single string across all UI languages) for cases that do.
   */
  private String resolveHelperSpeaker() {
    String authored = shell == null ? null : shell.getActiveHelperName();
    return authored != null ? authored : L10n.t("game.watsonSpeaker");
  }

  // The floating reveal popup shown above the Pinboard window, or null when none is up. Owned by
  // the Pinboard stage so it stacks above it and is auto-closed if the board closes.
  private javafx.stage.Stage revealPopupStage;

  /**
   * Floats a reveal (contradiction success / combine result / new deduction) in a lightweight
   * top-level popup that renders ABOVE the Pinboard window, so it is seen immediately without the
   * player moving anything (.scratch/gui-pinboard-reveal-float). The popup is an undecorated,
   * transparent {@link javafx.stage.Stage} owned by (and centered over) the Pinboard stage — owning
   * it makes it stack above the board and be dismissed with it — and reuses the exact {@link
   * #buildDialogueBubble} content/styling as the in-window bubble, including the same Close action.
   * Only one reveal is visible at a time: any prior popup is dismissed first (by the caller). Made
   * package-visible so the routing can be exercised headlessly without opening a real window.
   */
  void showRevealOverPinboard(common.dto.DialogueEventDTO event) {
    dismissRevealPopup();
    javafx.stage.Stage owner = pinboardController.getStage();

    // Same modifier/title derivation as the in-window bubble (localized Watson speaker included).
    String modifier = event.getType() != null ? event.getType().name().toLowerCase() : "narrative";
    String title =
        event.getType() == common.dto.DialogueType.WATSON
            ? resolveHelperSpeaker()
            : event.getTitle();

    VBox bubble =
        buildDialogueBubble(
            modifier,
            title,
            resolveDialogueImage(event),
            event.getText(),
            L10n.t("common.close"),
            this::dismissRevealPopup);
    // Width-bound only; height stays content-driven so the card stretches to show the whole reveal
    // (mirrors the in-window bubble, .scratch/gui-popup-text-wrap).
    bubble.setMaxWidth(480);

    StackPane root = new StackPane(bubble);
    StackPane.setAlignment(bubble, Pos.CENTER);
    root.setPadding(new Insets(20));
    // Undecorated transparent stage: only the vellum card is visible, floating over the board.
    root.setStyle("-fx-background-color: transparent;");

    javafx.scene.Scene scene = new javafx.scene.Scene(root);
    scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
    // The popup is its own Scene, unreached by the main window's sheets — install the active theme
    // so the .dialogue-bubble styling (looked-up -sl-* colours) resolves (as PinboardController and
    // DESIGN.md §8 require for stand-alone stages).
    ui.util.Theme.install(scene);

    javafx.stage.Stage popup = new javafx.stage.Stage();
    popup.initOwner(owner);
    popup.initStyle(javafx.stage.StageStyle.TRANSPARENT);
    popup.setAlwaysOnTop(true);
    ui.util.AppIcon.applyTo(popup);
    popup.setScene(scene);
    revealPopupStage = popup;

    // Escape dismisses the reveal, matching the Close button.
    scene.addEventFilter(
        javafx.scene.input.KeyEvent.KEY_PRESSED,
        ev -> {
          if (ev.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
            dismissRevealPopup();
            ev.consume();
          }
        });

    // Center over the board once the popup has a measured size, then bring it forward.
    popup.setOnShown(
        e -> {
          popup.setX(owner.getX() + (owner.getWidth() - popup.getWidth()) / 2);
          popup.setY(owner.getY() + (owner.getHeight() - popup.getHeight()) / 2);
          popup.toFront();
          popup.requestFocus();
        });

    popup.show();
    // Ink settling onto the page (DESIGN.md §6), same entrance as the in-window bubble.
    ui.util.Motion.settleIn(bubble);
  }

  /** Closes and forgets the floating reveal popup, returning focus to the board. */
  private void dismissRevealPopup() {
    if (revealPopupStage != null) {
      revealPopupStage.close();
      revealPopupStage = null;
    }
  }

  /**
   * The illustrative image for a result popup, or {@code null} when the event has no subject art
   * (so the popup simply omits the image band). Examining an object shows that object's image;
   * questioning a suspect shows the suspect's. Resolved through the live single-player context so a
   * non-object/non-suspect event (deduction, combine, narrative) yields no image.
   */
  private javafx.scene.image.Image resolveDialogueImage(common.dto.DialogueEventDTO event) {
    if (event == null || event.getType() == null || event.getTitle() == null) {
      return null;
    }
    if (!shell.isSinglePlayerMode() || shell.getSinglePlayerGame() == null) {
      return null;
    }
    singleplayer.GameContextSinglePlayer ctx = shell.getSinglePlayerGame().getGameContext();
    if (ctx == null) {
      return null;
    }
    switch (event.getType()) {
      case EXAMINE -> {
        String marker = "Examining: ";
        if (!event.getTitle().startsWith(marker)) {
          return null;
        }
        String objectName = event.getTitle().substring(marker.length()).trim();
        Core.Room room = ctx.getCurrentRoomForPlayer(null);
        Core.GameObject obj = room != null ? room.getObject(objectName) : null;
        return obj != null ? shell.getImageManager().getObjectImage(obj) : null;
      }
      case SUSPECT -> {
        Core.Suspect suspect =
            ctx.getAllSuspects().stream()
                .filter(s -> s.getName().equalsIgnoreCase(event.getTitle()))
                .findFirst()
                .orElse(null);
        return suspect != null ? shell.getImageManager().getSuspectImage(suspect) : null;
      }
      default -> {
        return null;
      }
    }
  }

  public void showRoomResponse(String targetName, String response) {
    if (roomView != null) {
      // Adapt to DialogueEventDTO for unified UI
      showDialogueBubble(
          new common.dto.DialogueEventDTO(targetName, response, common.dto.DialogueType.NARRATIVE));
    }
  }

  // ====================== Sub-windows ======================

  /**
   * @return true if any sub-window or dialogue bubble was actually open (and is now closed).
   */
  public boolean closeAllSubWindows() {
    boolean closedAny = false;
    if (journalWindow != null && journalWindow.isShowing()) {
      journalWindow.close();
      closedAny = true;
    }
    if (chatWindow != null && chatWindow.isShowing()) {
      chatWindow.close();
      closedAny = true;
    }
    if (tasksWindow != null && tasksWindow.isShowing()) {
      tasksWindow.close();
      closedAny = true;
    }
    if (helpWindow != null && helpWindow.isShowing()) {
      helpWindow.close();
      closedAny = true;
    }
    if (caseFileWindow != null && caseFileWindow.isShowing()) {
      caseFileWindow.close();
      closedAny = true;
    }
    if (pinboardController != null && pinboardController.isShowing()) {
      pinboardController.close();
      closedAny = true;
    }
    // Remove dialogue bubble if present (and let the tutorial guidance restore its image).
    boolean removedBubble =
        container
            .getChildren()
            .removeIf(node -> node.getId() != null && node.getId().equals("dialogueBubble"));
    if (removedBubble) {
      shell.setResultPopupShowing(false);
    }
    closedAny |= removedBubble;
    return closedAny;
  }

  /**
   * Tears down EVERY transient in-game surface (.scratch/gui-tutorial-exit-cleanup): all
   * sub-windows + the statement/dialogue popup ({@link #closeAllSubWindows()}), plus the pause and
   * in-game Settings overlays. Called on tutorial start/exit and when a game session ends, so
   * nothing stale carries over. (The tutorial overlay itself is shell-owned and torn down by {@code
   * MainController}.)
   */
  public void closeAllTransientSurfaces() {
    closeAllSubWindows();
    hidePauseMenu();
    hideInGameSettings();
  }

  // ====================== Pause menu (MENU_DESIGN #7) ======================

  private StackPane pauseOverlay;

  public boolean isPauseShowing() {
    return pauseOverlay != null && container.getChildren().contains(pauseOverlay);
  }

  /**
   * Raises the pause menu: a centred dossier card over a warm, dimmed (not black, DESIGN.md §1)
   * veil across the game plate. Resume / Settings / Journal / Quit to menu; clicking the veil
   * resumes.
   */
  public void showPauseMenu() {
    if (isPauseShowing()) {
      return;
    }
    Region scrim = new Region();
    scrim.getStyleClass().add("pause-scrim");
    scrim.setOnMouseClicked(event -> hidePauseMenu());

    Label title = new Label(L10n.t("pause.title"));
    title.getStyleClass().add("pause-title");

    Button resume = pausePlate("pause.resume", true, this::hidePauseMenu);
    Button settings =
        pausePlate(
            "pause.settings",
            false,
            () -> {
              hidePauseMenu();
              shell.showSettingsFromPause();
            });
    Button journal =
        pausePlate(
            "pause.journal",
            false,
            () -> {
              hidePauseMenu();
              shell.openJournalWindow();
            });
    Button quit =
        pausePlate(
            "pause.quit",
            false,
            () -> {
              hidePauseMenu();
              shell.returnToMainMenu();
            });

    VBox card = new VBox(14, title, resume, settings, journal, quit);
    card.getStyleClass().add("pause-card");
    card.setAlignment(Pos.CENTER);
    card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

    pauseOverlay = new StackPane(scrim, card);
    StackPane.setAlignment(card, Pos.CENTER);
    pauseOverlay.setViewOrder(-100); // above the room view + any dialogue bubble
    container.getChildren().add(pauseOverlay);
    ui.util.Motion.fadeIn(pauseOverlay, ui.util.Motion.SCREEN).play();
    Platform.runLater(resume::requestFocus);
  }

  /** Dismisses the pause menu (Resume / Escape / clicking the veil). */
  public void hidePauseMenu() {
    if (pauseOverlay != null) {
      container.getChildren().remove(pauseOverlay);
      pauseOverlay = null;
    }
  }

  private Button pausePlate(String key, boolean primary, Runnable action) {
    Button button = new Button(L10n.t(key));
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

  // ====================== In-game Settings overlay (MENU_DESIGN #6/#7) ======================

  // The trimmed Settings dossier is a shared overlay (InGameSettingsOverlay) so the exam paper can
  // raise the identical card over its own container (.scratch/gui-ingame-settings). Assigned in the
  // constructor — `shell` is not set until the constructor body runs, so a field initializer would
  // capture a null shell.
  private final InGameSettingsOverlay inGameSettings;

  public boolean isInGameSettingsShowing() {
    return inGameSettings.isShowing();
  }

  /**
   * Raises the TRIMMED Settings dossier during the game (.scratch/gui-ingame-settings). Closes the
   * pause menu first so only one overlay shows at a time; the card itself is built and mounted by
   * the shared {@link InGameSettingsOverlay}.
   */
  public void showInGameSettings() {
    hidePauseMenu(); // one overlay at a time
    inGameSettings.show();
  }

  /** Dismisses the in-game Settings overlay (Close / Escape / clicking the veil). */
  public void hideInGameSettings() {
    inGameSettings.hide();
  }

  /** Cached sub-windows hold labels in the old language; rebuild them lazily on next open. */
  public void disposeCachedSubWindows() {
    closeAllSubWindows();
    journalWindow = null;
    chatWindow = null;
    tasksWindow = null;
    helpWindow = null;
    caseFileWindow = null;
    if (pinboardController != null) {
      savedPinboardState = pinboardController.getState();
      pinboardController.close();
      pinboardController = null;
    }
  }

  public void openCaseFileWindow() {
    // Rebuild from the CURRENT active case on every open. A cached window keeps the tabs it was
    // built with, so after switching cases it would show the PREVIOUS case's victim/overview/
    // suspect profiles (.scratch/casefile-stale-on-case-switch). CaseFileWindow reads the live
    // block in its constructor, so recreating it repopulates from getActiveCaseFileBlock().
    if (caseFileWindow != null) {
      caseFileWindow.close();
    }
    caseFileWindow = new ui.windows.CaseFileWindow(shell);
    caseFileWindow.show();
  }

  public void openTasksWindow() {
    if (tasksWindow == null) {
      tasksWindow = new TasksWindow(shell);
    }

    // Dynamically load tasks based on the current game context
    if (shell.isSinglePlayerMode() && shell.getSinglePlayerGame() != null) {
      List<String> tasks = shell.getSinglePlayerGame().getCurrentCaseTasks();
      tasksWindow.loadTasks(tasks, taskStates);
    } else if (!shell.isSinglePlayerMode() && shell.getGameClient() != null) {
      List<String> tasks = shell.getGameClient().getCurrentCaseTasks();
      tasksWindow.loadTasks(tasks, taskStates);
    }

    tasksWindow.show();
  }

  public void openJournalWindow() {
    if (journalWindow == null) {
      journalWindow = new JournalWindow(shell);
    }
    loadJournalEntries();
    // Showing is intentional ONLY here (Journal button / explicit open) — never from a content
    // refresh, so commands like examine update the journal silently. See refreshJournalWindow.
    journalWindow.show();
  }

  /**
   * Repopulates the journal window's entries from the active game context <b>without opening
   * it</b>. A visible window updates live; a closed (or freshly created) one is simply repopulated
   * for its next open. No-ops until the window exists.
   */
  private void loadJournalEntries() {
    if (journalWindow == null) {
      return;
    }
    if (shell.isSinglePlayerMode() && shell.getSinglePlayerGame() != null) {
      List<common.dto.JournalEntryDTO> entries =
          shell.getSinglePlayerGame().getGameContext().getJournalEntries(null);
      if (entries != null) {
        journalWindow.setEntries(entries);
      }
    } else if (!shell.isSinglePlayerMode() && shell.getGameClient() != null) {
      List<common.dto.JournalEntryDTO> entries = shell.getGameClient().getJournalEntries();
      if (entries != null) {
        journalWindow.setEntries(entries);
      }
    }
  }

  public void openChatWindow() {
    if (chatWindow == null) {
      chatWindow = new ChatWindow(shell);
    }

    // Dynamically load chat history
    if (!shell.isSinglePlayerMode() && shell.getGameClient() != null) {
      List<common.dto.ChatMessage> history = shell.getGameClient().getChatHistory();
      if (history != null) {
        chatWindow.loadHistory(history);
      }
    }

    chatWindow.show();
    unreadChatCount = 0;
    shell.setUnreadChatBadge(unreadChatCount);
  }

  public void openHelpWindow() {
    if (helpWindow == null) {
      helpWindow = new HelpWindow();
    }
    helpWindow.show();
  }

  public void openPinboardWindow() {
    if (pinboardController == null) {
      logger.debug("Initializing Pinboard Controller...");
      pinboardController = new PinboardController();
      // Pinboard notes follow the "Reading text size" slider (.scratch/gui-typography-readability).
      pinboardController.applyReadingTextScale(shell.getReadingTextScale());
      // In a Review Session the board is strictly read-only — set this BEFORE applyState so the
      // restored item/link nodes are wired non-editable at creation (gui-review-enter-case).
      pinboardController.setReadOnly(shell.isReviewModeActive());
      // "Sync journal" is meaningless offline — hide it entirely in single player.
      pinboardController.setMultiplayer(!shell.isSinglePlayerMode());
      if (!shell.isSinglePlayerMode() && shell.getGameClient() != null) {
        // Rebuilt after a language switch mid-Game Session: re-wire the connection and
        // let the server restore the board.
        initializePinboardNetworking();
        shell.getGameClient().sendPinboardStateRequest();
      } else if (savedPinboardState != null) {
        pinboardController.applyState(savedPinboardState);
      }
      savedPinboardState = null;
    }

    // Ensure command handler is set if opened in Single Player (where networking
    // init might differ)
    pinboardController.setCommandHandler(shell::sendCommand);
    // Drawing a link advances the pinboard tutorial's link step (.scratch/gui-pinboard-tutorial);
    // a no-op outside a tutorial.
    pinboardController.setOnLinkCreated(shell::notifyTutorialPinboardLinked);

    // Load existing clues if just opening for first time but game has progressed
    syncPinboardData();

    pinboardController.setOnSyncRequest(this::syncPinboardData);
    pinboardController.show();
  }

  /**
   * Applies a single-player pinboard sync (e.g. the red contradiction link) to the live board,
   * mirroring the multiplayer incoming-update listener. Marshalled to the FX thread and a no-op
   * when the board is not open (there is no persisted SP board to replay into).
   */
  public void applyPinboardUpdate(common.dto.pinboard.PinboardUpdateDTO update) {
    if (update == null) {
      return;
    }
    Platform.runLater(
        () -> {
          if (pinboardController != null) {
            pinboardController.applyUpdate(update);
          }
        });
  }

  private void syncPinboardData() {
    if (pinboardController == null) return;

    if (shell.isSinglePlayerMode() && shell.getSinglePlayerGame() != null) {
      List<common.dto.JournalEntryDTO> entries =
          shell.getSinglePlayerGame().getGameContext().getJournalEntries(null);
      // Use the new bulk setter for full sync
      if (entries != null) {
        // DEFENSIVE COPY: Avoid ConcurrentModificationException if background thread
        // updates list
        pinboardController.setJournalEntries(new java.util.ArrayList<>(entries));
      }
    } else if (!shell.isSinglePlayerMode() && shell.getGameClient() != null) {
      List<common.dto.JournalEntryDTO> entries = shell.getGameClient().getJournalEntries();
      if (entries != null) {
        // DEFENSIVE COPY: Avoid ConcurrentModificationException
        pinboardController.setJournalEntries(new java.util.ArrayList<>(entries));
      }
    }

    // Sync discovered suspects
    for (String suspect : discoveredSuspects) {
      pinboardController.addSuspectName(suspect);
    }
  }

  private void initializePinboardNetworking() {
    if (pinboardController == null) {
      pinboardController = new PinboardController();
      pinboardController.applyReadingTextScale(shell.getReadingTextScale());
      pinboardController.setMultiplayer(true); // this path only runs for a networked session
    }

    // 1. Outgoing updates: Pinboard -> Server
    pinboardController.setOnUpdateCallback(
        update -> {
          if (shell.getGameClient() != null) {
            shell.getGameClient().sendPinboardUpdate(update);
          }
        });

    // 1b. Commands from Pinboard -> Game (e.g. Contradict)
    pinboardController.setCommandHandler(shell::sendCommand);

    // 2. Incoming updates: Server -> Pinboard (Wrapped in runLater to be safe)
    shell
        .getGameClient()
        .setPinboardUpdateListener(
            update -> {
              if (pinboardController != null) {
                Platform.runLater(() -> pinboardController.applyUpdate(update));
              }
            });

    // 3. Initial State: Server -> Pinboard (Wrapped in runLater to be safe)
    shell
        .getGameClient()
        .setPinboardStateListener(
            state -> {
              if (pinboardController != null) {
                Platform.runLater(() -> pinboardController.applyState(state));
              }
            });
  }

  /**
   * Refreshes journal-derived UI after a new entry (examine/deduce/question/combine/contradict). It
   * updates CONTENTS only and must NEVER force the journal window open — previously it called
   * {@code openJournalWindow()}, so every examine popped the window once it had been opened a
   * single time. The window opens only on an explicit player action (Journal button / open).
   */
  public void refreshJournalWindow() {
    Platform.runLater(
        () -> {
          loadJournalEntries(); // silent: repopulate without showing
          if (pinboardController != null) {
            syncPinboardData();
          }
        });
  }

  public void addJournalEntry(String entry) {
    if (journalWindow != null) {
      Platform.runLater(
          () -> {
            journalWindow.addEntry(entry);
          });
    }
  }

  public void addChatMessage(String sender, String message) {
    if (chatWindow != null) {
      Platform.runLater(
          () -> {
            chatWindow.addChatMessage(sender, message);
          });
    }
  }

  public void onChatMessageReceived(common.dto.ChatMessage message) {
    if (chatWindow != null) {
      Platform.runLater(
          () -> {
            chatWindow.addChatMessage(message);
          });
    }
  }

  public void incrementUnreadChat() {
    unreadChatCount++;
    shell.setUnreadChatBadge(unreadChatCount);
  }

  // ====================== Tasks ======================

  public void updateTaskState(String task, boolean isCompleted) {
    if (shell.isSinglePlayerMode()) {
      // For single player, we just update the local map directly.
      taskStates.put(task, isCompleted);
    } else if (shell.getGameClient() != null) {
      // For multiplayer, we need to find the task index and send a command.
      List<String> tasks = shell.getGameClient().getCurrentCaseTasks();
      if (tasks != null) {
        int taskIndex = tasks.indexOf(task);
        if (taskIndex != -1) {
          // Note: We are NOT updating the local map here directly.
          // The UI will only update when the server broadcasts the change back to us,
          // ensuring a single source of truth and synchronization.
          UpdateTaskStateCommand command = new UpdateTaskStateCommand(taskIndex, isCompleted);
          shell.getGameClient().sendDirectCommand(command);
        }
      }
    }
  }

  public void onTaskStateUpdate(int taskIndex, boolean isCompleted) {
    // This is a multiplayer-only feature, as single player state is local.
    if (shell.isSinglePlayerMode() || shell.getGameClient() == null) {
      return;
    }

    Platform.runLater(
        () -> {
          List<String> tasks = shell.getGameClient().getCurrentCaseTasks();

          if (tasks != null && taskIndex >= 0 && taskIndex < tasks.size()) {
            String task = tasks.get(taskIndex);
            taskStates.put(task, isCompleted); // Update the local state map

            // If the tasks window is open, refresh its view to reflect the change
            if (tasksWindow != null && tasksWindow.isShowing()) {
              tasksWindow.loadTasks(tasks, taskStates);
            }
          }
        });
  }
}

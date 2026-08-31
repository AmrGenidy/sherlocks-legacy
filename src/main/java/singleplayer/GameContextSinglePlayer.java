package singleplayer;

import Core.Detective;
import Core.DoctorWatson;
import Core.Room;
import Core.Suspect;
import Core.TaskList;
import JsonDTO.CaseData;
import client.GameClientStateListener;
import common.dto.ExamQuestionDTO;
import common.dto.ExamResultDTO;
import common.dto.JournalEntryDTO;
import common.dto.JournalEntryType;
import common.dto.RoomDescriptionDTO;
import common.dto.TextMessage;
import common.dto.WatsonHintResponseDTO;
import common.interfaces.GameActionContext;
import engine.GameEngine;
import engine.GameEventListener;
import engine.PlayerSet;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GameContextSinglePlayer implements GameActionContext {

  private static final Logger logger = LoggerFactory.getLogger(GameContextSinglePlayer.class);

  // --- Game State Fields ---
  private Detective detective;
  private boolean wantsToExitToCaseSelection = false;
  private boolean wantsToExitApplication = false;
  // Review Session flag (.scratch/gui-review-enter-case): when true the player walks a solved Case
  // read-only — BaseCommand.execute gates gameplay mutations + the Final Exam off (mirroring the
  // exam lockout). Set after 'start case' has built the world.
  private boolean reviewMode = false;
  private CaseData selectedCase;
  private client.GameClientStateListener stateListener; // NEW

  // All shared game state and rules live in the engine (ADR-0001); this context is its in-process
  // single-player adapter (ROADMAP Hard Constraint 1: direct method calls, never a socket).
  private final GameEngine engine;

  // Typed game-output events flow here. Defaults to the console renderer so terminal play and the
  // legacy GUI stdout-capture path are unchanged; the GUI can swap in a DTO-consuming sink.
  private GameOutputSink outputSink = new ConsoleGameOutputSink();

  /**
   * Overrides the destination for game-output events. A {@code null} sink restores the console
   * default.
   */
  public void setOutputSink(GameOutputSink outputSink) {
    this.outputSink = (outputSink != null) ? outputSink : new ConsoleGameOutputSink();
  }

  /**
   * Injects UI-language scaffolding texts into the engine (ui-localization). Case content is
   * unaffected. The console default stays {@code GameTexts.ENGLISH}.
   */
  public void setGameTexts(common.text.GameTexts texts) {
    engine.setGameTexts(texts);
  }

  @Override
  public common.text.GameTexts getGameTexts() {
    return engine.getGameTexts();
  }

  @Override
  public boolean isReviewMode() {
    return reviewMode;
  }

  @Override
  public void setReviewMode(boolean reviewMode) {
    this.reviewMode = reviewMode;
  }

  @Override
  public void seedReviewJournal(java.util.List<common.dto.JournalEntryDTO> entries) {
    engine.seedJournal(entries);
  }

  /** The active output sink (never null) — lets the SP shell emit its own scaffolding lines. */
  public GameOutputSink getOutputSink() {
    return outputSink;
  }

  // Applies a pinboard sync (e.g. the red contradiction link) to the GUI board, mirroring the
  // multiplayer GameClient's pinboardUpdateListener. Null for headless/console play. Wired by the
  // GUI shell alongside the output sink.
  private java.util.function.Consumer<common.dto.pinboard.PinboardUpdateDTO> pinboardUpdateHandler;

  /**
   * Routes single-player pinboard syncs (a {@link common.commands.pinboard.UpdatePinboardCommand}
   * broadcast) to the GUI board. A {@code null} handler means such broadcasts are dropped (console
   * play), never emitted to the terminal.
   */
  public void setPinboardUpdateHandler(
      java.util.function.Consumer<common.dto.pinboard.PinboardUpdateDTO> handler) {
    this.pinboardUpdateHandler = handler;
  }

  public void setStateListener(GameClientStateListener stateListener) {
    this.stateListener = stateListener;
    logger.debug(
        "DEBUG: stateListener SET to: "
            + (stateListener != null
                ? stateListener.getClass().getName() + "@" + System.identityHashCode(stateListener)
                : "null"));
  }

  public GameContextSinglePlayer() {
    this.detective = new Detective("PlayerDetectiveSP");
    this.engine =
        new GameEngine(
            new SoloPlayerSet(),
            new GameEventListener() {
              @Override
              public void toPlayer(String playerId, Serializable event) {
                routeEngineEvent(event);
              }

              @Override
              public void toAll(Serializable event, String excludePlayerId) {
                routeEngineEvent(event);
              }
            });
  }

  /**
   * Routes one engine event to its single-player surface. Counter updates travel the stateListener
   * seam (the GUI binds them there; the console suppresses them); every other event flows to the
   * output sink like any direct response.
   */
  private void routeEngineEvent(Serializable event) {
    if (event instanceof common.dto.InsightTokenUpdateDTO tokens) {
      if (this.stateListener != null) {
        this.stateListener.onInsightTokensUpdate(tokens.getCount());
      }
    } else if (event instanceof common.dto.DeductionCountUpdateDTO deductions) {
      if (this.stateListener != null) {
        this.stateListener.onDeductionCountUpdate(deductions.getCount());
      }
    } else {
      sendResponseToPlayer(null, event);
    }
  }

  public void resetForNewCaseLoad() {
    logContextMessage("Resetting context for new case load.");
    engine.resetWorld();
    this.selectedCase = null;
    engine.loadCase(null); // also clears the case-started flag and the exam state
    // A new case load is a fresh, fully-interactive session — clear any leftover Review lockout so
    // a
    // "Play again" on the reused context isn't gated like Review (.scratch/gui-review-enter-case).
    // The Review path re-arms this via setReviewMode(true) only after its own "start case".
    this.reviewMode = false;
    this.wantsToExitToCaseSelection = false;
    this.wantsToExitApplication = false;
    if (this.detective != null) {
      this.detective.resetForNewCase();
    } else {
      this.detective = new Detective("PlayerDetectiveSP_Fallback");
    }
  }

  public void initializeNewCase(CaseData caseFile, String startingRoomName) {
    this.selectedCase = caseFile;

    if (this.stateListener == null) {
      logger.warn("stateListener is NULL at start of initializeNewCase");
    } else {
      logger.debug(
          "DEBUG: stateListener is present at start of initializeNewCase: "
              + this.stateListener.getClass().getName()
              + "@"
              + System.identityHashCode(this.stateListener));
    }

    // Initialize shared tokens (and reset the deduction counter) from the case
    engine.loadCase(caseFile);
    logContextMessage(
        "[SP] Init sharedInsightTokens from CaseData: "
            + engine.getSharedInsightTokens()); // Exact requested log
    logContextMessage(
        "Init sharedInsightTokens from case file: startingInsightTokens="
            + caseFile.getStartingInsightTokens()
            + ", sharedInsightTokens="
            + engine.getSharedInsightTokens());

    // broadcast initial state to UI
    new Thread(
            () -> {
              try {
                Thread.sleep(500);
              } catch (Exception e) {
              } // Give UI time to bind
              if (this.stateListener != null) {
                logger.debug(
                    "DEBUG: Dispatching onInsightTokensUpdate("
                        + engine.getSharedInsightTokens()
                        + ") to listener="
                        + System.identityHashCode(this.stateListener));
                this.stateListener.onInsightTokensUpdate(engine.getSharedInsightTokens());
                this.stateListener.onDeductionCountUpdate(engine.getSessionDeduceCount()); // NEW
              } else {
                logger.warn("Cannot dispatch token update - stateListener is NULL");
              }
            })
        .start();

    // Engine places the detective, Dr. Watson, and the suspects (suspects always start outside
    // the Starting Room now — the historical multiplayer rule, adopted for both modes).
    if (engine.initializeStartingState()) {
      logContextMessage(
          "Initialized case '"
              + caseFile.getTitle()
              + "'. Starting room: "
              + (getCurrentRoomForPlayer(null) != null
                  ? getCurrentRoomForPlayer(null).getName()
                  : "unknown"));
    } else {
      logContextMessage("CRITICAL Error: No rooms loaded at all. Player cannot be placed.");
    }
  }

  @Override
  public void handlePlayerCancelLobby(String playerId) {
    // This command is for multiplayer lobbies.
    // In single player, 'exit' is used to return to case selection,
    // so this method has no action here. We can just provide a log message.
    logContextMessage("handlePlayerCancelLobby called in Single Player context. No action taken.");

    // We could potentially have it act like the 'exit' command if we wanted.
    // For now, let's treat it as a command that's not applicable here.
    sendResponseToPlayer(
        playerId, new TextMessage("The 'cancel' command is not used in this context.", true));
  }

  private void logContextMessage(String message) {
    logger.info(message);
  }

  public RoomDescriptionDTO createRoomDescriptionDTO(Room room, String playerId) {
    return engine.buildRoomDescription(room, playerId);
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
  public Map<String, Room> getAllRooms() {
    return engine.getAllRooms();
  }

  @Override
  public void addSuspect(Suspect suspect) {
    engine.addSuspect(suspect);
  }

  @Override
  public void logLoadingMessage(String message) {
    logger.info("[LOADER_SP] {}", message);
  }

  @Override
  public String getContextIdForLog() {
    return "SinglePlayer";
  }

  // --- GameActionContext Implementation (for Commands) ---
  @Override
  public boolean isCaseStarted() {
    return engine.isCaseStarted();
  }

  @Override
  public void setCaseStarted(boolean started) {
    if (engine.isCaseStarted() == started) {
      logContextMessage("setCaseStarted(" + started + ") called, but state is already " + started);
      return;
    }
    engine.setCaseStartedFlag(started);
    if (started) {
      logContextMessage(
          "Case '"
              + (selectedCase != null ? selectedCase.getTitle() : "Unknown")
              + "' has been started.");
      engine.announceCaseStart();
    } else {
      logContextMessage(
          "Case '"
              + (selectedCase != null ? selectedCase.getTitle() : "Unknown")
              + "' has been stopped/reset.");
    }
  }

  @Override
  public CaseData getSelectedCase() { // MODIFIED: Return type is CaseData
    return this.selectedCase;
  }

  public CaseData getCaseFile() {
    return this.selectedCase;
  }

  @Override
  public Detective getPlayerDetective(String playerId) {
    return this.detective;
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
    sendResponseToPlayer(null, message);
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
    if (responseDto == null) {
      return;
    }
    // Emit the typed event. The default console sink reproduces the legacy marker-text output;
    // a GUI sink consumes the DTO directly. (Formatting now lives in ConsoleGameOutputSink.)
    outputSink.emit(responseDto);
  }

  @Override
  public void broadcastToSession(Serializable dto, String excludePlayerId) {
    // A pinboard sync (e.g. the red contradiction link ContradictCommand broadcasts) arrives as an
    // UpdatePinboardCommand — a Command, not a display DTO. Route its PinboardUpdateDTO straight to
    // the board handler exactly as the multiplayer GameClient does, and return WITHOUT emitting:
    // otherwise the output sink would print the command's default toString() to the terminal
    // (e.g. "common.commands.pinboard.UpdatePinboardCommand@…").
    if (dto instanceof common.commands.pinboard.UpdatePinboardCommand pinboardCommand) {
      if (pinboardUpdateHandler != null && pinboardCommand.getUpdate() != null) {
        pinboardUpdateHandler.accept(pinboardCommand.getUpdate());
      }
      return;
    }
    sendResponseToPlayer(null, dto);
  }

  @Override
  public void notifyPlayerMove(String movingPlayerId, Room newRoom, Room oldRoom) {
    engine.notifyPlayerMove(movingPlayerId, newRoom, oldRoom); // no-op for a solo session
  }

  @Override
  public boolean canStartFinalExam(String playerId) {
    return engine.canStartFinalExam();
  }

  @Override
  public void startExamProcess(String playerId) {
    engine.startExam(playerId);
  }

  @Override
  public boolean isExamActive() {
    return engine.isExamActive();
  }

  public ExamQuestionDTO getCurrentExamQuestionDTO() {
    return engine.getCurrentExamQuestion();
  }

  /** The scored result of the last exam; survives until the next exam start or case load. */
  public ExamResultDTO getLastResultDTO() {
    return engine.getLastExamResult();
  }

  @Override
  public void updateNpcMovements(String triggeringPlayerId) {
    engine.updateNpcMovements(triggeringPlayerId);
  }

  @Override
  public void handlePlayerExitRequest(String playerId) {
    // In single-player, "exit" should always go back to the case selection, never
    // exit the app.
    sendResponseToPlayer(
        playerId, new TextMessage("Exiting current case. Returning to case selection.", false));
    sendResponseToPlayer(playerId, new common.dto.ReturnToCaseSelectionDTO());
    engine.resetExamStateHard();
    engine.setCaseStartedFlag(false);
    this.wantsToExitToCaseSelection = true;
  }

  @Override
  public void processUpdateDisplayName(String playerId, String newDisplayName) {
    Detective spDetective = getPlayerDetective(playerId);
    if (spDetective != null) {
      logContextMessage(
          "Display name update processed for "
              + spDetective.getPlayerId()
              + " to "
              + newDisplayName
              + ". (In SP, client handles its own display name for prompts).");
      sendResponseToPlayer(
          playerId, new TextMessage("Display name noted as: " + newDisplayName, false));
    } else {
      logContextMessage(
          "Error: processUpdateDisplayName called, but SP detective not found for ID: " + playerId);
    }
  }

  @Override
  public void processRequestStartCase(String requestingPlayerId) {
    logContextMessage(
        "Received 'request start case' in Single Player for player: " + requestingPlayerId);
    if (isCaseStarted()) {
      sendResponseToPlayer(
          requestingPlayerId, new TextMessage("The case has already started.", false));
      return;
    }
    if (getSelectedCase() == null) {
      sendResponseToPlayer(requestingPlayerId, new TextMessage("No case selected to start.", true));
      return;
    }
    setCaseStarted(true);
  }

  @Override
  public void processRequestInitiateExam(String requestingPlayerId) {
    logContextMessage(
        "Received 'request final exam' in Single Player for player: " + requestingPlayerId);
    if (!isCaseStarted()) {
      sendResponseToPlayer(
          requestingPlayerId,
          new TextMessage("The case has not started yet. Cannot start exam.", true));
      return;
    }
    if (engine.isExamActive()) {
      sendResponseToPlayer(
          requestingPlayerId, new TextMessage("An exam is already in progress.", false));
      return;
    }
    startExamProcess(requestingPlayerId);
  }

  @Override
  public WatsonHintResponseDTO askWatsonForHint(String playerId) {
    return engine.askWatsonForHint(this, this.detective.getPlayerId());
  }

  @Override
  public boolean trySpendInsightTokens(int amount) {
    return engine.trySpendInsightTokens(amount);
  }

  @Override
  public WatsonHintResponseDTO askWatsonAboutTarget(String playerId, String targetName) {
    return engine.askWatsonAboutTarget(this, playerId, targetName);
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

  // --- SP Specific Flow Control ---
  public boolean wantsToExitToCaseSelection() {
    return wantsToExitToCaseSelection;
  }

  public boolean wantsToExitApplication() {
    return wantsToExitApplication;
  }

  public void resetExitFlags() {
    this.wantsToExitApplication = false;
    this.wantsToExitToCaseSelection = false;
  }

  public boolean isAwaitingExamAnswer() {
    return engine.isAwaitingExamAnswer();
  }

  public int getAwaitingQuestionNumber() {
    return engine.getAwaitingQuestionNumber();
  }

  @Override
  public int getSessionDeduceCount() {
    // One canonical counter in the engine — the heal from awardInsightToken is observable here
    // (issue 04 resolution; Detective.deduceCount is only the duplicate-deduction guard).
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
  public void processUpdateTaskState(String playerId, int taskIndex, boolean isCompleted) {
    engine.processUpdateTaskState(playerId, taskIndex, isCompleted);
  }

  @Override
  public Map<Integer, Boolean> getTaskStates() {
    return engine.getTaskStates();
  }

  @Override
  public void processSubmitQuestionAnswer(
      String playerId, int questionIndex, Map<String, String> answers) {
    engine.submitExamAnswer(playerId, questionIndex, answers);
  }

  @Override
  public void processContinueGame(String playerId) {
    // In Single Player, "Continue" just means showing the current room again (like
    // a 'look' command) to confirm they are back in the game world.
    engine.processContinueGame(playerId);
  }

  @Override
  public String getWatsonImagePath() {
    if (selectedCase != null) {
      return selectedCase.getWatsonImagePath();
    }
    return null;
  }

  public int getSharedInsightTokens() {
    return engine.getSharedInsightTokens();
  }

  /** The single local Detective as the engine's {@link PlayerSet} seam. */
  private final class SoloPlayerSet implements PlayerSet {
    @Override
    public Detective detectiveFor(String playerId) {
      // Historical single-player contract: every id (including null) resolves to the one
      // Detective.
      return detective;
    }

    @Override
    public List<Detective> detectives() {
      return detective != null ? List.of(detective) : List.of();
    }

    @Override
    public String displayName(String playerId) {
      return detective != null ? detective.getPlayerId() : "Detective";
    }

    @Override
    public boolean isSolo() {
      return true;
    }
  }
}

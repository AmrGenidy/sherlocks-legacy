package common.interfaces;

import Core.Detective;
import Core.DoctorWatson;
import Core.Room;
import Core.Suspect;
import Core.TaskList;
import JsonDTO.CaseData;
import common.dto.JournalEntryDTO;
import common.dto.JournalEntryType;
import common.dto.RoomDescriptionDTO;
import common.dto.TextMessage;
import common.dto.WatsonHintResponseDTO;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

public interface GameActionContext extends GameContext {

  /**
   * Scaffolding strings for command output (ui-localization). Default: the legacy English — the
   * server keeps emitting exactly what it always did; the GUI single-player context overrides this
   * with the engine's UI-language texts.
   */
  default common.text.GameTexts getGameTexts() {
    return common.text.GameTexts.ENGLISH;
  }

  /**
   * Whether this is a non-destructive <b>Review Session</b> (CONTEXT.md): the player walks a solved
   * Case (move/look + view Journal/Pinboard) but gameplay mutations and the Final Exam are disabled,
   * so the saved Completed-Case Record is never changed. Review is a single-player / host-local
   * concern; the server context keeps the default {@code false}. The gate itself lives once at
   * {@link common.commands.BaseCommand#execute} (mirroring the Final-Exam lockout, ADR-0001).
   */
  default boolean isReviewMode() {
    return false;
  }

  /** Enters/leaves Review Session mode (single-player only; no-op elsewhere). */
  default void setReviewMode(boolean reviewMode) {}

  /** Seeds the engine Journal from a saved Completed-Case Record (single-player only; no-op else). */
  default void seedReviewJournal(List<JournalEntryDTO> entries) {}

  // Methods for command logic to interact with the game state
  Room getCurrentRoomForPlayer(String playerId);

  /**
   * Builds the full {@link RoomDescriptionDTO} for {@code room} (image path, object/suspect
   * positions and per-sprite scales included) — the same canonical description the room-entry path
   * emits. Commands that re-describe the current room (e.g. {@code look}) must use this so they
   * never emit a partial DTO that blanks the room image (.scratch/ingame-fixes-2 issue 01).
   */
  RoomDescriptionDTO createRoomDescriptionDTO(Room room, String playerId);

  Detective getPlayerDetective(String playerId);

  void sendResponseToPlayer(String playerId, Serializable message);

  void broadcastMessage(TextMessage message);

  void broadcastToSession(Serializable dto, String excludePlayerId);

  void addJournalEntry(JournalEntryDTO entry);

  List<JournalEntryDTO> getJournalEntries(String playerId);

  // New Query Methods
  List<JournalEntryDTO> getJournalEntriesByType(String playerId, JournalEntryType type);

  List<JournalEntryDTO> getJournalEntriesBySourceId(String playerId, String sourceId);

  JournalEntryDTO getJournalEntryById(String playerId, String entryId);

  Map<JournalEntryType, List<JournalEntryDTO>> getJournalEntriesGroupedByType(String playerId);

  // New method needed for DeduceCommand shared logic
  void incrementSessionDeduceCount();

  boolean trySpendInsightToken();

  int getSessionDeduceCount();

  void awardInsightToken();

  // Access to all suspects
  List<Suspect> getAllSuspects();

  // Missing methods restored
  void processUpdateDisplayName(String playerId, String newDisplayName);

  /**
   * Records a player's chosen avatar preset id and broadcasts it to peers. Default no-op: avatars
   * are a multiplayer-only concept (single-player has no peers to show one to), so only the server
   * context overrides this.
   */
  default void processUpdateAvatar(String playerId, String avatarId) {
    // no-op for contexts without peers (single-player)
  }

  boolean canStartFinalExam(String playerId);

  /**
   * True while a Final Exam is in progress (from start until the result is scored). The command
   * dispatch ({@code BaseCommand.execute}) consults this to lock out gameplay/action commands in
   * both contexts (.scratch/exam-command-lockout). Delegates to {@code GameEngine.isExamActive()}.
   */
  boolean isExamActive();

  void startExamProcess(String playerId);

  void processSubmitQuestionAnswer(String playerId, int questionIndex, Map<String, String> answers);

  void handlePlayerExitRequest(String playerId);

  void processRequestStartCase(String playerId);

  void handlePlayerCancelLobby(String playerId);

  boolean isCaseStarted();

  CaseData getSelectedCase();

  void setCaseStarted(boolean started);

  String getOccupantsDescriptionInRoom(Room room, String askingPlayerId);

  boolean movePlayer(String playerId, String direction);

  TaskList getTaskList();

  boolean trySpendInsightTokens(int amount);

  WatsonHintResponseDTO askWatsonAboutTarget(String playerId, String targetName);

  WatsonHintResponseDTO askWatsonForHint(String playerId);

  void processRequestInitiateExam(String playerId);

  void processContinueGame(String playerId);

  void processUpdateTaskState(String playerId, int taskIndex, boolean isCompleted);

  /** Per-task completion toggles recorded so far, keyed by task index (issue 07 read-back). */
  Map<Integer, Boolean> getTaskStates();

  // Added notifyPlayerMove here just in case, because GameContextServer had
  // @Override on it.
  void notifyPlayerMove(String movingPlayerId, Room newRoom, Room oldRoom);

  // Newly identified missing methods
  DoctorWatson getWatson();

  void updateNpcMovements(String triggeringPlayerId);

  // Cooldown Management
  void reportCombineSuccess();

  void reportCombineFailure();

  boolean isCombineOnCooldown();

  long getCombineCooldownRemaining(); // Returns seconds

  void reportContradictSuccess();

  void reportContradictFailure();

  boolean isContradictOnCooldown();

  long getContradictCooldownRemaining(); // Returns seconds
}

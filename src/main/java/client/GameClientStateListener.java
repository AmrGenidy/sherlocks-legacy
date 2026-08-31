package client;

import JsonDTO.CaseFile;
import common.dto.PublicGameInfoDTO;
import common.dto.RoomDescriptionDTO;
import java.util.List;

public interface GameClientStateListener {
  void onDisconnected();

  void onConnecting();

  void onConnected();

  void onMainMenu();

  void onReturnToMainMenu(String message);

  void onHostGameOptions();

  void onCaseSelection(List<CaseFile> cases);

  void onLanguageSelection(CaseFile caseFile);

  void onHostingLobby(String gameCode);

  void onJoinGameOptions();

  void onPublicGamesList(List<PublicGameInfoDTO> games);

  void onPrivateGameEntry();

  void onLobby();

  /**
   * Fired when a player's display name or avatar in the current lobby changes (player-profile
   * feature), so the lobby can re-render the seats with up-to-date names/portraits. Default no-op
   * for listeners that don't show a lobby.
   */
  default void onLobbyIdentitiesUpdated() {}

  void onEnterGame(RoomDescriptionDTO initialRoom);

  void onUpdateRoom(RoomDescriptionDTO newRoom);

  void onReceiveCaseInvitation(String invitation, boolean isHost);

  void onJournalUpdated();

  void onChatMessageReceived(common.dto.ChatMessage message);

  void onTaskStateUpdate(int taskIndex, boolean isCompleted);

  void onFinalExamUnlocked();

  void onFinalExamRequest(String requesterDisplayName);

  void onJoinGameFailed(String message);

  void showExamResults(common.dto.ExamResultDTO resultDTO);

  void onDialogueEvent(common.dto.DialogueEventDTO event);

  void onInsightTokensUpdate(int count);

  void onDeductionCountUpdate(int count);

  /**
   * A command (contradict/combine) was locked after too many failed attempts. Default no-op so
   * non-GUI listeners can ignore it; the GUI shell shows a bottom-right countdown badge.
   *
   * @param commandType the locked command, e.g. {@code "contradict"} or {@code "combine"}
   * @param cooldownUntil the epoch-millis instant the lock ends
   */
  default void onCommandCooldownUpdate(String commandType, long cooldownUntil) {}
}

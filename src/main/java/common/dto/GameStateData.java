package common.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameStateData implements Serializable {
  @Serial
  private static final long serialVersionUID = 1L;

  private String caseTitle;
  private String sessionId;
  private List<String> playerIds;
  private long lastPlayedTimestamp;
  private boolean isCaseStarted;
  private List<JournalEntryDTO> journalEntries;
  private int deduceCount;
  private List<String> deducedObjectsInSession;
  private Map<String, String> playerCurrentRoomNames;
  private Map<String, String> npcCurrentRoomNames;
  private Map<String, Boolean> taskCompletionStatus;
  private Map<String, Integer> playerScores;
  private Map<String, String> playerRanks;

  public GameStateData() {
    this.playerIds = new ArrayList<>();
    this.journalEntries = new ArrayList<>();
    this.playerCurrentRoomNames = new HashMap<>();
    this.npcCurrentRoomNames = new HashMap<>();
    this.taskCompletionStatus = new HashMap<>();
    this.playerScores = new HashMap<>();
    this.playerRanks = new HashMap<>();
    this.deducedObjectsInSession = new ArrayList<>();
  }

  public String getCaseTitle() {
    return caseTitle;
  }

  public void setCaseTitle(String caseTitle) {
    this.caseTitle = caseTitle;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public List<String> getPlayerIds() {
    return playerIds;
  }

  public void setPlayerIds(List<String> playerIds) {
    this.playerIds = playerIds;
  }

  public long getLastPlayedTimestamp() {
    return lastPlayedTimestamp;
  }

  public void setLastPlayedTimestamp(long lastPlayedTimestamp) {
    this.lastPlayedTimestamp = lastPlayedTimestamp;
  }

  public boolean isCaseStarted() {
    return isCaseStarted;
  }

  public void setCaseStarted(boolean caseStarted) {
    isCaseStarted = caseStarted;
  }

  public List<JournalEntryDTO> getJournalEntries() {
    return journalEntries;
  }

  public void setJournalEntries(List<JournalEntryDTO> journalEntries) {
    this.journalEntries = journalEntries;
  }

  public int getDeduceCount() {
    return deduceCount;
  }

  public void setDeduceCount(int deduceCount) {
    this.deduceCount = deduceCount;
  }

  public List<String> getDeducedObjectsInSession() {
    return deducedObjectsInSession;
  }

  public void setDeducedObjectsInSession(List<String> deducedObjectsInSession) {
    this.deducedObjectsInSession = deducedObjectsInSession;
  }

  public Map<String, String> getPlayerCurrentRoomNames() {
    return playerCurrentRoomNames;
  }

  public void setPlayerCurrentRoomNames(Map<String, String> playerCurrentRoomNames) {
    this.playerCurrentRoomNames = playerCurrentRoomNames;
  }

  public Map<String, String> getNpcCurrentRoomNames() {
    return npcCurrentRoomNames;
  }

  public void setNpcCurrentRoomNames(Map<String, String> npcCurrentRoomNames) {
    this.npcCurrentRoomNames = npcCurrentRoomNames;
  }

  public Map<String, Boolean> getTaskCompletionStatus() {
    return taskCompletionStatus;
  }

  public void setTaskCompletionStatus(Map<String, Boolean> taskCompletionStatus) {
    this.taskCompletionStatus = taskCompletionStatus;
  }

  public Map<String, Integer> getPlayerScores() {
    return playerScores;
  }

  public void setPlayerScores(Map<String, Integer> playerScores) {
    this.playerScores = playerScores;
  }

  public Map<String, String> getPlayerRanks() {
    return playerRanks;
  }

  public void setPlayerRanks(Map<String, String> playerRanks) {
    this.playerRanks = playerRanks;
  }
}
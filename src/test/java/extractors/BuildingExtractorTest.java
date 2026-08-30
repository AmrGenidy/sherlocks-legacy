package extractors;

import static org.junit.Assert.*;

import Core.Detective;
import Core.DoctorWatson;
import Core.Room;
import Core.Suspect;
import Core.TaskList;
import JsonDTO.CaseData;
import JsonDTO.CaseFile;
import common.dto.FinalExamDTO;
import common.dto.JournalEntryDTO;
import common.dto.JournalEntryType;
import common.dto.TextMessage;
import common.dto.WatsonHintResponseDTO;
import common.interfaces.GameActionContext;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class BuildingExtractorTest {

  @Test
  public void testLoadBuildingExtractsImagePath() {
    // 1. Setup Mock CaseData
    CaseFile.RoomData roomData = new CaseFile.RoomData();
    roomData.name = "TestRoom";
    roomData.description = "A test room";
    roomData.imagePath = "images/test_room.png";
    roomData.neighbors = new HashMap<>();

    MockCaseData caseData = new MockCaseData();
    caseData.rooms = new ArrayList<>();
    caseData.rooms.add(roomData);
    caseData.startingRoom = "TestRoom";

    // 2. Setup Mock GameContext
    MockGameContext context = new MockGameContext();

    // 3. Run Extractor
    boolean result = BuildingExtractor.loadBuilding(caseData, context);

    // 4. Verification
    assertTrue("Building loading should succeed", result);
    Room loadedRoom = context.getRoomByName("TestRoom");
    assertNotNull("Room should be loaded", loadedRoom);
    assertEquals(
        "Image path should be extracted", "images/test_room.png", loadedRoom.getImagePath());
  }

  @Test
  public void testLoadBuildingHandlesNullImagePath() {
    CaseFile.RoomData roomData = new CaseFile.RoomData();
    roomData.name = "NullImageRoom";
    roomData.description = "Desc";
    roomData.imagePath = null;

    MockCaseData caseData = new MockCaseData();
    caseData.rooms = new ArrayList<>();
    caseData.rooms.add(roomData);
    caseData.startingRoom = "NullImageRoom";

    MockGameContext context = new MockGameContext();
    BuildingExtractor.loadBuilding(caseData, context);

    Room room = context.getRoomByName("NullImageRoom");
    assertNull("Image path should be null", room.getImagePath());
  }

  // --- Mocks ---

  private static class MockCaseData implements CaseData {
    public List<CaseFile.RoomData> rooms;
    public String startingRoom;

    @Override
    public String getTitle() {
      return "Mock";
    }

    @Override
    public String getInvitation() {
      return "";
    }

    @Override
    public String getDescription() {
      return "";
    }

    @Override
    public String getStartingRoom() {
      return startingRoom;
    }

    @Override
    public List<CaseFile.SuspectData> getSuspects() {
      return Collections.emptyList();
    }

    @Override
    public List<CaseFile.RoomData> getRooms() {
      return rooms;
    }

    @Override
    public FinalExamDTO getFinalExam() {
      return null;
    }

    @Override
    public List<String> getTasks() {
      return Collections.emptyList();
    }

    @Override
    public List<CaseFile.RankTierData> getRankingTiers() {
      return Collections.emptyList();
    }

    @Override
    public String getWinningMessage() {
      return "";
    }

    @Override
    public String getWatsonImagePath() {
      return null;
    }

    @Override
    public Integer getStartingInsightTokens() {
      return 0;
    }

    @Override
    public List<CaseFile.CombineRule> getCombineLogic() {
      return null;
    }

    @Override
    public java.util.Map<String, List<JsonDTO.LocalizedCaseFile.LocalizedWatsonHint>>
        getStructuredWatsonHints() {
      return null;
    }

    @Override
    public JsonDTO.CaseFile.RedHerringMetadata getRedHerrings() {
      return null;
    }

    @Override
    public String getLanguageCode() {
      return "en";
    }

    @Override
    public JsonDTO.LocalizedCaseFile.LocalizedCaseFileBlock getCaseFile() {
      return null;
    }
  }

  private static class MockGameContext implements GameActionContext {
    private Map<String, Room> rooms = new HashMap<>();

    @Override
    public Map<Integer, Boolean> getTaskStates() {
      return java.util.Collections.emptyMap();
    }

    @Override
    public void addRoom(Room room) {
      rooms.put(room.getName().toLowerCase(), room);
    }

    @Override
    public Room getRoomByName(String name) {
      return rooms.get(name.toLowerCase());
    }

    @Override
    public common.dto.RoomDescriptionDTO createRoomDescriptionDTO(Room room, String playerId) {
      return null;
    }

    @Override
    public Map<String, Room> getAllRooms() {
      return rooms;
    }

    @Override
    public boolean trySpendInsightToken() {
      return false;
    }

    @Override
    public void incrementSessionDeduceCount() {}

    @Override
    public int getSessionDeduceCount() {
      return 0;
    }

    @Override
    public void awardInsightToken() {}

    @Override
    public void addSuspect(Suspect suspect) {}

    @Override
    public void logLoadingMessage(String message) {
      System.out.println(message);
    }

    @Override
    public String getContextIdForLog() {
      return "MockContext";
    }

    // Unused methods
    @Override
    public boolean isCaseStarted() {
      return false;
    }

    @Override
    public boolean isExamActive() {
      return false;
    }

    @Override
    public void setCaseStarted(boolean started) {}

    @Override
    public CaseData getSelectedCase() {
      return null;
    }

    @Override
    public Detective getPlayerDetective(String playerId) {
      return null;
    }

    @Override
    public Room getCurrentRoomForPlayer(String playerId) {
      return null;
    }

    @Override
    public String getOccupantsDescriptionInRoom(Room room, String askingPlayerId) {
      return "";
    }

    @Override
    public TaskList getTaskList() {
      return null;
    }

    @Override
    public DoctorWatson getWatson() {
      return null;
    }

    @Override
    public List<Suspect> getAllSuspects() {
      return null;
    }

    @Override
    public boolean movePlayer(String playerId, String direction) {
      return false;
    }

    @Override
    public void broadcastMessage(TextMessage message) {}

    @Override
    public void addJournalEntry(JournalEntryDTO entry) {}

    @Override
    public List<JournalEntryDTO> getJournalEntries(String playerId) {
      return null;
    }

    @Override
    public void sendResponseToPlayer(String playerId, Serializable responseDto) {}

    @Override
    public void broadcastToSession(Serializable dto, String excludePlayerId) {}

    @Override
    public void notifyPlayerMove(String movingPlayerId, Room newRoom, Room oldRoom) {}

    @Override
    public boolean canStartFinalExam(String playerId) {
      return false;
    }

    @Override
    public void startExamProcess(String playerId) {}

    @Override
    public void processUpdateDisplayName(String playerId, String newDisplayName) {}

    @Override
    public void processRequestStartCase(String requestingPlayerId) {}

    @Override
    public void processRequestInitiateExam(String requestingPlayerId) {}

    @Override
    public boolean trySpendInsightTokens(int amount) {
      return false;
    }

    @Override
    public common.dto.WatsonHintResponseDTO askWatsonAboutTarget(
        String playerId, String targetName) {
      return null;
    }

    @Override
    public WatsonHintResponseDTO askWatsonForHint(String playerId) {
      return null;
    }

    @Override
    public void updateNpcMovements(String triggeringPlayerId) {}

    @Override
    public void handlePlayerExitRequest(String playerId) {}

    @Override
    public void processUpdateTaskState(String playerId, int taskIndex, boolean isCompleted) {}

    @Override
    public List<JournalEntryDTO> getJournalEntriesByType(String playerId, JournalEntryType type) {
      return null;
    }

    @Override
    public List<JournalEntryDTO> getJournalEntriesBySourceId(String playerId, String sourceId) {
      return null;
    }

    @Override
    public JournalEntryDTO getJournalEntryById(String playerId, String entryId) {
      return null;
    }

    @Override
    public Map<JournalEntryType, List<JournalEntryDTO>> getJournalEntriesGroupedByType(
        String playerId) {
      return null;
    }

    @Override
    public void processSubmitQuestionAnswer(
        String playerId, int questionIndex, Map<String, String> answers) {}

    @Override
    public void processContinueGame(String playerId) {}

    @Override
    public void handlePlayerCancelLobby(String playerId) {}

    @Override
    public void reportCombineSuccess() {}

    @Override
    public void reportCombineFailure() {}

    @Override
    public boolean isCombineOnCooldown() {
      return false;
    }

    @Override
    public long getCombineCooldownRemaining() {
      return 0;
    }

    @Override
    public void reportContradictSuccess() {}

    @Override
    public void reportContradictFailure() {}

    @Override
    public boolean isContradictOnCooldown() {
      return false;
    }

    @Override
    public long getContradictCooldownRemaining() {
      return 0;
    }
  }
}

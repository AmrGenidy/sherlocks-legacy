package extractors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import Core.Room;
import Core.Suspect;
import JsonDTO.CaseData;
import JsonDTO.CaseFile;
import common.interfaces.GameContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Suspect placement (Case Maker slice 3, DEC-5): {@link SuspectExtractor} honours an authored home
 * room and carries the normalized position, sprite scale, and stationary flag onto the {@link
 * Suspect}, falling back to the historical random placement when no (resolvable) home room is
 * given.
 */
public class SuspectExtractorTest {

  /** Minimal {@link GameContext} double: pre-seeded rooms, captures added suspects. */
  private static final class TestContext implements GameContext {
    final Map<String, Room> rooms = new LinkedHashMap<>();
    final List<Suspect> suspects = new ArrayList<>();

    TestContext(String... roomNames) {
      for (String name : roomNames) {
        rooms.put(name.toLowerCase(), new Room(name, name + " description"));
      }
    }

    @Override
    public void addRoom(Room room) {
      rooms.put(room.getName().toLowerCase(), room);
    }

    @Override
    public Room getRoomByName(String name) {
      return name == null ? null : rooms.get(name.toLowerCase());
    }

    @Override
    public Map<String, Room> getAllRooms() {
      return rooms;
    }

    @Override
    public void addSuspect(Suspect suspect) {
      suspects.add(suspect);
    }

    @Override
    public void logLoadingMessage(String message) {}

    @Override
    public String getContextIdForLog() {
      return "test";
    }
  }

  private static CaseData caseWith(CaseFile.SuspectData... suspects) {
    CaseData caseData = mock(CaseData.class);
    when(caseData.getSuspects()).thenReturn(List.of(suspects));
    return caseData;
  }

  private static CaseFile.SuspectData suspect(String name) {
    CaseFile.SuspectData data = new CaseFile.SuspectData();
    data.name = name;
    return data;
  }

  @Test
  public void suspectIsPlacedInItsAuthoredHomeRoom() throws Exception {
    TestContext context = new TestContext("Study", "Hall", "Garden");
    CaseFile.SuspectData valet = suspect("Valet");
    valet.homeRoom = "Hall";

    SuspectExtractor.loadSuspects(caseWith(valet), context);

    assertEquals(1, context.suspects.size());
    Suspect placed = context.suspects.get(0);
    assertEquals("Hall", placed.getCurrentRoom().getName());
    assertEquals("Hall", placed.getHomeRoom());
  }

  @Test
  public void positionScaleAndStationaryFlagAreCarriedOntoTheSuspect() throws Exception {
    TestContext context = new TestContext("Study");
    CaseFile.SuspectData ghost = suspect("Ghost");
    ghost.homeRoom = "Study";
    ghost.posX = 0.3;
    ghost.posY = 0.7;
    ghost.stationary = true;

    SuspectExtractor.loadSuspects(caseWith(ghost), context);

    Suspect placed = context.suspects.get(0);
    assertEquals(0.3, placed.getPosX(), 1e-9);
    assertEquals(0.7, placed.getPosY(), 1e-9);
    assertTrue(placed.isStationary());
  }

  @Test
  public void suspectWithoutHomeRoomFallsBackToARealRoom() throws Exception {
    TestContext context = new TestContext("Study", "Hall");
    SuspectExtractor.loadSuspects(caseWith(suspect("Drifter")), context);

    Suspect placed = context.suspects.get(0);
    assertNotNull("a suspect with no home room is still placed somewhere", placed.getCurrentRoom());
    assertTrue(context.rooms.containsValue(placed.getCurrentRoom()));
    assertTrue("non-stationary by default", !placed.isStationary());
  }

  @Test
  public void unresolvableHomeRoomFallsBackToARealRoom() throws Exception {
    TestContext context = new TestContext("Study", "Hall");
    CaseFile.SuspectData lost = suspect("Lost");
    lost.homeRoom = "Nowhere"; // not a room in the case

    SuspectExtractor.loadSuspects(caseWith(lost), context);

    Suspect placed = context.suspects.get(0);
    assertNotNull(placed.getCurrentRoom());
    assertTrue(context.rooms.containsValue(placed.getCurrentRoom()));
  }
}

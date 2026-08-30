package engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import Core.Detective;
import Core.Room;
import Core.Suspect;
import JsonDTO.CaseData;
import java.io.Serializable;
import java.util.List;
import org.junit.Test;

/**
 * {@link GameEngine#initializeStartingState()} suspect placement (Case Maker slice 3, DEC-5). The
 * engine re-places suspects on every (re)start, so it — not just the extractor — must honour an
 * authored home room, overriding the historical "place in a random non-starting room" rule, while
 * still falling back to that rule for suspects without a home room.
 */
public class GameEngineSuspectPlacementTest {

  private static final PlayerSet NO_PLAYERS =
      new PlayerSet() {
        @Override
        public Detective detectiveFor(String playerId) {
          return null;
        }

        @Override
        public List<Detective> detectives() {
          return List.of();
        }

        @Override
        public String displayName(String playerId) {
          return "";
        }

        @Override
        public boolean isSolo() {
          return true;
        }
      };

  private static final GameEventListener SILENT =
      new GameEventListener() {
        @Override
        public void toPlayer(String playerId, Serializable event) {}

        @Override
        public void toAll(Serializable event, String excludePlayerId) {}
      };

  private GameEngine engineWithRooms(String startingRoom, String... roomNames) {
    CaseData caseData = mock(CaseData.class);
    when(caseData.getStartingRoom()).thenReturn(startingRoom);
    GameEngine engine = new GameEngine(NO_PLAYERS, SILENT);
    engine.loadCase(caseData);
    for (String name : roomNames) {
      engine.addRoom(new Room(name, name + " description"));
    }
    return engine;
  }

  @Test
  public void suspectIsPlacedInItsHomeRoomEvenWhenThatIsTheStartingRoom() {
    GameEngine engine = engineWithRooms("Ballroom", "Ballroom", "Terrace");
    Suspect valet = new Suspect("valet", "Valet", "stmt", "clue");
    valet.setHomeRoom("Ballroom"); // historically suspects were kept OUT of the starting room
    engine.addSuspect(valet);

    engine.initializeStartingState();

    assertEquals("Ballroom", valet.getCurrentRoom().getName());
  }

  @Test
  public void suspectWithoutHomeRoomKeepsTheRandomNonStartingPlacement() {
    GameEngine engine = engineWithRooms("Ballroom", "Ballroom", "Terrace", "Garden");
    Suspect drifter = new Suspect("drifter", "Drifter", "stmt", "clue");
    engine.addSuspect(drifter);

    engine.initializeStartingState();

    assertNotNull(drifter.getCurrentRoom());
    assertNotEquals(
        "legacy behaviour: a home-less suspect avoids the starting room",
        "Ballroom",
        drifter.getCurrentRoom().getName());
  }

  @Test
  public void aStationarySuspectDoesNotWander() {
    GameEngine engine = engineWithRooms("Hall", "Hall", "Study");
    Room hall = engine.getRoomByName("Hall");
    Room study = engine.getRoomByName("Study");
    hall.setNeighbor("north", study);
    study.setNeighbor("south", hall);

    Suspect statue = new Suspect("statue", "Statue", "stmt", "clue");
    statue.setHomeRoom("Hall");
    statue.setStationary(true);
    engine.addSuspect(statue);
    engine.initializeStartingState();
    engine.setCaseStartedFlag(true);

    for (int i = 0; i < 20; i++) {
      engine.updateNpcMovements(null);
    }

    assertEquals("a stationary suspect stays put", "Hall", statue.getCurrentRoom().getName());
  }

  @Test
  public void roomDescriptionCarriesWatsonImageScaleFromMetadata() {
    CaseData caseData = mock(CaseData.class);
    when(caseData.getStartingRoom()).thenReturn("Hall");
    when(caseData.getWatsonImageScaleX()).thenReturn(2.0);
    when(caseData.getWatsonImageScaleY()).thenReturn(2.0);
    GameEngine engine = new GameEngine(NO_PLAYERS, SILENT);
    engine.loadCase(caseData);
    engine.addRoom(new Room("Hall", "Hall description"));
    Room hall = engine.getRoomByName("Hall");
    engine.getWatson().setCurrentRoom(hall);

    common.dto.RoomDescriptionDTO dto = engine.buildRoomDescription(hall, null);

    assertEquals(
        "authored metadata.watsonImageScale should reach RoomView via spriteScales",
        Double.valueOf(2.0),
        dto.getSpriteScales().get("Dr. Watson"));
  }

  @Test
  public void roomDescriptionCarriesPerRoomWatsonPosition() {
    GameEngine engine = engineWithRooms("Hall", "Hall");
    Room hall = engine.getRoomByName("Hall");
    hall.setWatsonPosition(0.9, 0.5); // authored per-room Watson spot (Case Maker placement tab)
    engine.getWatson().setCurrentRoom(hall);

    common.dto.RoomDescriptionDTO dto = engine.buildRoomDescription(hall, null);

    common.dto.VisualPositionDTO pos = dto.getObjectPositions().get("Dr. Watson");
    assertNotNull("Watson's authored per-room position should reach RoomView", pos);
    assertEquals(0.9, pos.getX(), 1e-9);
    assertEquals(0.5, pos.getY(), 1e-9);
  }

  @Test
  public void roomDescriptionCarriesIndependentScalesAndFlips() {
    GameEngine engine = engineWithRooms("Hall", "Hall");
    Room hall = engine.getRoomByName("Hall");
    Suspect valet = new Suspect("valet", "Valet", "stmt", "clue");
    valet.setImageScaleX(2.0);
    valet.setImageScaleY(0.5);
    valet.setFlipX(true);
    valet.setCurrentRoom(hall);
    engine.addSuspect(valet);

    common.dto.RoomDescriptionDTO dto = engine.buildRoomDescription(hall, null);

    assertEquals(Double.valueOf(2.0), dto.getSpriteScales().get("Valet")); // horizontal
    assertEquals(Double.valueOf(0.5), dto.getSpriteScalesY().get("Valet")); // vertical
    common.dto.VisualPositionDTO flip = dto.getFlips().get("Valet");
    assertNotNull("flip flags should reach RoomView", flip);
    assertEquals(1.0, flip.getX(), 1e-9); // flipX
    assertEquals(0.0, flip.getY(), 1e-9); // flipY off
  }

  @Test
  public void roomDescriptionCarriesSuspectRotation() {
    GameEngine engine = engineWithRooms("Hall", "Hall");
    Room hall = engine.getRoomByName("Hall");
    Suspect valet = new Suspect("valet", "Valet", "stmt", "clue");
    valet.setRotation(30.0); // authored rotation (Case Maker placement rotation grips)
    valet.setCurrentRoom(hall);
    engine.addSuspect(valet);

    common.dto.RoomDescriptionDTO dto = engine.buildRoomDescription(hall, null);

    // Rotation rides on the per-element "flips" (visual transform) map.
    common.dto.VisualPositionDTO transform = dto.getFlips().get("Valet");
    assertNotNull("the suspect's rotation should reach RoomView", transform);
    assertEquals(Double.valueOf(30.0), transform.getRotation());
  }

  @Test
  public void roomDescriptionCarriesWatsonRotationFromMetadata() {
    CaseData caseData = mock(CaseData.class);
    when(caseData.getStartingRoom()).thenReturn("Hall");
    when(caseData.getWatsonRotation()).thenReturn(45.0);
    GameEngine engine = new GameEngine(NO_PLAYERS, SILENT);
    engine.loadCase(caseData);
    engine.addRoom(new Room("Hall", "Hall description"));
    Room hall = engine.getRoomByName("Hall");
    engine.getWatson().setCurrentRoom(hall);

    common.dto.RoomDescriptionDTO dto = engine.buildRoomDescription(hall, null);

    common.dto.VisualPositionDTO transform = dto.getFlips().get("Dr. Watson");
    assertNotNull("Watson's authored rotation should reach RoomView", transform);
    assertEquals(Double.valueOf(45.0), transform.getRotation());
  }

  @Test
  public void roomDescriptionCarriesAuthoredLabelOffsets() {
    GameEngine engine = engineWithRooms("Hall", "Hall");
    Room hall = engine.getRoomByName("Hall");
    Suspect valet = new Suspect("valet", "Valet", "stmt", "clue");
    valet.setLabelOffset(0.25, -0.5); // authored name-label offset (Case Maker placement tab)
    valet.setCurrentRoom(hall);
    engine.addSuspect(valet);

    common.dto.RoomDescriptionDTO dto = engine.buildRoomDescription(hall, null);

    common.dto.VisualPositionDTO off = dto.getLabelOffsets().get("Valet");
    assertNotNull("the suspect's label offset should reach RoomView", off);
    assertEquals(0.25, off.getX(), 1e-9);
    assertEquals(-0.5, off.getY(), 1e-9);
  }

  @Test
  public void roomDescriptionCarriesTheSuspectNormalizedPosition() {
    GameEngine engine = engineWithRooms("Hall", "Hall");
    Room hall = engine.getRoomByName("Hall");
    Suspect valet = new Suspect("valet", "Valet", "stmt", "clue");
    valet.setPosition(0.25, 0.6);
    valet.setCurrentRoom(hall);
    engine.addSuspect(valet);

    common.dto.RoomDescriptionDTO dto = engine.buildRoomDescription(hall, null);

    common.dto.VisualPositionDTO pos = dto.getObjectPositions().get("Valet");
    assertNotNull("the suspect's render position should reach RoomView", pos);
    assertEquals(0.25, pos.getX(), 1e-9);
    assertEquals(0.6, pos.getY(), 1e-9);
  }
}

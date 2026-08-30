package ui.casemaker.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/**
 * Behavioural tests for the Case Maker room-graph model (slice 1). These exercise the authoring
 * model through its public interface — linking rooms, renaming, and connectivity — independent of
 * any JavaFX UI.
 */
public class RoomGraphTest {

  @Test
  public void linkingRoomsIsBidirectional() {
    CaseDraft draft = new CaseDraft();
    RoomDraft ballroom = draft.addRoom("Ballroom");
    RoomDraft terrace = draft.addRoom("Terrace");

    draft.linkRooms(ballroom, "east", terrace);

    assertSame("east of Ballroom should be Terrace", terrace, ballroom.getNeighbors().get("east"));
    assertSame(
        "the reverse (west) link should be set automatically",
        ballroom,
        terrace.getNeighbors().get("west"));
  }

  @Test
  public void unlinkingRoomsClearsBothDirections() {
    CaseDraft draft = new CaseDraft();
    RoomDraft ballroom = draft.addRoom("Ballroom");
    RoomDraft terrace = draft.addRoom("Terrace");
    draft.linkRooms(ballroom, "east", terrace);

    draft.unlinkRooms(ballroom, terrace);

    assertTrue("Ballroom should have no neighbours left", ballroom.getNeighbors().isEmpty());
    assertTrue("Terrace should have no neighbours left", terrace.getNeighbors().isEmpty());
  }

  @Test
  public void renamingARoomIsReflectedThroughNeighbourReferencesAndStartingRoom() {
    CaseDraft draft = new CaseDraft();
    RoomDraft ballroom = draft.addRoom("Ballroom");
    RoomDraft terrace = draft.addRoom("Terrace");
    draft.linkRooms(ballroom, "east", terrace);
    draft.setStartingRoom(ballroom);

    draft.renameRoom(ballroom, "Grand Ballroom");

    assertEquals("Grand Ballroom", draft.getStartingRoom().getName());
    assertEquals(
        "Terrace's west neighbour should still resolve to the renamed room",
        "Grand Ballroom",
        terrace.getNeighbors().get("west").getName());
  }

  @Test
  public void connectedGraphHasNoUnreachableRooms() {
    CaseDraft draft = new CaseDraft();
    RoomDraft ballroom = draft.addRoom("Ballroom");
    RoomDraft terrace = draft.addRoom("Terrace");
    draft.linkRooms(ballroom, "east", terrace);
    draft.setStartingRoom(ballroom);

    assertTrue(draft.unreachableRooms().isEmpty());
    assertEquals(2, draft.reachableRooms().size());
  }

  @Test
  public void islandRoomIsReportedUnreachableFromTheStartingRoom() {
    CaseDraft draft = new CaseDraft();
    RoomDraft ballroom = draft.addRoom("Ballroom");
    RoomDraft terrace = draft.addRoom("Terrace");
    RoomDraft attic = draft.addRoom("Attic"); // deliberately unlinked island
    draft.linkRooms(ballroom, "east", terrace);
    draft.setStartingRoom(ballroom);

    List<RoomDraft> unreachable = draft.unreachableRooms();

    assertEquals(1, unreachable.size());
    assertSame(attic, unreachable.get(0));
    assertFalse(draft.reachableRooms().contains(attic));
  }

  @Test
  public void removingARoomDropsItFromTheCaseAndFromItsNeighbours() {
    CaseDraft draft = new CaseDraft();
    RoomDraft ballroom = draft.addRoom("Ballroom");
    RoomDraft terrace = draft.addRoom("Terrace");
    draft.linkRooms(ballroom, "east", terrace);

    draft.removeRoom(terrace);

    assertEquals(1, draft.getRooms().size());
    assertFalse(draft.getRooms().contains(terrace));
    assertTrue(
        "Ballroom should no longer link to the removed room", ballroom.getNeighbors().isEmpty());
  }

  @Test
  public void removingTheStartingRoomClearsTheStartingSelection() {
    CaseDraft draft = new CaseDraft();
    RoomDraft ballroom = draft.addRoom("Ballroom"); // first room → becomes starting room
    draft.addRoom("Terrace");

    draft.removeRoom(ballroom);

    assertNull(draft.getStartingRoom());
  }
}

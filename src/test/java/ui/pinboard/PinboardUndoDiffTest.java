package ui.pinboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import common.dto.pinboard.PinboardItemDTO;
import common.dto.pinboard.PinboardLinkDTO;
import common.dto.pinboard.PinboardStateDTO;
import common.dto.pinboard.PinboardUpdateDTO;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * Pins the undo/redo restore diff (.scratch/gui-pinboard-undo): the granular updates that turn one
 * board snapshot into another, in an order a peer can replay (cut orphaned threads before their
 * cards; re-string threads only after both endpoints exist).
 */
public class PinboardUndoDiffTest {

  private static PinboardItemDTO item(String id, double x, double y) {
    PinboardItemDTO dto = new PinboardItemDTO();
    dto.setId(id);
    dto.setType("NOTE");
    dto.setTitle("t-" + id);
    dto.setContent("c-" + id);
    dto.setX(x);
    dto.setY(y);
    dto.setWidth(200);
    dto.setHeight(150);
    return dto;
  }

  private static PinboardStateDTO state(List<PinboardItemDTO> items, List<PinboardLinkDTO> links) {
    PinboardStateDTO state = new PinboardStateDTO();
    state.setItems(items);
    state.setLinks(links);
    return state;
  }

  @Test
  public void identicalStatesProduceNoUpdates() {
    PinboardStateDTO a = state(List.of(item("a", 1, 2)), List.of());
    PinboardStateDTO b = state(List.of(item("a", 1, 2)), List.of());
    assertTrue(PinboardUndoDiff.diff(a, b).isEmpty());
  }

  @Test
  public void undoingAnAddRemovesTheItem() {
    PinboardStateDTO now = state(List.of(item("a", 1, 2), item("b", 3, 4)), List.of());
    PinboardStateDTO snapshot = state(List.of(item("a", 1, 2)), List.of());

    List<PinboardUpdateDTO> diff = PinboardUndoDiff.diff(now, snapshot);
    assertEquals(1, diff.size());
    assertEquals(PinboardUpdateDTO.UpdateType.REMOVE_ITEM, diff.get(0).getType());
    assertEquals("b", diff.get(0).getTargetId());
  }

  @Test
  public void undoingAMoveEmitsMoveBackToTheSnapshotPosition() {
    PinboardStateDTO now = state(List.of(item("a", 50, 60)), List.of());
    PinboardStateDTO snapshot = state(List.of(item("a", 1, 2)), List.of());

    List<PinboardUpdateDTO> diff = PinboardUndoDiff.diff(now, snapshot);
    assertEquals(1, diff.size());
    assertEquals(PinboardUpdateDTO.UpdateType.MOVE_ITEM, diff.get(0).getType());
    assertEquals("a", diff.get(0).getTargetId());
    assertEquals(1, diff.get(0).getNewX(), 0.0);
    assertEquals(2, diff.get(0).getNewY(), 0.0);
  }

  @Test
  public void resizeAndContentChangesEmitTheirOwnUpdates() {
    PinboardItemDTO before = item("a", 1, 2);
    PinboardItemDTO after = item("a", 1, 2);
    after.setWidth(300);
    after.setContent("edited");

    List<PinboardUpdateDTO> diff =
        PinboardUndoDiff.diff(
            state(new ArrayList<>(List.of(before)), List.of()),
            state(new ArrayList<>(List.of(after)), List.of()));
    assertEquals(2, diff.size());
    assertEquals(PinboardUpdateDTO.UpdateType.RESIZE_ITEM, diff.get(0).getType());
    assertEquals(300, diff.get(0).getItem().getWidth(), 0.0);
    assertEquals(PinboardUpdateDTO.UpdateType.UPDATE_CONTENT, diff.get(1).getType());
    assertEquals("edited", diff.get(1).getValue());
  }

  @Test
  public void undoingADeleteRestoresItemBeforeItsLink() {
    // Now: only "a" survives. Snapshot: "a"–"b" linked. Undo must re-add "b" BEFORE the link.
    PinboardStateDTO now = state(List.of(item("a", 1, 2)), List.of());
    PinboardStateDTO snapshot =
        state(
            List.of(item("a", 1, 2), item("b", 3, 4)),
            List.of(new PinboardLinkDTO("a", "b", "RED")));

    List<PinboardUpdateDTO> diff = PinboardUndoDiff.diff(now, snapshot);
    assertEquals(
        Arrays.asList(PinboardUpdateDTO.UpdateType.ADD_ITEM, PinboardUpdateDTO.UpdateType.ADD_LINK),
        diff.stream()
            .map(PinboardUpdateDTO::getType)
            .collect(java.util.stream.Collectors.toList()));
  }

  @Test
  public void redoingADeleteCutsTheLinkBeforeTheItem() {
    PinboardStateDTO now =
        state(
            List.of(item("a", 1, 2), item("b", 3, 4)),
            List.of(new PinboardLinkDTO("a", "b", "RED")));
    PinboardStateDTO snapshot = state(List.of(item("a", 1, 2)), List.of());

    List<PinboardUpdateDTO> diff = PinboardUndoDiff.diff(now, snapshot);
    assertEquals(
        Arrays.asList(
            PinboardUpdateDTO.UpdateType.REMOVE_LINK, PinboardUpdateDTO.UpdateType.REMOVE_ITEM),
        diff.stream()
            .map(PinboardUpdateDTO::getType)
            .collect(java.util.stream.Collectors.toList()));
  }

  @Test
  public void aRecolouredLinkIsCutAndRestrung() {
    PinboardStateDTO now =
        state(
            List.of(item("a", 1, 2), item("b", 3, 4)),
            List.of(new PinboardLinkDTO("a", "b", "RED")));
    PinboardStateDTO snapshot =
        state(
            List.of(item("a", 1, 2), item("b", 3, 4)),
            List.of(new PinboardLinkDTO("a", "b", "GREEN")));

    List<PinboardUpdateDTO> diff = PinboardUndoDiff.diff(now, snapshot);
    assertEquals(2, diff.size());
    assertEquals(PinboardUpdateDTO.UpdateType.REMOVE_LINK, diff.get(0).getType());
    assertEquals(PinboardUpdateDTO.UpdateType.ADD_LINK, diff.get(1).getType());
    assertEquals("GREEN", diff.get(1).getLink().getColor());
  }

  @Test
  public void nullItemAndLinkListsAreSafe() {
    assertTrue(PinboardUndoDiff.diff(new PinboardStateDTO(), new PinboardStateDTO()).isEmpty());
  }
}

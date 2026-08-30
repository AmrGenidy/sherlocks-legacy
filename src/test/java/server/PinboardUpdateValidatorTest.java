package server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import common.WireLimits;
import common.dto.pinboard.PinboardItemDTO;
import common.dto.pinboard.PinboardLinkDTO;
import common.dto.pinboard.PinboardStateDTO;
import common.dto.pinboard.PinboardUpdateDTO;
import java.util.ArrayList;
import org.junit.Test;

/**
 * Server-side bounds on client pinboard updates (security-pass issue 02): geometry must be finite
 * and bounded, free text capped, and the board's item/link collections must not grow without
 * limit.
 */
public class PinboardUpdateValidatorTest {

  private static PinboardItemDTO item(String id) {
    PinboardItemDTO item = new PinboardItemDTO();
    item.setId(id);
    item.setType("NOTE");
    item.setTitle("a note");
    item.setContent("hello");
    item.setX(10);
    item.setY(20);
    item.setWidth(100);
    item.setHeight(80);
    return item;
  }

  private static PinboardUpdateDTO addItem(PinboardItemDTO item) {
    PinboardUpdateDTO update = new PinboardUpdateDTO(PinboardUpdateDTO.UpdateType.ADD_ITEM);
    update.setItem(item);
    return update;
  }

  @Test
  public void wellFormedAddItemIsAccepted() {
    assertTrue(PinboardUpdateValidator.isAcceptable(addItem(item("n1")), new PinboardStateDTO()));
  }

  @Test
  public void nullUpdateOrTypeIsRejected() {
    assertFalse(PinboardUpdateValidator.isAcceptable(null, new PinboardStateDTO()));
    assertFalse(
        PinboardUpdateValidator.isAcceptable(new PinboardUpdateDTO(), new PinboardStateDTO()));
  }

  @Test
  public void nonFiniteCoordinatesAreRejected() {
    PinboardItemDTO poisoned = item("n1");
    poisoned.setX(Double.NaN);
    assertFalse(PinboardUpdateValidator.isAcceptable(addItem(poisoned), new PinboardStateDTO()));

    PinboardUpdateDTO move = new PinboardUpdateDTO(PinboardUpdateDTO.UpdateType.MOVE_ITEM);
    move.setTargetId("n1");
    move.setNewX(Double.POSITIVE_INFINITY);
    assertFalse(PinboardUpdateValidator.isAcceptable(move, new PinboardStateDTO()));
  }

  @Test
  public void hugeCoordinatesAreRejected() {
    PinboardItemDTO offscreen = item("n1");
    offscreen.setY(WireLimits.MAX_PINBOARD_COORD * 2);
    assertFalse(PinboardUpdateValidator.isAcceptable(addItem(offscreen), new PinboardStateDTO()));
  }

  @Test
  public void oversizedContentIsRejected() {
    PinboardItemDTO bloated = item("n1");
    bloated.setContent("x".repeat(WireLimits.MAX_NOTE_TEXT_LENGTH + 1));
    assertFalse(PinboardUpdateValidator.isAcceptable(addItem(bloated), new PinboardStateDTO()));

    PinboardUpdateDTO content = new PinboardUpdateDTO(PinboardUpdateDTO.UpdateType.UPDATE_CONTENT);
    content.setTargetId("n1");
    content.setValue("x".repeat(WireLimits.MAX_NOTE_TEXT_LENGTH + 1));
    assertFalse(PinboardUpdateValidator.isAcceptable(content, new PinboardStateDTO()));
  }

  @Test
  public void addItemIsRejectedOnceTheBoardIsFull() {
    PinboardStateDTO state = new PinboardStateDTO();
    state.setItems(new ArrayList<>());
    for (int i = 0; i < WireLimits.MAX_PINBOARD_ITEMS; i++) {
      state.getItems().add(item("n" + i));
    }

    assertFalse(PinboardUpdateValidator.isAcceptable(addItem(item("overflow")), state));
    // Non-growing updates remain fine on a full board.
    PinboardUpdateDTO remove = new PinboardUpdateDTO(PinboardUpdateDTO.UpdateType.REMOVE_ITEM);
    remove.setTargetId("n0");
    assertTrue(PinboardUpdateValidator.isAcceptable(remove, state));
  }

  @Test
  public void addLinkIsRejectedOnceLinksAreFull() {
    PinboardStateDTO state = new PinboardStateDTO();
    state.setLinks(new ArrayList<>());
    for (int i = 0; i < WireLimits.MAX_PINBOARD_LINKS; i++) {
      state.getLinks().add(new PinboardLinkDTO("a" + i, "b" + i, "RED"));
    }
    PinboardUpdateDTO addLink = new PinboardUpdateDTO(PinboardUpdateDTO.UpdateType.ADD_LINK);
    addLink.setLink(new PinboardLinkDTO("a", "b", "RED"));

    assertFalse(PinboardUpdateValidator.isAcceptable(addLink, state));
  }
}

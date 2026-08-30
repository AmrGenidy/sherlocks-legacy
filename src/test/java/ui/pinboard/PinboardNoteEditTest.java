package ui.pinboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import common.dto.pinboard.PinboardItemDTO;
import common.dto.pinboard.PinboardUpdateDTO;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Pins the inert-note-body interaction model (.scratch/gui-pinboard-note-clicks): a note's TextArea
 * is mouse-transparent and non-editable by default (so clicks anywhere on the card select / pick in
 * Link mode), a double-click on the card starts an edit, and clicking the bare cork commits it —
 * broadcasting through the normal UPDATE_CONTENT path — and returns the body to inert. Read-only
 * review never becomes editable.
 */
public class PinboardNoteEditTest {

  private static PinboardController board;
  private static List<PinboardUpdateDTO> sent;

  @BeforeClass
  public static void initJfx() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    try {
      Platform.startup(latch::countDown);
    } catch (IllegalStateException already) {
      latch.countDown();
    }
    assertTrue("JavaFX did not start", latch.await(5, TimeUnit.SECONDS));
  }

  @Before
  public void freshBoard() throws Exception {
    onFx(
        () -> {
          board = new PinboardController();
          sent = new ArrayList<>();
          board.setOnUpdateCallback(sent::add);
        });
  }

  private static void onFx(Runnable action) throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    Throwable[] error = new Throwable[1];
    Platform.runLater(
        () -> {
          try {
            action.run();
          } catch (Throwable t) {
            error[0] = t;
          } finally {
            done.countDown();
          }
        });
    assertTrue("FX task timed out", done.await(5, TimeUnit.SECONDS));
    if (error[0] != null) {
      throw new AssertionError(error[0]);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T field(String name) throws Exception {
    Field f = PinboardController.class.getDeclaredField(name);
    f.setAccessible(true);
    return (T) f.get(board);
  }

  /** Adds a plain note through the public peer-update path (no auto-edit) and returns its card. */
  private static Node addNoteCard(String id) throws Exception {
    PinboardItemDTO dto = new PinboardItemDTO();
    dto.setId(id);
    dto.setType("NOTE");
    dto.setTitle("note");
    dto.setContent("original text");
    dto.setX(50);
    dto.setY(50);
    dto.setWidth(200);
    dto.setHeight(150);
    PinboardUpdateDTO update = new PinboardUpdateDTO(PinboardUpdateDTO.UpdateType.ADD_ITEM);
    update.setItem(dto);
    board.applyUpdate(update); // enqueues on the FX thread
    Node[] card = new Node[1];
    onFx(
        () -> {
          try {
            Map<String, Node> nodes = field("itemNodeMap");
            card[0] = nodes.get(id);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
    assertTrue("card was not created", card[0] != null);
    return card[0];
  }

  private static TextArea bodyOf(Node card) {
    return (TextArea) card.lookup(".pinboard-item-content");
  }

  private static void fireClick(Node target, int clickCount) throws Exception {
    onFx(
        () ->
            Event.fireEvent(
                target,
                new MouseEvent(
                    MouseEvent.MOUSE_CLICKED,
                    0,
                    0,
                    0,
                    0,
                    MouseButton.PRIMARY,
                    clickCount,
                    false,
                    false,
                    false,
                    false,
                    true,
                    false,
                    false,
                    false,
                    false,
                    true,
                    null)));
  }

  @Test
  public void noteBodyStartsInertSoClicksReachTheCard() throws Exception {
    Node card = addNoteCard("n1");
    onFx(
        () -> {
          TextArea body = bodyOf(card);
          assertTrue("body must let clicks pass through to the card", body.isMouseTransparent());
          assertFalse("body must not be editable until an edit begins", body.isEditable());
          assertFalse("body must not take Tab focus", body.isFocusTraversable());
        });
  }

  @Test
  public void doubleClickStartsEditingAndEscapeCommitsAndReverts() throws Exception {
    Node card = addNoteCard("n1");
    fireClick(card, 2);
    onFx(
        () -> {
          TextArea body = bodyOf(card);
          assertTrue("double-click must make the body editable", body.isEditable());
          assertFalse("editable body must take the mouse again", body.isMouseTransparent());
          body.setText("edited text");
        });

    onFx(() -> sent.clear());
    onFx(
        () ->
            Event.fireEvent(
                bodyOf(card),
                new KeyEvent(
                    KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false)));
    onFx(
        () -> {
          TextArea body = bodyOf(card);
          assertFalse("Escape must end the edit", body.isEditable());
          assertTrue("ended edit must go back to inert", body.isMouseTransparent());
        });
    assertEquals(
        "ending the edit must commit through the normal broadcast path",
        PinboardUpdateDTO.UpdateType.UPDATE_CONTENT,
        sent.get(0).getType());
    assertEquals("edited text", sent.get(0).getValue());
  }

  @Test
  public void selectingAnotherCardCommitsTheEditViaDeselection() throws Exception {
    Node first = addNoteCard("n1");
    Node second = addNoteCard("n2");
    fireClick(first, 2);
    onFx(() -> bodyOf(first).setText("changed"));

    onFx(() -> sent.clear());
    fireClick(second, 1); // plain-selects n2 → deselects n1 → its edit commits
    onFx(
        () -> {
          assertFalse("deselection must end the edit", bodyOf(first).isEditable());
          assertTrue("ended edit must go back to inert", bodyOf(first).isMouseTransparent());
        });
    assertEquals(PinboardUpdateDTO.UpdateType.UPDATE_CONTENT, sent.get(0).getType());
    assertEquals("changed", sent.get(0).getValue());
  }

  @Test
  public void aNoOpEditCommitsNothing() throws Exception {
    Node card = addNoteCard("n1");
    fireClick(card, 2);
    onFx(() -> sent.clear());
    onFx(
        () ->
            Event.fireEvent(
                bodyOf(card),
                new KeyEvent(
                    KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false)));
    onFx(
        () -> {
          assertTrue("a no-op edit must not hit the wire", sent.isEmpty());
          assertTrue(bodyOf(card).isMouseTransparent());
        });
  }

  @Test
  public void readOnlyReviewNeverBecomesEditable() throws Exception {
    onFx(() -> board.setReadOnly(true));
    Node card = addNoteCard("n1");
    fireClick(card, 2);
    onFx(
        () -> {
          TextArea body = bodyOf(card);
          assertFalse("review must never edit a note body", body.isEditable());
          assertTrue("review body stays inert", body.isMouseTransparent());
        });
  }
}

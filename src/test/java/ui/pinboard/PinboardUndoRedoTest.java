package ui.pinboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import common.dto.pinboard.PinboardUpdateDTO;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Drives the real Pinboard undo/redo (.scratch/gui-pinboard-undo) end to end: real scene, real
 * Ctrl+Z / Ctrl+Shift+Z / Ctrl+Y key events through the scene's key filter. Verifies the memento
 * behaviour (undo back to the start, redo forward), that a restore broadcasts through the normal
 * update callback (multiplayer peers must follow an undo), and that read-only review ignores the
 * keys.
 */
public class PinboardUndoRedoTest {

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

  /** Adds a note through the real toolbar path (the private Add Note action). */
  private static void addNote() throws Exception {
    Method create = PinboardController.class.getDeclaredMethod("createNoteAtCenter");
    create.setAccessible(true);
    onFx(
        () -> {
          try {
            create.invoke(board);
          } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
          }
        });
  }

  /** Fires a Ctrl(+Shift) key press into the board's scene, through the real key filter. */
  private static void fireCtrlKey(KeyCode code, boolean shift) throws Exception {
    onFx(
        () -> {
          Scene scene = board.getStage().getScene();
          Event.fireEvent(
              scene.getRoot(),
              new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, true, false, false));
        });
  }

  private static int itemCount() throws Exception {
    int[] count = new int[1];
    onFx(() -> count[0] = board.getState().getItems().size());
    return count[0];
  }

  @Test
  public void ctrlZWalksBackToTheStartAndBothRedoCombosWalkForward() throws Exception {
    addNote();
    addNote();
    assertEquals(2, itemCount());

    fireCtrlKey(KeyCode.Z, false);
    assertEquals(1, itemCount());
    fireCtrlKey(KeyCode.Z, false);
    assertEquals("Ctrl+Z must walk all the way back to the empty board", 0, itemCount());
    fireCtrlKey(KeyCode.Z, false);
    assertEquals("an exhausted undo stack is a no-op", 0, itemCount());

    fireCtrlKey(KeyCode.Z, true); // Ctrl+Shift+Z
    assertEquals(1, itemCount());
    fireCtrlKey(KeyCode.Y, false); // Ctrl+Y is redo too
    assertEquals(2, itemCount());
  }

  @Test
  public void aNewEditForksHistoryAndDiscardsRedo() throws Exception {
    addNote();
    fireCtrlKey(KeyCode.Z, false);
    assertEquals(0, itemCount());
    addNote(); // forks history: the undone add must no longer be redoable
    fireCtrlKey(KeyCode.Y, false);
    assertEquals("redo after a fresh edit must be a no-op", 1, itemCount());
  }

  @Test
  public void undoBroadcastsThroughTheNormalUpdatePath() throws Exception {
    addNote();
    onFx(() -> sent.clear());
    fireCtrlKey(KeyCode.Z, false);
    assertFalse("an undo must broadcast, never mutate the board silently", sent.isEmpty());
    assertEquals(PinboardUpdateDTO.UpdateType.REMOVE_ITEM, sent.get(0).getType());
  }

  @Test
  public void readOnlyReviewIgnoresUndoKeys() throws Exception {
    addNote();
    onFx(() -> board.setReadOnly(true));
    fireCtrlKey(KeyCode.Z, false);
    assertEquals("read-only review must ignore Ctrl+Z", 1, itemCount());
  }
}

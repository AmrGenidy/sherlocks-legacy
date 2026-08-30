package ui.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Locks the terminal's clean rendering (.scratch/terminal-line-highlight): transcript lines are
 * coloured TEXT on the uniform well with NO per-line background box. Renders a real {@link
 * TerminalView} with the real stylesheet, then snapshots and samples the pixels behind the text — a
 * per-line / flow background would make the line band differ from the well colour and fail here.
 */
public class TerminalCleanRenderTest {

  private static final int WELL_LIGHT = 0xFFE6D8B4; // -sl-terminal-bg (light)
  private static final int WELL_DARK = 0xFF1A1611; // -sl-terminal-bg (dark)

  @BeforeClass
  public static void initJfx() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    try {
      Platform.startup(latch::countDown);
    } catch (IllegalStateException already) {
      latch.countDown();
    }
    assertTrue(latch.await(5, TimeUnit.SECONDS));
  }

  @Test
  public void lightMode_textIsCleanOnTheWell_noPerLineBox() throws Exception {
    assertCleanWell(false, WELL_LIGHT);
  }

  @Test
  public void darkMode_textIsCleanOnTheWell_noPerLineBox() throws Exception {
    assertCleanWell(true, WELL_DARK);
  }

  @Test
  public void defaultText_rendersInk_notPetrol() throws Exception {
    // Ordinary narrative (a room description is classified NORMAL) must be the body ink, never petrol
    // (.scratch/terminal-default-colour).
    assertDefaultFill(false, Color.web("#241E17")); // ink (light)
    assertDefaultFill(true, Color.web("#E8D4A8")); // lamp-lit ochre (dark)
  }

  private void assertDefaultFill(boolean dark, Color expected) throws Exception {
    onFx(
        () -> {
          TerminalView v = newView(dark);
          v.appendLine("A grand ballroom, lined with shelves.\n", TerminalLineKind.NORMAL);
          v.applyCss();
          v.layout();
          Text t = (Text) ((TextFlow) v.getContent()).getChildren().get(0);
          assertEquals(
              "default terminal text must be the body ink, not petrol", expected, t.getFill());
        });
  }

  @Test
  public void linesAreTextNodesWithoutWrappers() throws Exception {
    onFx(
        () -> {
          TerminalView v = newView(false);
          v.appendLine("a system line\n", TerminalLineKind.SYSTEM);
          v.appendLine("an error line\n", TerminalLineKind.ERROR);
          v.applyCss();
          v.layout();
          TextFlow flow = (TextFlow) v.getContent();
          for (Node child : flow.getChildren()) {
            assertTrue(
                "every transcript line must be a plain Text node (no Region/Label that could carry a"
                    + " background), was " + child.getClass().getName(),
                child instanceof Text);
          }
        });
  }

  private void assertCleanWell(boolean dark, int wellArgb) throws Exception {
    int[] mismatch = new int[] {-1, 0, 0}; // y, sampled, expected
    onFx(
        () -> {
          TerminalView v = newView(dark);
          // A multi-line block (room description) plus other kinds — the exact content the report shows.
          v.appendLine("--- Ballroom ---\nObjects: vase\nExits: east (to Terrace)\n", TerminalLineKind.SYSTEM);
          v.appendLine("You move east.\n", TerminalLineKind.NORMAL);
          v.appendLine("Contradiction confirmed!\n", TerminalLineKind.ERROR);
          v.applyCss();
          v.layout();

          WritableImage img = v.snapshot(null, null);
          PixelReader pr = img.getPixelReader();
          int w = (int) img.getWidth();
          int h = (int) img.getHeight();
          // Sample the right margin (no glyphs there; the lines are short), inside the well and clear
          // of the scroll bar gutter. A full-width per-line highlight box would colour these pixels.
          int x = Math.min(w - 60, 300);
          for (int y = 10; y < Math.min(h - 10, 90); y += 4) {
            int px = pr.getArgb(x, y);
            if (px != wellArgb) {
              mismatch[0] = y;
              mismatch[1] = px;
              mismatch[2] = wellArgb;
              break;
            }
          }
        });
    assertEquals(
        "the area behind the text must be the bare well colour (no per-line highlight box) at y="
            + mismatch[0]
            + " — sampled 0x"
            + Integer.toHexString(mismatch[1])
            + " expected 0x"
            + Integer.toHexString(mismatch[2]),
        -1,
        mismatch[0]);
  }

  private static TerminalView newView(boolean dark) {
    TerminalView v = new TerminalView();
    Scene scene = new Scene(v, 360, 160);
    scene.getStylesheets().add(ui.util.Theme.baseStylesheet());
    if (dark) {
      scene.getStylesheets().add(ui.util.Theme.darkStylesheet());
    }
    v.applyCss();
    v.layout();
    return v;
  }

  private interface FxTask {
    void run() throws Exception;
  }

  private static void onFx(FxTask task) throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    Throwable[] err = new Throwable[1];
    Platform.runLater(
        () -> {
          try {
            task.run();
          } catch (Throwable t) {
            err[0] = t;
          } finally {
            done.countDown();
          }
        });
    assertTrue("FX task timed out", done.await(10, TimeUnit.SECONDS));
    if (err[0] instanceof AssertionError ae) {
      throw ae;
    } else if (err[0] != null) {
      throw new RuntimeException(err[0]);
    }
  }
}

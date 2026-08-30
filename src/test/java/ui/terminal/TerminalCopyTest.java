package ui.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Copy support for the read-only {@link TerminalView} transcript: the whole transcript reconstructs
 * verbatim from the per-line {@code Text} nodes (Copy all), each line keeps its {@link
 * TerminalLineKind} ink class (colours untouched by copy), and Ctrl+C lands the transcript on the
 * system clipboard.
 */
public class TerminalCopyTest {

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

  @Test
  public void transcriptReconstructsVerbatim_andKeepsPerLineInk() throws Exception {
    TerminalView v = build();
    onFx(
        () -> {
          v.appendLine("--- Study ---\n", TerminalLineKind.SYSTEM);
          v.appendLine("Contradiction successful! +1 Insight Token\n", TerminalLineKind.SUCCESS);
          v.appendLine("No contradiction found.\n", TerminalLineKind.ERROR);
        });
    pump(3);

    assertEquals(
        "--- Study ---\nContradiction successful! +1 Insight Token\nNo contradiction found.\n",
        transcript(v));

    // Per-line ink classes are still present — copying never touched the nodes' styling.
    onFx(
        () -> {
          TextFlow flow = (TextFlow) v.getContent();
          assertTrue(text(flow, 0).getStyleClass().contains(TerminalLineKind.SYSTEM.cssClass()));
          assertTrue(text(flow, 1).getStyleClass().contains(TerminalLineKind.SUCCESS.cssClass()));
          assertTrue(text(flow, 2).getStyleClass().contains(TerminalLineKind.ERROR.cssClass()));
          assertTrue(text(flow, 0).getStyleClass().contains("terminal-line"));
        });
  }

  @Test
  public void ctrlC_copiesWholeTranscriptToClipboard() throws Exception {
    TerminalView v = build();
    onFx(
        () -> {
          v.appendLine("first line\n", TerminalLineKind.NORMAL);
          v.appendLine("second line\n", TerminalLineKind.NORMAL);
        });
    pump(2);

    String expected = transcript(v);
    onFx(
        () ->
            v.fireEvent(
                new KeyEvent(
                    KeyEvent.KEY_PRESSED,
                    "",
                    "",
                    KeyCode.C,
                    false,
                    true, // controlDown -> shortcutDown on Windows/Linux
                    false,
                    false)));
    pump(2);

    String[] clip = new String[1];
    onFx(() -> clip[0] = Clipboard.getSystemClipboard().getString());
    assertEquals("Ctrl+C must copy the whole transcript", expected, clip[0]);
  }

  // ---- helpers ----

  private static Text text(TextFlow flow, int i) {
    return (Text) flow.getChildren().get(i);
  }

  private static String transcript(TerminalView v) throws Exception {
    String[] out = new String[1];
    onFx(() -> out[0] = v.transcriptText());
    return out[0];
  }

  private static TerminalView build() throws Exception {
    TerminalView[] tv = new TerminalView[1];
    onFx(
        () -> {
          TerminalView v = new TerminalView();
          Scene scene = new Scene(v, 240, 100);
          scene.getRoot().applyCss();
          v.applyCss();
          v.layout();
          tv[0] = v;
        });
    pump(2);
    return tv[0];
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
    if (err[0] != null) {
      throw new RuntimeException(err[0]);
    }
  }

  private static void pump(int times) throws Exception {
    for (int i = 0; i < times; i++) {
      onFx(() -> {});
    }
  }
}

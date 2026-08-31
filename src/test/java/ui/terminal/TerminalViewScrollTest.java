package ui.terminal;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Headless regression for the terminal auto-scroll that is shared by single-player AND multiplayer
 * (.scratch/terminal-scroll-mp). Drives a real {@link TerminalView} and asserts the actual {@code
 * vvalue} after layout. The multiplayer path is exercised end-to-end in {@link #realGameClientDispatch_scrolls()}
 * by pushing a real server DTO through the production {@code GameClient.processServerMessage} on a
 * network-like thread, and over a real socket in {@code TerminalScrollMpSocketTest}.
 */
public class TerminalViewScrollTest {

  /** "At the bottom" — the view's own AT_BOTTOM_EPSILON is 0.02, so this is a lenient check. */
  private static final double AT_BOTTOM = 0.95;

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
  public void onThreadAppend_scrollsToBottom() throws Exception {
    TerminalView v = build();
    appendN(v, 0, 40);
    double vv = vvalue(v);
    assertTrue("on-thread append must auto-scroll to bottom, was " + vv, vv >= AT_BOTTOM);
  }

  @Test
  public void offThreadAppend_viaMarshal_scrollsToBottom() throws Exception {
    TerminalView v = build();
    appendN(v, 0, 5);
    Thread net =
        new Thread(
            () -> {
              for (int i = 0; i < 30; i++) {
                final int n = i;
                Platform.runLater(() -> v.appendLine("net " + n + " yyyyyyyyyyyyyy", TerminalLineKind.NORMAL));
              }
            });
    net.start();
    net.join();
    pump(8);
    double vv = vvalue(v);
    assertTrue("off-thread (marshalled) append must auto-scroll to bottom, was " + vv, vv >= AT_BOTTOM);
  }

  @Test
  public void scrollUpSuppressesPassive_butResetReturnsToBottom() throws Exception {
    TerminalView v = build();
    appendN(v, 0, 40);
    assertTrue(vvalue(v) >= AT_BOTTOM);

    // User scrolls up to read history.
    onFx(() -> v.setVvalue(0.2));
    pump(2);

    // Passive/background output must NOT yank the reader down.
    appendN(v, 40, 10);
    assertTrue("passive output must leave a scrolled-up reader put", vvalue(v) < 0.9);

    // The user submits a command -> reset to bottom AT SEND TIME (before any async response).
    onFx(v::repinToBottom);
    pump(3);
    assertTrue("submitting a command must re-pin to the bottom", vvalue(v) >= AT_BOTTOM);

    // The async (MP) response lands later and follows.
    appendN(v, 50, 10);
    assertTrue("async response after a re-pin must stay at the bottom", vvalue(v) >= AT_BOTTOM);
  }

  @Test
  public void multiLineBlock_scrollsToBottom() throws Exception {
    // A room change / look appends a tall MULTI-LINE block in one go; its first line must be pulled
    // fully into view (.scratch/terminal-scroll-mp issue 04).
    TerminalView v = build();
    appendN(v, 0, 10);
    StringBuilder block = new StringBuilder();
    for (int i = 0; i < 14; i++) {
      block.append("--- Library ---  line ").append(i).append(" of a tall room description\n");
    }
    onFx(() -> v.appendLine(block.toString(), TerminalLineKind.SYSTEM));
    pump(8);
    double vv = vvalue(v);
    assertTrue("a tall multi-line block must auto-scroll to the bottom, was " + vv, vv >= AT_BOTTOM);
  }

  @Test
  public void growthDropDuringMultiLineBlock_keepsAutoScroll() throws Exception {
    // Reproduces the real-GUI failure deterministically: a tall block grows the content and the
    // ScrollPane drops vvalue (keeps pixel scrollTop). Simulate that drop BEFORE the deferred scroll
    // runs (so lastScrolledHeight is still the pre-block height). With the growth-BLIND rule this
    // unpins and strands the block; growth-aware keeps the pin and the height listener scrolls.
    TerminalView v = build();
    appendN(v, 0, 12); // establish a scrolled-to-bottom baseline (lastScrolledHeight = small)

    StringBuilder block = new StringBuilder();
    for (int i = 0; i < 16; i++) {
      block.append("tall block line ").append(i).append(" xxxxxxxxxxxxxxxxxxxx\n");
    }
    onFx(
        () -> {
          v.appendLine(block.toString(), TerminalLineKind.SYSTEM); // grows content; schedules scroll
          v.applyCss();
          v.layout(); // realise the taller height now (lastScrolledHeight not yet updated)
          v.setVvalue(0.3); // simulate the ScrollPane's growth-induced vvalue DROP
        });
    pump(8);
    double vv = vvalue(v);
    assertTrue(
        "a growth-induced vvalue drop must not disengage auto-scroll for a multi-line block, was " + vv,
        vv >= AT_BOTTOM);
  }

  @Test
  public void realGameClientDispatch_scrolls() throws Exception {
    TerminalView v = build();
    appendN(v, 0, 10);

    client.GameClient gc =
        new client.GameClient(
            "localhost", 5000, guiWriter(v), client.GameClient.LaunchMode.NORMAL, null);
    java.lang.reflect.Method dispatch =
        client.GameClient.class.getDeclaredMethod("processServerMessage", Object.class);
    dispatch.setAccessible(true);

    common.dto.RoomDescriptionDTO room =
        new common.dto.RoomDescriptionDTO(
            "Library",
            "A long room lined with shelves. ".repeat(6),
            java.util.List.of("ledger", "candlestick"),
            java.util.List.of("Colonel Hastings"),
            new java.util.LinkedHashMap<>(java.util.Map.of("north", "Hall")));

    // Real production dispatch, on a network-like thread: processServerMessage -> printToConsole ->
    // consoleWriter -> appendTerminalText(marshal) -> appendLine -> scroll.
    Thread net =
        new Thread(
            () -> {
              try {
                for (int i = 0; i < 8; i++) {
                  dispatch.invoke(gc, room);
                }
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
    net.start();
    net.join();
    pump(8);
    double vv = vvalue(v);
    assertTrue("a server-pushed room description must auto-scroll the terminal, was " + vv, vv >= AT_BOTTOM);
  }

  // ---- helpers ----

  /** Faithful replica of MainController.appendTerminalText: marshal to FX, then appendLine. */
  private static java.util.function.Consumer<String> guiWriter(TerminalView v) {
    return line -> {
      if (!Platform.isFxApplicationThread()) {
        Platform.runLater(() -> v.appendLine(line, TerminalLineKind.NORMAL));
      } else {
        v.appendLine(line, TerminalLineKind.NORMAL);
      }
    };
  }

  private static TerminalView build() throws Exception {
    TerminalView[] tv = new TerminalView[1];
    onFx(
        () -> {
          TerminalView v = new TerminalView();
          v.setPrefViewportHeight(80);
          v.setPrefViewportWidth(220);
          v.setMinHeight(80);
          v.setMaxHeight(80);
          Scene scene = new Scene(v, 220, 80);
          scene.getRoot().applyCss();
          v.applyCss();
          v.layout();
          tv[0] = v;
        });
    pump(3);
    return tv[0];
  }

  private static void appendN(TerminalView v, int from, int count) throws Exception {
    for (int i = from; i < from + count; i++) {
      final int n = i;
      onFx(() -> v.appendLine("line " + n + " xxxxxxxxxxxxxxxxxxxx", TerminalLineKind.NORMAL));
    }
    pump(4);
  }

  private static double vvalue(TerminalView v) throws Exception {
    double[] out = new double[1];
    onFx(() -> out[0] = v.getVvalue());
    return out[0];
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

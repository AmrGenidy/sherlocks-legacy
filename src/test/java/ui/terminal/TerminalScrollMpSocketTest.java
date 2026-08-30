package ui.terminal;

import static org.junit.Assert.assertTrue;

import client.GameClient;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.text.TextFlow;
import org.junit.BeforeClass;
import org.junit.Test;
import server.GameServer;

/**
 * Real local multiplayer regression (.scratch/terminal-scroll-mp): a REAL {@link GameServer} + REAL
 * {@link GameClient} over a REAL localhost socket, wired exactly as the GUI (the client's {@code
 * consoleWriter} marshals to the FX thread and appends to a real {@link TerminalView}). Asserts that
 * server-pushed output over the socket auto-scrolls the terminal — the multiplayer path the headless
 * single-component tests cannot cover. Verified host-and-guest are symmetric (both use the same
 * {@code consoleWriter} wiring via {@code LobbyController.connect}).
 */
public class TerminalScrollMpSocketTest {

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

  @Test(timeout = 30_000)
  public void realServerPushedOutput_autoScrollsTerminal() throws Exception {
    int port;
    try (ServerSocket s = new ServerSocket(0)) {
      port = s.getLocalPort();
    }

    GameServer server = new GameServer(port);
    server.startServer();
    Thread serverThread = new Thread(server::run, "test-mp-server");
    serverThread.setDaemon(true);
    serverThread.start();

    TerminalView v = buildView();

    GameClient client =
        new GameClient(
            "localhost",
            port,
            line -> {
              if (!Platform.isFxApplicationThread()) {
                Platform.runLater(() -> v.appendLine(line, TerminalLineKind.NORMAL));
              } else {
                v.appendLine(line, TerminalLineKind.NORMAL);
              }
            },
            GameClient.LaunchMode.NORMAL,
            null);
    client.setGameTexts(new ui.i18n.L10nGameTexts());
    Thread clientThread = new Thread(client::run, "test-mp-client");
    clientThread.setDaemon(true);

    try {
      clientThread.start();

      // Wait for the real socket round-trip to push enough output to overflow the viewport.
      long deadline = System.currentTimeMillis() + 15_000;
      boolean ready = false;
      while (System.currentTimeMillis() < deadline) {
        if (lineCount(v) >= 3 && contentOverflows(v)) {
          ready = true;
          break;
        }
        Thread.sleep(100);
      }
      pump(6);
      assertTrue("server-pushed output did not reach the terminal over the socket", ready);
      double vv = vvalue(v);
      assertTrue("multiplayer server-pushed output must auto-scroll the terminal, was " + vv, vv >= 0.95);
    } finally {
      client.stopClient();
      server.stopServer();
      clientThread.interrupt();
    }
  }

  // ---- helpers ----

  private static TerminalView buildView() throws Exception {
    TerminalView[] tv = new TerminalView[1];
    onFx(
        () -> {
          TerminalView v = new TerminalView();
          v.setPrefViewportHeight(60);
          v.setPrefViewportWidth(220);
          v.setMinHeight(60);
          v.setMaxHeight(60);
          Scene scene = new Scene(v, 220, 60);
          scene.getRoot().applyCss();
          v.applyCss();
          v.layout();
          tv[0] = v;
        });
    pump(2);
    return tv[0];
  }

  private static int lineCount(TerminalView v) throws Exception {
    int[] n = new int[1];
    onFx(() -> n[0] = ((TextFlow) v.getContent()).getChildren().size());
    return n[0];
  }

  private static boolean contentOverflows(TerminalView v) throws Exception {
    double[] d = new double[2];
    onFx(
        () -> {
          d[0] = v.getContent().getBoundsInLocal().getHeight();
          d[1] = v.getViewportBounds().getHeight();
        });
    return d[0] > d[1] + 1;
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

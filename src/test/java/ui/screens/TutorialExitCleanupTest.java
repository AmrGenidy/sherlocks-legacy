package ui.screens;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import common.dto.DialogueEventDTO;
import common.dto.DialogueType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import org.junit.BeforeClass;
import org.junit.Test;
import ui.MainController;
import ui.i18n.L10n;

/**
 * Regression for .scratch/gui-tutorial-exit-cleanup: exiting a tutorial — and leaving a game
 * session to the menu — must tear down ALL transient in-game surfaces (the statement/dialogue
 * popup, open sub-windows, the tutorial overlay), so nothing stale carries into the next tutorial
 * or session.
 */
public class TutorialExitCleanupTest {

  private static MainController controller;

  @BeforeClass
  public static void initJfx() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    try {
      Platform.startup(latch::countDown);
    } catch (IllegalStateException already) {
      latch.countDown();
    }
    assertTrue("JavaFX did not start", latch.await(5, TimeUnit.SECONDS));

    onFx(
        () -> {
          FXMLLoader loader =
              new FXMLLoader(TutorialExitCleanupTest.class.getResource("/fxml/main.fxml"));
          BorderPane root = loader.load();
          controller = loader.getController();
          Scene scene = new Scene(root);
          scene.getStylesheets().add(ui.util.Theme.baseStylesheet());
        });
    flush();
  }

  @org.junit.AfterClass
  public static void resetLanguage() throws Exception {
    onFx(() -> L10n.setLanguage(L10n.ENGLISH));
    flush();
  }

  @Test
  public void exitingATutorialTearsDownPopupAndOverlay() throws Exception {
    onFx(() -> controller.startTutorial("examine_tutorial"));
    flush();

    // Open a statement popup (dialogue bubble) and confirm the tutorial overlay is up.
    onFx(() -> showBubble());
    flush();
    onFx(
        () -> {
          assertTrue("precondition: a statement popup is open", bubbleOpen());
          assertTrue("precondition: the tutorial overlay is up", tutorialOverlayMounted());
        });

    // Exit the tutorial (the escape-hatch verb routed through the step machine).
    onFx(() -> invoke(tutorialManager(), "processInput", "exit"));
    flush();

    onFx(
        () -> {
          assertFalse("the statement popup must be gone after exiting the tutorial", bubbleOpen());
          assertFalse(
              "the tutorial overlay must be gone after exiting the tutorial",
              tutorialOverlayMounted());
        });
  }

  @Test
  public void returningToMenuTearsDownTheStatementPopup() throws Exception {
    onFx(() -> controller.startTutorial("examine_tutorial"));
    flush();
    onFx(() -> showBubble());
    flush();
    onFx(() -> assertTrue("precondition: a statement popup is open", bubbleOpen()));

    onFx(() -> controller.returnToMainMenu());
    flush();

    onFx(
        () ->
            assertFalse(
                "no statement popup may survive returning to the menu / starting a new session",
                bubbleOpen()));
  }

  @Test
  public void exitingATutorialClosesTheExamVictoryPopup() throws Exception {
    onFx(() -> controller.startTutorial("examine_tutorial"));
    flush();

    onFx(() -> showExamVictory());
    flush();
    onFx(() -> assertTrue("precondition: the Case-solved window is open", examVictoryOpen()));

    onFx(() -> invoke(tutorialManager(), "processInput", "exit"));
    flush();

    onFx(
        () ->
            assertFalse(
                "the Case-solved victory window must not survive exiting the tutorial",
                examVictoryOpen()));
  }

  // ---- helpers ----

  private static void showExamVictory() {
    Object exam = field(controller, "examScreenController");
    try {
      java.lang.reflect.Method m =
          exam.getClass().getDeclaredMethod("showVictoryPopup", String.class, String.class);
      m.setAccessible(true);
      m.invoke(exam, "Inspector", "The case is closed.");
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("invoke showVictoryPopup", e);
    }
  }

  private static boolean examVictoryOpen() {
    Object exam = field(controller, "examScreenController");
    Pane container = (Pane) field(exam, "container");
    return container.getChildren().stream().anyMatch(n -> "victoryBubble".equals(n.getId()));
  }

  private static void showBubble() {
    Object gsc = field(controller, "gameScreenController");
    invoke(
        gsc,
        "showDialogueBubble",
        new DialogueEventDTO("Clue", "A torn letter.", DialogueType.EXAMINE));
  }

  private static boolean bubbleOpen() {
    Object gsc = field(controller, "gameScreenController");
    Node view = (Node) invoke(gsc, "getView");
    return ((Pane) view).getChildren().stream().anyMatch(n -> "dialogueBubble".equals(n.getId()));
  }

  private static boolean tutorialOverlayMounted() {
    Node pane = (Node) field(controller, "tutorialOverlayPane");
    if (pane == null) {
      return false;
    }
    Pane room = controller.getRoomPane();
    return room.getChildren().contains(pane);
  }

  private static Object tutorialManager() {
    return field(controller, "tutorialManager");
  }

  // ---- reflection ----

  private static Object field(Object target, String name) {
    try {
      for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
        try {
          Field f = c.getDeclaredField(name);
          f.setAccessible(true);
          return f.get(target);
        } catch (NoSuchFieldException ignored) {
          // walk up
        }
      }
      throw new NoSuchFieldException(name);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("reflect field " + name, e);
    }
  }

  private static Object invoke(Object target, String method, Object... args) {
    try {
      for (Method m : target.getClass().getMethods()) {
        if (m.getName().equals(method) && m.getParameterCount() == args.length) {
          m.setAccessible(true);
          return m.invoke(target, args);
        }
      }
      throw new NoSuchMethodException(method);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("invoke " + method, e);
    }
  }

  // ---- FX plumbing ----

  private interface FxTask {
    void run() throws Exception;
  }

  private static void onFx(FxTask task) throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    Throwable[] error = new Throwable[1];
    Platform.runLater(
        () -> {
          try {
            task.run();
          } catch (Throwable t) {
            error[0] = t;
          } finally {
            done.countDown();
          }
        });
    assertTrue("FX task timed out", done.await(10, TimeUnit.SECONDS));
    if (error[0] != null) {
      if (error[0] instanceof AssertionError) {
        throw (AssertionError) error[0];
      }
      fail(error[0].toString());
    }
  }

  private static void flush() throws Exception {
    onFx(() -> {});
  }
}

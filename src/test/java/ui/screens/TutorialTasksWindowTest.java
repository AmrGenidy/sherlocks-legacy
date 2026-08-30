package ui.screens;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import org.junit.BeforeClass;
import org.junit.Test;
import ui.MainController;
import ui.i18n.L10n;

/**
 * Regression for .scratch/gui-tasks-tutorial: the Tasks window lists the practice case's tasks
 * during a tutorial. {@code GameScreenController.openTasksWindow} populates the window from {@code
 * shell.isSinglePlayerMode() && shell.getSinglePlayerGame() != null} → {@code
 * getSinglePlayerGame().getCurrentCaseTasks()}. Same shared root as the Journal/Pinboard sync bug:
 * a tutorial must (a) report single-player mode and (b) register its game as the active SP game, so
 * all three reads openTasksWindow performs resolve to the live practice case.
 */
public class TutorialTasksWindowTest {

  private static MainController controller;

  @BeforeClass
  public static void initJfx() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    try {
      Platform.startup(latch::countDown);
    } catch (IllegalStateException alreadyStarted) {
      latch.countDown();
    }
    assertTrue("JavaFX did not start", latch.await(5, TimeUnit.SECONDS));

    onFx(
        () -> {
          FXMLLoader loader =
              new FXMLLoader(TutorialTasksWindowTest.class.getResource("/fxml/main.fxml"));
          BorderPane root = loader.load();
          controller = loader.getController();
          new Scene(root);
        });
    flush();
  }

  @org.junit.AfterClass
  public static void resetLanguage() throws Exception {
    onFx(() -> L10n.setLanguage(L10n.ENGLISH));
    flush();
  }

  @Test
  public void tasksWindowGetsThePracticeCaseTasksDuringATutorial() throws Exception {
    onFx(() -> controller.startTutorial("tasks_tutorial"));
    flush(); // startTutorial -> tutorialManager.startTutorial -> host.showGameView() posts runLater

    onFx(
        () -> {
          // The exact three reads GameScreenController.openTasksWindow performs before loadTasks:
          assertTrue(
              "a running tutorial must report single-player mode", controller.isSinglePlayerMode());
          assertNotNull(
              "a running tutorial must register its game as the active single-player game",
              controller.getSinglePlayerGame());
          List<String> tasks = controller.getSinglePlayerGame().getCurrentCaseTasks();
          assertNotNull("the practice case must expose its tasks during a tutorial", tasks);
          assertFalse(
              "the Tasks window must receive the practice case's tasks (it was empty)",
              tasks.isEmpty());
          assertTrue(
              "the practice case's authored tasks must be present",
              tasks.contains("Examine the clues in the Study."));
        });
  }

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

package ui.screens;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import org.junit.BeforeClass;
import org.junit.Test;
import ui.MainController;
import ui.i18n.L10n;
import ui.windows.JournalWindow;

/**
 * Regression for .scratch/gui-journal-tutorial-addnote: the journal tutorial's "add note" step must
 * advance whether the player types {@code journal add …} in the terminal OR uses the Journal
 * window's "Add note" button. Drives the real {@link JournalWindow#addNote} (via the note field +
 * the private method) and asserts the tutorial step advances exactly as the terminal path does.
 */
public class JournalTutorialAddNoteTest {

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
              new FXMLLoader(JournalTutorialAddNoteTest.class.getResource("/fxml/main.fxml"));
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
  public void addingTheNoteFromTheJournalWindowAdvancesTheTutorial() throws Exception {
    onFx(() -> controller.startTutorial("journal_tutorial"));
    flush();

    // Advance the first step (open the journal) so we sit on the "journal add …" step.
    onFx(() -> controller.sendCommand("journal"));
    flush();
    int[] indexAtAddStep = new int[1];
    onFx(() -> indexAtAddStep[0] = tutorialStepIndex());

    // Use the Journal WINDOW's Add note (not the terminal): type the note and fire addNote().
    onFx(
        () -> {
          JournalWindow journal = new JournalWindow(controller);
          ((TextArea) field(journal, "noteTextArea")).setText("The valet seemed nervous");
          invoke(journal, "addNote");
        });
    flush();

    onFx(
        () ->
            assertTrue(
                "adding the note via the Journal window must advance the tutorial just like the"
                    + " terminal command",
                tutorialStepIndex() > indexAtAddStep[0]));
  }

  // ---- reflection helpers ----

  private static int tutorialStepIndex() {
    Object mgr = field(controller, "tutorialManager");
    return (int) field(mgr, "currentStepIndex");
  }

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

  private static void invoke(Object target, String method) {
    try {
      Method m = target.getClass().getDeclaredMethod(method);
      m.setAccessible(true);
      m.invoke(target);
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

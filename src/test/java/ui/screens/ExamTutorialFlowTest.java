package ui.screens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import org.junit.BeforeClass;
import org.junit.Test;
import ui.MainController;
import ui.i18n.L10n;

/**
 * Regression for .scratch/gui-exam-tutorial: the Final Exam tutorial shows the question in the GUI
 * and teaches BOTH answer paths — one question answered in the terminal, one in the exam window —
 * and the tutorial advances on each. Drives the real FXML-loaded controller end-to-end.
 */
public class ExamTutorialFlowTest {

  private static final String Q1 = "The culprit was lured to the ______ and is, in fact, the ____.";
  private static final String Q2 =
      "The single piece of evidence that shattered his alibi was the ______.";

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
              new FXMLLoader(ExamTutorialFlowTest.class.getResource("/fxml/main.fxml"));
          BorderPane root = loader.load();
          controller = loader.getController();
          new Scene(root);
        });
    settle();
  }

  @org.junit.AfterClass
  public static void resetLanguage() throws Exception {
    onFx(() -> L10n.setLanguage(L10n.ENGLISH));
    settle();
  }

  @Test
  public void examTutorialShowsQuestionsInGuiAndTeachesBothAnswerPaths() throws Exception {
    onFx(() -> controller.startTutorial("final_exam_tutorial"));
    settle();

    // Start the exam (routes through the tutorial to the engine; the GUI must populate).
    onFx(() -> controller.sendCommand("final exam"));
    settle();
    onFx(
        () ->
            assertEquals(
                "the exam GUI must show the first question (was empty)", Q1, questionPrompt()));

    // Answer Q1 in the TERMINAL; the engine advances to Q2 and the GUI live-updates.
    onFx(() -> controller.sendCommand("1,1"));
    settle();
    onFx(
        () ->
            assertEquals(
                "answering in the terminal must advance the exam GUI to question 2",
                Q2,
                questionPrompt()));

    int stepBeforeGuiAnswer = tutorialStepIndex();

    // Answer Q2 in the GUI: pick the correct choice and press the real Submit button.
    onFx(
        () -> {
          ComboBox<?> slot1 = slot1Combo();
          selectChoiceById(slot1, "q2_opt1");
          submitButton().fire();
        });
    settle();

    onFx(
        () -> {
          common.dto.ExamResultDTO result =
              controller.getSinglePlayerGame().getGameContext().getLastResultDTO();
          assertNotNull("answering Q2 in the GUI must complete the exam", result);
          assertTrue("the all-correct practice exam must be solved", result.isCaseSolved());
          assertTrue(
              "answering in the GUI must advance the tutorial past the GUI step",
              tutorialStepIndex() > stepBeforeGuiAnswer);
        });
  }

  // ---- reflection helpers into the live controller graph ----

  private static Object field(Object target, String name) {
    try {
      Field f = findField(target.getClass(), name);
      f.setAccessible(true);
      return f.get(target);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("reflect " + name, e);
    }
  }

  private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
    for (Class<?> c = type; c != null; c = c.getSuperclass()) {
      try {
        return c.getDeclaredField(name);
      } catch (NoSuchFieldException ignored) {
        // walk up
      }
    }
    throw new NoSuchFieldException(name);
  }

  private static Object examViewController() {
    Object esc = field(controller, "examScreenController");
    return field(esc, "finalExamViewController");
  }

  private static String questionPrompt() {
    return ((Label) field(examViewController(), "questionPromptLabel")).getText();
  }

  @SuppressWarnings("unchecked")
  private static ComboBox<Object> slot1Combo() {
    return (ComboBox<Object>) field(examViewController(), "slot1ComboBox");
  }

  private static Button submitButton() {
    return (Button) field(examViewController(), "submitButton");
  }

  private static void selectChoiceById(ComboBox<?> combo, String choiceId) {
    for (Object item : combo.getItems()) {
      if (choiceId.equals(((common.dto.FinalExamChoiceDTO) item).getChoiceId())) {
        ((ComboBox<Object>) combo).getSelectionModel().select(item);
        return;
      }
    }
    fail("choice id not found in combo: " + choiceId);
  }

  private static int tutorialStepIndex() {
    Object mgr = field(controller, "tutorialManager");
    return (int) field(mgr, "currentStepIndex");
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

  /**
   * Drains several FX cycles so nested runLater chains (sink → handler → displayQuestion) settle.
   */
  private static void settle() throws Exception {
    for (int i = 0; i < 6; i++) {
      onFx(() -> {});
    }
  }
}

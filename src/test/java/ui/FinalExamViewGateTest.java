package ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import common.dto.ExamQuestionDTO;
import common.dto.ExamResultDTO;
import common.dto.FinalExamChoiceDTO;
import common.dto.FinalExamSlotDTO;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Regression for .scratch/gui-exam-tutorial-input-enforce: on a terminal-taught exam question the
 * answer dropdowns are DISABLED and pressing Submit does not answer — it runs the "use the
 * terminal" nudge instead; on a GUI-taught question the dropdowns are enabled. Drives the real
 * {@link FinalExamViewController} loaded from FXML.
 */
public class FinalExamViewGateTest {

  private static FinalExamViewController vc;

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
              new FXMLLoader(FinalExamViewGateTest.class.getResource("/fxml/FinalExamView.fxml"));
          Parent root = loader.load();
          vc = loader.getController();
          new Scene(root);
          vc.setFinalExamController(null, true); // host view
        });
    flush();
  }

  private static ExamQuestionDTO question() {
    FinalExamSlotDTO slot1 =
        new FinalExamSlotDTO("slot1", List.of(new FinalExamChoiceDTO("c1", "Choice 1")));
    Map<String, FinalExamSlotDTO> slots = new HashMap<>();
    slots.put("slot1", slot1);
    return new ExamQuestionDTO(0, 2, "Who did it?", slots, new HashMap<>());
  }

  @Test
  public void terminalTaughtDisablesDropdownsAndBlocksSubmit() throws Exception {
    boolean[] nudged = {false};

    // Terminal-taught: GUI must not answer.
    onFx(
        () -> {
          vc.setGuiAnswerGate(() -> false, () -> nudged[0] = true);
          vc.displayQuestion(question());
        });
    flush(); // let displayQuestion's deferred body (which applies the gate) run

    onFx(
        () ->
            assertTrue(
                "a terminal-taught question must disable the answer dropdown",
                ((ComboBox<?>) field("slot1ComboBox")).isDisabled()));

    onFx(() -> ((Button) field("submitButton")).fire());
    onFx(
        () ->
            assertTrue(
                "pressing Submit on a terminal-taught question must nudge to the terminal, not answer",
                nudged[0]));
  }

  @Test
  public void guiTaughtEnablesDropdowns() throws Exception {
    onFx(
        () -> {
          vc.setGuiAnswerGate(() -> true, () -> {});
          vc.displayQuestion(question());
        });
    flush();

    onFx(
        () ->
            assertFalse(
                "a GUI-taught question must enable the answer dropdown",
                ((ComboBox<?>) field("slot1ComboBox")).isDisabled()));
  }

  private static ExamResultDTO result() {
    return new ExamResultDTO(1, 2, "Done", "Inspector", List.of("Q1: correct"), null, false);
  }

  /**
   * The hidden pane is always unmanaged so it reserves no width (no squeeze), and re-entering the
   * exam after a submit restores a clean full-width question layout. Guards the layout fix.
   */
  @Test
  public void hiddenPanesAreUnmanagedAndReEntryRestoresQuestionLayout() throws Exception {
    onFx(
        () -> {
          vc.setGuiAnswerGate(() -> true, () -> {});
          vc.displayQuestion(question());
        });
    flush();
    onFx(
        () -> {
          javafx.scene.Node qp = (javafx.scene.Node) field("questionPane");
          javafx.scene.Node rp = (javafx.scene.Node) field("resultScrollPane");
          assertTrue("question pane occupies layout while a question shows", isLive(qp));
          assertFalse("results pane reserves no space while a question shows", isLive(rp));
        });

    // Submit → results: the question pane (and submit) must free their layout space.
    onFx(() -> vc.displayResults(result()));
    flush();
    onFx(
        () -> {
          assertFalse(
              "question pane unmanaged while results show",
              isLive((javafx.scene.Node) field("questionPane")));
          assertFalse(
              "submit unmanaged while results show",
              isLive((javafx.scene.Node) field("submitButton")));
          assertTrue(
              "results pane occupies layout while showing",
              isLive((javafx.scene.Node) field("resultScrollPane")));
        });

    // Re-enter the exam (next question): a clean, full-width question layout again.
    onFx(() -> vc.displayQuestion(question()));
    flush();
    onFx(
        () -> {
          assertTrue(
              "question pane restored on re-entry",
              isLive((javafx.scene.Node) field("questionPane")));
          assertTrue(
              "submit restored on re-entry", isLive((javafx.scene.Node) field("submitButton")));
          assertFalse(
              "results pane hidden+unmanaged on re-entry",
              isLive((javafx.scene.Node) field("resultScrollPane")));
        });
  }

  // ---- helpers ----

  /** A node "occupies layout" iff it is both visible and managed. */
  private static boolean isLive(javafx.scene.Node node) {
    return node.isVisible() && node.isManaged();
  }

  private static Object field(String name) {
    try {
      Field f = FinalExamViewController.class.getDeclaredField(name);
      f.setAccessible(true);
      return f.get(vc);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("reflect field " + name, e);
    }
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

package ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import ui.MainController.ExamInputMode;

/**
 * Regression for .scratch/gui-exam-tutorial-input-enforce: each exam-tutorial step teaches exactly
 * one input method, derived from its {@code expectedCommand}. A terminal-answer step (choice
 * numbers like {@code 1,1}) is TERMINAL_ONLY; the GUI-answer sentinel is GUI_ONLY; everything else
 * is ANY (no gating). Pure classifier — no FX.
 */
public class ExamTutorialInputModeTest {

  @Test
  public void classifiesTheTaughtMethodPerStep() {
    assertEquals(ExamInputMode.TERMINAL_ONLY, MainController.classifyExamInputMode("1,1"));
    assertEquals(ExamInputMode.TERMINAL_ONLY, MainController.classifyExamInputMode("2"));
    assertEquals(ExamInputMode.TERMINAL_ONLY, MainController.classifyExamInputMode("1, 2"));

    assertEquals(
        ExamInputMode.GUI_ONLY,
        MainController.classifyExamInputMode(MainController.TUTORIAL_EXAM_GUI_ANSWER));

    // Non-answer steps and no-tutorial impose no gating.
    assertEquals(ExamInputMode.ANY, MainController.classifyExamInputMode("continue"));
    assertEquals(ExamInputMode.ANY, MainController.classifyExamInputMode("final exam"));
    assertEquals(ExamInputMode.ANY, MainController.classifyExamInputMode(null));
  }
}

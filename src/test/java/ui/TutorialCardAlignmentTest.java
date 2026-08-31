package ui;

import static org.junit.Assert.assertEquals;

import javafx.geometry.Pos;
import org.junit.Test;

/**
 * Regression for .scratch/gui-tutorial-bubble-position (exam screen): the guidance card must never
 * cover the content it references or the control the player needs. On the Final Exam screen it
 * always sits at the TOP — over the "Final exam" title, clear of the question/answer area (Q steps)
 * and clear of the centred "Case solved" victory card + its close button (the final step). Room
 * placement is unchanged. Pure resolver — no FX toolkit needed.
 */
public class TutorialCardAlignmentTest {

  @Test
  public void examScreenAlwaysPlacesTheCardAtTheTop() {
    // Q1 step (TERMINAL target) — must clear the question, not sit bottom-centre over it.
    assertEquals(Pos.TOP_CENTER, MainController.tutorialCardAlignment("TERMINAL", false, true));
    // Final "continue" step (NONE target) — must clear the victory card's close button.
    assertEquals(Pos.TOP_CENTER, MainController.tutorialCardAlignment("NONE", false, true));
    // Exam placement wins even if a result popup flag is somehow set.
    assertEquals(Pos.TOP_CENTER, MainController.tutorialCardAlignment("CENTER", true, true));
  }

  @Test
  public void roomPlacementIsUnchanged() {
    assertEquals(Pos.TOP_CENTER, MainController.tutorialCardAlignment("CENTER", false, false));
    assertEquals(
        Pos.CENTER_LEFT, MainController.tutorialCardAlignment("RIGHT_PANEL", false, false));
    assertEquals(Pos.BOTTOM_CENTER, MainController.tutorialCardAlignment("TERMINAL", false, false));
    assertEquals(Pos.BOTTOM_CENTER, MainController.tutorialCardAlignment("TOP_BAR", false, false));
    assertEquals(Pos.BOTTOM_CENTER, MainController.tutorialCardAlignment("NONE", false, false));
    // A result popup occupies the top band in-room, so the card dodges to the bottom.
    assertEquals(Pos.BOTTOM_CENTER, MainController.tutorialCardAlignment("CENTER", true, false));
  }
}

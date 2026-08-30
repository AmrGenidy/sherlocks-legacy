package ui.pinboard;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Pins the new-note cascade offset (.scratch/ingame-fixes-3 issue 01). */
public class NoteCascadeTest {

  @Test
  public void firstNoteSitsAtTheBase() {
    assertEquals(0.0, NoteCascade.offsetFor(0, 24, 8), 0.0);
  }

  @Test
  public void eachStepAddsTheOffset() {
    assertEquals(24.0, NoteCascade.offsetFor(1, 24, 8), 0.0);
    assertEquals(48.0, NoteCascade.offsetFor(2, 24, 8), 0.0);
    assertEquals(168.0, NoteCascade.offsetFor(7, 24, 8), 0.0);
  }

  @Test
  public void wrapsBackToTheBaseAfterWrapSteps() {
    assertEquals(0.0, NoteCascade.offsetFor(8, 24, 8), 0.0); // wrapped
    assertEquals(24.0, NoteCascade.offsetFor(9, 24, 8), 0.0);
  }

  @Test
  public void negativeStepsAreSafe() {
    assertEquals(0.0, NoteCascade.offsetFor(-8, 24, 8), 0.0);
    assertEquals(168.0, NoteCascade.offsetFor(-1, 24, 8), 0.0); // -1 -> 7
  }

  @Test
  public void nonPositiveWrapYieldsNoOffset() {
    assertEquals(0.0, NoteCascade.offsetFor(5, 24, 0), 0.0);
  }
}

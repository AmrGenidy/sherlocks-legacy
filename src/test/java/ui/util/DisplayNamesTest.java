package ui.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Pins the id-humanizer used as the Case File tab-label fallback (.scratch/casefile-tabs issue 02). */
public class DisplayNamesTest {

  @Test
  public void splitsPascalCase() {
    assertEquals("Dr Aris Thorne", DisplayNames.humanizeId("DrArisThorne"));
    assertEquals("Colonel Hastings", DisplayNames.humanizeId("ColonelHastings"));
    assertEquals("Lady Eleanor", DisplayNames.humanizeId("LadyEleanor"));
    assertEquals("Julian Vance", DisplayNames.humanizeId("JulianVance"));
  }

  @Test
  public void titleCasesSnakeAndKebab() {
    assertEquals("Lord Ashworth", DisplayNames.humanizeId("lord_ashworth"));
    assertEquals("Mademoiselle Dupont", DisplayNames.humanizeId("mademoiselle-dupont"));
  }

  @Test
  public void keepsAcronymRunsTogether() {
    assertEquals("FBI Agent", DisplayNames.humanizeId("FBIAgent"));
  }

  @Test
  public void leavesAlreadySpacedNamesUntouched() {
    assertEquals("Lady Eleanor", DisplayNames.humanizeId("Lady Eleanor"));
    assertEquals("Dr. Aris Thorne", DisplayNames.humanizeId("Dr. Aris Thorne"));
  }

  @Test
  public void blankAndNullBecomeEmpty() {
    assertEquals("", DisplayNames.humanizeId(null));
    assertEquals("", DisplayNames.humanizeId("   "));
  }
}

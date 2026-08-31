package ui.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure mapping from a slider multiplier to a prefixed {@code <prefix>NNN} bucket class. No FX. */
public class ContentScaleTest {

  private static final String R = ContentScale.READING_PREFIX;
  private static final String T = ContentScale.TERMINAL_PREFIX;

  @Test
  public void mapsMultipliersToTwentyPercentBucketsUnderEachPrefix() {
    assertEquals("read-scale-100", ContentScale.styleClass(R, 1.0));
    assertEquals("read-scale-140", ContentScale.styleClass(R, 1.4));
    assertEquals("term-scale-80", ContentScale.styleClass(T, 0.8));
    assertEquals("term-scale-120", ContentScale.styleClass(T, 1.2));
  }

  @Test
  public void snapsToTheNearestStop() {
    assertEquals("read-scale-120", ContentScale.styleClass(R, 1.13));
    assertEquals("term-scale-120", ContentScale.styleClass(T, 1.16));
    assertEquals(1.2, ContentScale.snap(1.14), 1e-9);
  }

  @Test
  public void clampsOutOfRangeMultipliers() {
    assertEquals("read-scale-80", ContentScale.styleClass(R, 0.2));
    assertEquals("term-scale-160", ContentScale.styleClass(T, 5.0));
    assertEquals("read-scale-100", ContentScale.styleClass(R, Double.NaN));
  }

  @Test
  public void recognisesItsOwnClassesPerPrefix() {
    assertTrue(ContentScale.isScaleClass(R, "read-scale-130"));
    assertTrue(ContentScale.isScaleClass(T, "term-scale-130"));
    assertFalse(
        "a reading class is not a terminal class", ContentScale.isScaleClass(T, "read-scale-130"));
    assertFalse(ContentScale.isScaleClass(R, "lang-ar"));
    assertFalse(ContentScale.isScaleClass(R, null));
  }
}

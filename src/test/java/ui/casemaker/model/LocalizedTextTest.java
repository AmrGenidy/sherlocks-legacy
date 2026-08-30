package ui.casemaker.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Behaviour of {@link LocalizedText} (slice 5). */
public class LocalizedTextTest {

  @Test
  public void noArgAccessorsOperateOnThePrimaryLanguage() {
    LocalizedText text = new LocalizedText();
    text.set("Hello"); // primary (en)
    assertEquals("Hello", text.get());
    assertEquals("Hello", text.get("en"));
  }

  @Test
  public void languagesAreIndependent() {
    LocalizedText text = new LocalizedText();
    text.set("en", "Hello");
    text.set("ru", "Привет");
    assertEquals("Hello", text.get("en"));
    assertEquals("Привет", text.get("ru"));
    assertNull(text.get("ar"));
  }

  @Test
  public void blankClearsTheLanguage() {
    LocalizedText text = new LocalizedText();
    text.set("ru", "Привет");
    assertTrue(text.has("ru"));
    text.set("ru", "  ");
    assertFalse(text.has("ru"));
    assertTrue(text.isEmpty());
  }

  @Test
  public void asMapContainsOnlyPresentLanguages() {
    LocalizedText text = new LocalizedText();
    text.set("en", "Hello");
    text.set("ru", "");
    assertEquals(1, text.asMap().size());
    assertEquals("Hello", text.asMap().get("en"));
  }
}

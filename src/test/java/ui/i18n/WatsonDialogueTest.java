package ui.i18n;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import common.dto.DialogueEventDTO;
import common.dto.DialogueType;
import org.junit.After;
import org.junit.Test;

/**
 * The client-side seam that localizes the engine's generic Watson responses
 * (.scratch/gui-localized-watson-hints phase 2). A {@link DialogueEventDTO} carrying a {@code
 * textKey} is rendered in the UI language via {@link L10n}; an authored hint (no key) is left
 * untouched.
 */
public class WatsonDialogueTest {

  @After
  public void resetLanguage() {
    L10n.setLanguage(L10n.ENGLISH);
  }

  @Test
  public void keyedDialogueResolvesInTheUiLanguageAndClearsTheKey() {
    DialogueEventDTO keyed =
        new DialogueEventDTO(
            "Dr. Watson", "English fallback.", DialogueType.WATSON, "watson.generic.connected");

    L10n.setLanguage(L10n.ARABIC);
    DialogueEventDTO out = WatsonDialogue.localize(keyed);

    assertNull("the key is resolved away on the client", out.getTextKey());
    assertEquals("\"" + L10n.t("watson.generic.connected") + "\"", out.getText());
  }

  @Test
  public void uiLanguageChangesTheRenderedText() {
    DialogueEventDTO keyed =
        new DialogueEventDTO(
            "Dr. Watson", "English fallback.", DialogueType.WATSON, "watson.generic.distraction");

    L10n.setLanguage(L10n.ARABIC);
    String arabic = WatsonDialogue.localize(keyed).getText();
    L10n.setLanguage(L10n.ENGLISH);
    String english = WatsonDialogue.localize(keyed).getText();

    org.junit.Assert.assertNotEquals("the generic line localizes per UI language", arabic, english);
  }

  @Test
  public void authoredDialogueWithoutKeyIsLeftUntouched() {
    DialogueEventDTO authored =
        new DialogueEventDTO(
            "Dr. Watson", "\"An authored, already-localized hint.\"", DialogueType.WATSON);
    assertSame(
        "a keyless dialogue passes through unchanged", authored, WatsonDialogue.localize(authored));
  }
}

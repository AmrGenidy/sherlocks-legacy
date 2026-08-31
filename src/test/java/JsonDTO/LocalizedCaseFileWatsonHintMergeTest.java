package JsonDTO;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.Test;

/**
 * Locks the canonical localized-hint loading path (.scratch/gui-localized-watson-hints): the
 * structured {@code watson.hints} block is the single home for Watson hint text. {@link
 * LocalizedCaseFile} resolves each hint's per-language {@code text} map to the chosen case Language
 * Code, falling back to English when a translation is absent — exactly how a Statement or Clue is
 * localized.
 */
public class LocalizedCaseFileWatsonHintMergeTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  // Two general hints: g1 has both en+ar, g2 has only en (a missing translation).
  private static final String CASE_JSON =
      "{"
          + "\"universal_title\":\"T\","
          + "\"startingRoom\":\"Study\","
          + "\"rooms\":[{\"name\":\"Study\",\"neighbors\":{}}],"
          + "\"watson\":{\"hints\":{\"general\":["
          + "{\"id\":\"g1\",\"text\":{\"en\":\"Look at the glass.\",\"ar\":\"بص على الإزاز.\"}},"
          + "{\"id\":\"g2\",\"text\":{\"en\":\"Mind the alibi.\"}}]}},"
          + "\"localizations\":{"
          + "\"en\":{\"languageName\":\"English\",\"title\":\"T\",\"invitation\":\"i\","
          + "\"roomDetails\":[{\"name\":\"Study\",\"description\":\"d\"}],\"objectDetails\":[]},"
          + "\"ar\":{\"languageName\":\"العربية\",\"title\":\"T\",\"invitation\":\"i\","
          + "\"roomDetails\":[{\"name\":\"Study\",\"description\":\"d\"}],\"objectDetails\":[]}}}";

  private LocalizedCaseFile localized(String lang) throws Exception {
    return new LocalizedCaseFile(MAPPER.readValue(CASE_JSON, CaseFile.class), lang);
  }

  @Test
  public void hintResolvesInTheChosenCaseLanguage() throws Exception {
    List<LocalizedCaseFile.LocalizedWatsonHint> general =
        localized("ar").getStructuredWatsonHints().get("general");
    assertEquals("g1", general.get(0).getId());
    assertEquals("بص على الإزاز.", general.get(0).getText());
  }

  @Test
  public void hintFallsBackToEnglishWhenTranslationAbsent() throws Exception {
    List<LocalizedCaseFile.LocalizedWatsonHint> general =
        localized("ar").getStructuredWatsonHints().get("general");
    assertEquals("g2", general.get(1).getId());
    assertEquals("Mind the alibi.", general.get(1).getText());
  }
}

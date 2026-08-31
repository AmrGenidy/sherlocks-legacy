package server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import JsonDTO.CaseFile;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import common.SerializationUtils;
import common.dto.AvailableCasesDTO;
import extractors.CaseLoader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;

/**
 * The lobby case list must never ship a case's answer key (SECURITY_PLAN D). These tests pin that
 * the browse payload built through {@link LobbyCaseSanitizer} — exactly what {@code
 * GameSessionManager} sends for {@code RequestCaseListCommand} — carries no final-exam {@code
 * correct_combination} (nor the suspect/combine reward content), on the object graph AND on the
 * serialized wire, while keeping what the case browser actually reads. The authoritative case, with
 * answers, stays server-side for scoring.
 */
public class LobbyCaseSanitizerTest {

  private static final ObjectMapper PLAIN =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private static CaseFile parse(String json) {
    try {
      return PLAIN.readValue(json, CaseFile.class);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void strippedCaseHasNoAnswerKeyButKeepsBrowseFields() {
    CaseFile source = parse(CASE_WITH_ANSWER_KEY);
    // Precondition: the source really carries the answer key we expect to strip.
    assertNotNull(
        source
            .getLocalizations()
            .get("en")
            .getFinalExam()
            .getQuestions()
            .get(0)
            .getCorrectCombination());

    CaseFile safe = LobbyCaseSanitizer.stripped(source);

    // Answer-key / reward content removed.
    assertNull(
        "final_exam (answer key) must be stripped",
        safe.getLocalizations().get("en").getFinalExam());
    assertNull(
        "suspect truth-states/reward targets must be stripped",
        safe.getLocalizations().get("en").getSuspects());
    assertTrue(
        "combine reward targets must be stripped",
        safe.getCombineLogic() == null || safe.getCombineLogic().isEmpty());
    assertNull("server file path must not leak", safe.getSourcePath());

    // Browser-needed fields survive.
    assertNotNull(safe.getUniversalTitle());
    assertNotNull(safe.getLocalizations());
    CaseFile.LocalizedData en = safe.getLocalizations().get("en");
    assertNotNull("language name is shown in the picker", en.getLanguageName());
    assertNotNull("invitation is shown in the dossier", en.getInvitation());
  }

  @Test
  public void sanitizingDoesNotMutateTheAuthoritativeSource() {
    CaseFile source = parse(CASE_WITH_ANSWER_KEY);
    LobbyCaseSanitizer.stripped(source);
    // The server's own copy still has the full exam + answer key for scoring.
    assertNotNull(source.getLocalizations().get("en").getFinalExam());
    assertNotNull(
        source
            .getLocalizations()
            .get("en")
            .getFinalExam()
            .getQuestions()
            .get(0)
            .getCorrectCombination());
  }

  @Test
  public void browsePayloadWireFormCarriesNoCorrectCombination() throws Exception {
    // The exact object the server sends for RequestCaseListCommand, serialized with the real wire
    // mapper. Scan the bytes for the answer-key field name in either JSON or Java form.
    List<CaseFile> browse = LobbyCaseSanitizer.forBrowsing(List.of(parse(CASE_WITH_ANSWER_KEY)));
    AvailableCasesDTO dto = new AvailableCasesDTO(browse);

    String wire = new String(SerializationUtils.serialize(dto), StandardCharsets.UTF_8);
    assertFalse(
        "wire must not contain the JSON answer-key field", wire.contains("correct_combination"));
    assertFalse("wire must not contain the answer-key field", wire.contains("correctCombination"));
  }

  @Test
  public void allBundledCasesAreStrippedOfAnswerKeys() {
    List<CaseFile> bundled = CaseLoader.loadCases("cases");
    assertFalse("precondition: bundled cases load", bundled.isEmpty());

    List<CaseFile> browse = LobbyCaseSanitizer.forBrowsing(bundled);
    assertTrue("no case should be dropped by sanitizing", browse.size() == bundled.size());
    for (CaseFile c : browse) {
      assertNotNull("browse case keeps its title", c.getUniversalTitle());
      if (c.getLocalizations() != null) {
        for (CaseFile.LocalizedData loc : c.getLocalizations().values()) {
          assertNull("no final exam (answer key) in a browse case", loc.getFinalExam());
        }
      }
    }
  }

  private static final String CASE_WITH_ANSWER_KEY =
      """
      {
        "universal_title": "Answer Key Case",
        "startingRoom": "Hall",
        "metadata": {"author": "Test Author"},
        "rooms": [{"name":"Hall","neighbors":{},"objects":[{"name":"key"}]}],
        "combine_logic":[
          {"requires":["key"],"resultDeductionId":"ded_combo","resultText":{"en":"x"},"tokenReward":1,"repeatable":false}
        ],
        "localizations":{
          "en":{
            "languageName":"English","title":"Answer Key Case","invitation":"Come quick",
            "suspects":[
              {"name":"Alice","homeRoom":"Hall","initialState":"LIE","states":{
                 "LIE":{"statement":"I was home","contradictions":[{"evidenceId":"key","nextState":"TRUTH","rewardDeductionId":"ded_alice"}]},
                 "TRUTH":{"statement":"Fine, I was there"}
              }}
            ],
            "roomDetails":[{"name":"Hall","description":"a hall"}],
            "objectDetails":[{"name":"key","examine":"shiny"}],
            "final_exam":{"questions":[{"question_prompt":"who?","slots":{"slot1":{"slot_id":"slot1","choices":[{"choice_id":"c1","choice_text":"Alice"}]}},"correct_combination":{"slot1":"c1"}}]},
            "rankingTiers":[{"rankName":"Top","maxDeductions":1},{"rankName":"Default","defaultRank":true}]
          }
        }
      }
      """;
}

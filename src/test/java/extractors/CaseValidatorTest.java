package extractors;

import static org.junit.Assert.*;

import JsonDTO.CaseFile;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import extractors.CaseValidator.Issue;
import extractors.CaseValidator.Severity;
import java.util.List;
import org.junit.Test;

/**
 * Behaviour specification for {@link CaseValidator}. Cases are built by parsing JSON (the real
 * Jackson path the game uses) and then mutating the public DTO fields to break one thing at a time.
 */
public class CaseValidatorTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private static CaseFile parse(String json) {
    try {
      return MAPPER.readValue(json, CaseFile.class);
    } catch (Exception e) {
      throw new RuntimeException("Test fixture JSON failed to parse", e);
    }
  }

  /** A fully well-formed case: connected graph, complete localization, all ids resolve. */
  private static CaseFile validCase() {
    return parse(VALID_JSON);
  }

  private static boolean hasError(List<Issue> issues, String needle) {
    return issues.stream()
        .anyMatch(i -> i.severity() == Severity.ERROR && i.message().contains(needle));
  }

  private static boolean hasWarning(List<Issue> issues, String needle) {
    return issues.stream()
        .anyMatch(i -> i.severity() == Severity.WARNING && i.message().contains(needle));
  }

  // ---- Tracer: a well-formed case is valid ----

  @Test
  public void wellFormedCaseHasNoErrors() {
    CaseValidator.Report report = CaseValidator.validate(validCase());
    assertTrue("A well-formed case should be valid. Got: " + report.errors(), report.isValid());
    assertFalse(report.hasErrors());
  }

  // ---- Localization completeness ----

  @Test
  public void missingFinalExamIsAnError() {
    CaseFile c = validCase();
    c.getLocalizations().get("en").finalExam = null;
    assertTrue(hasError(CaseValidator.validate(c).issues(), "Final exam missing"));
  }

  @Test
  public void emptyFinalExamQuestionsIsAnError() {
    CaseFile c = validCase();
    c.getLocalizations().get("en").getFinalExam().setQuestions(java.util.List.of());
    assertTrue(hasError(CaseValidator.validate(c).issues(), "no questions"));
  }

  @Test
  public void blankTitleIsAnError() {
    CaseFile c = validCase();
    c.getLocalizations().get("en").title = "  ";
    assertTrue(hasError(CaseValidator.validate(c).issues(), "blank title"));
  }

  @Test
  public void blankInvitationIsAnError() {
    CaseFile c = validCase();
    c.getLocalizations().get("en").invitation = null;
    assertTrue(hasError(CaseValidator.validate(c).issues(), "blank invitation"));
  }

  @Test
  public void roomNotCoveredByRoomDetailsIsAnError() {
    CaseFile c = validCase();
    c.getLocalizations().get("en").getRoomDetails().remove(0); // drop "Hall"
    assertTrue(hasError(CaseValidator.validate(c).issues(), "roomDetails entry for room 'Hall'"));
  }

  @Test
  public void objectNotCoveredByObjectDetailsIsAnError() {
    CaseFile c = validCase();
    c.getLocalizations().get("en").getObjectDetails().remove(0); // drop "key"
    assertTrue(
        hasError(CaseValidator.validate(c).issues(), "objectDetails entry for object 'key'"));
  }

  @Test
  public void badCorrectCombinationChoiceIsAnError() {
    CaseFile c = validCase();
    c.getLocalizations()
        .get("en")
        .getFinalExam()
        .getQuestions()
        .get(0)
        .setCorrectCombination(java.util.Map.of("slot1", "does_not_exist"));
    assertTrue(hasError(CaseValidator.validate(c).issues(), "no choice 'does_not_exist'"));
  }

  @Test
  public void noLocalizationsIsAnError() {
    CaseFile c =
        parse(
            """
            {
              "universal_title": "Bare",
              "startingRoom": "Hall",
              "rooms": [{"name":"Hall","neighbors":{}}],
              "localizations": {}
            }
            """);
    assertTrue(hasError(CaseValidator.validate(c).issues(), "No localizations defined"));
  }

  // ---- Room graph ----

  @Test
  public void danglingNeighbourIsAnError() {
    CaseFile c = validCase();
    c.getRooms().get(0).getNeighbors().put("north", "Nowhere");
    assertTrue(
        hasError(CaseValidator.validate(c).issues(), "Neighbour 'Nowhere' is not a defined"));
  }

  @Test
  public void startingRoomNotADefinedRoomIsAnError() {
    CaseFile c = validCase();
    c.getRooms().get(0).name = "Foyer"; // start was "Hall"
    assertTrue(
        hasError(CaseValidator.validate(c).issues(), "startingRoom 'Hall' is not a defined"));
  }

  @Test
  public void unreachableRoomIsAnError() {
    CaseFile c = validCase();
    CaseFile.RoomData attic = new CaseFile.RoomData();
    attic.name = "Attic";
    attic.neighbors = new java.util.HashMap<>();
    c.getRooms().add(attic);
    assertTrue(hasError(CaseValidator.validate(c).issues(), "Room 'Attic' is unreachable"));
  }

  @Test
  public void oneWayLinkIsAWarningNotAnError() {
    CaseFile c = validCase();
    c.getRooms().get(1).getNeighbors().clear(); // Library no longer links back to Hall
    List<Issue> issues = CaseValidator.validate(c).issues();
    assertTrue(hasWarning(issues, "One-way link"));
    assertTrue("one-way links must not block loading", CaseValidator.validate(c).isValid());
  }

  // ---- Cross references ----

  @Test
  public void danglingContradictionEvidenceIsAnError() {
    CaseFile c = validCase();
    c.getLocalizations()
            .get("en")
            .getSuspects()
            .get(0)
            .getStates()
            .get("LIE")
            .getContradictions()
            .get(0)
            .evidenceId =
        "ghost_clue";
    assertTrue(hasError(CaseValidator.validate(c).issues(), "evidenceId 'ghost_clue'"));
  }

  @Test
  public void contradictionEvidenceMayResolveToADeduction() {
    CaseFile c = validCase();
    c.getLocalizations()
            .get("en")
            .getSuspects()
            .get(0)
            .getStates()
            .get("LIE")
            .getContradictions()
            .get(0)
            .evidenceId =
        "ded_combo"; // a combine result deduction id
    assertFalse(hasError(CaseValidator.validate(c).issues(), "evidenceId"));
  }

  @Test
  public void danglingCombineRequiresIsAnError() {
    CaseFile c = validCase();
    c.getCombineLogic().get(0).requires = java.util.List.of("key", "phantom");
    assertTrue(hasError(CaseValidator.validate(c).issues(), "requires 'phantom'"));
  }

  @Test
  public void duplicateResultDeductionIdIsAnError() {
    CaseFile c = validCase();
    CaseFile.CombineRule dup = new CaseFile.CombineRule();
    dup.requires = java.util.List.of("key", "book");
    dup.resultDeductionId = "ded_combo"; // same as the existing rule
    c.getCombineLogic().add(dup);
    assertTrue(hasError(CaseValidator.validate(c).issues(), "Duplicate resultDeductionId"));
  }

  @Test
  public void danglingRedHerringRecoveryIsAnError() {
    CaseFile c = validCase();
    c.getRedHerrings().getObjects().get("key").recoverableBy = "missing_thing";
    assertTrue(hasError(CaseValidator.validate(c).issues(), "recoverable_by 'missing_thing'"));
  }

  // ---- Images (warn-only) ----

  @Test
  public void unresolvedImagePathIsAWarningNotAnError() {
    CaseFile c = validCase();
    c.getRooms().get(0).imagePath = "images/definitely_missing_zzz.png";
    List<Issue> issues = CaseValidator.validate(c).issues();
    assertTrue(hasWarning(issues, "does not resolve"));
    assertTrue("missing images must not block loading", CaseValidator.validate(c).isValid());
  }

  // ---- Display Names (warn-only): .scratch/gui-localized-case-names ----

  @Test
  public void missingDisplayNameIsAWarningNotAnError() {
    // VALID_JSON authors no displayName anywhere; every room/object/suspect should warn, but the
    // case still loads (the runtime falls back to the Universal Name).
    CaseValidator.Report report = CaseValidator.validate(validCase());
    assertTrue(hasWarning(report.issues(), "Display Name"));
    assertTrue("missing Display Names must not block loading", report.isValid());
  }

  @Test
  public void presentDisplayNamesProduceNoDisplayNameWarning() {
    CaseFile c = validCase();
    CaseFile.LocalizedData en = c.getLocalizations().get("en");
    en.getRoomDetails().forEach(r -> r.displayName = "DN-" + r.name);
    en.getObjectDetails().forEach(o -> o.displayName = "DN-" + o.name);
    en.getSuspects().forEach(s -> s.displayName = "DN-" + s.name);
    assertFalse(
        "a fully authored localization should not warn about Display Names",
        hasWarning(CaseValidator.validate(c).issues(), "Display Name"));
  }

  // ---- Suspect placement: home room required (DEC-6) ----

  @Test
  public void suspectWithoutHomeRoomIsAnError() {
    CaseFile c = validCase();
    c.getLocalizations().get("en").getSuspects().get(0).homeRoom = null;
    assertTrue(hasError(CaseValidator.validate(c).issues(), "no home room"));
  }

  @Test
  public void suspectWithUnresolvableHomeRoomIsAnError() {
    CaseFile c = validCase();
    c.getLocalizations().get("en").getSuspects().get(0).homeRoom = "Nowhere";
    assertTrue(hasError(CaseValidator.validate(c).issues(), "is not a defined room"));
  }

  // ---- Suspect placement: cross-language consistency (DEC-9) ----

  @Test
  public void identicalSuspectPlacementAcrossLanguagesIsValid() {
    CaseValidator.Report report = CaseValidator.validate(parse(TWO_LANG_JSON));
    assertFalse(
        "consistent two-language case should be valid: " + report.errors(), report.hasErrors());
  }

  @Test
  public void divergentSuspectPlacementAcrossLanguagesIsAnError() {
    CaseFile c = parse(TWO_LANG_JSON);
    c.getLocalizations().get("ru").getSuspects().get(0).homeRoom = "Library"; // en says Hall
    assertTrue(hasError(CaseValidator.validate(c).issues(), "differs between languages"));
  }

  @Test
  public void suspectMissingFromOneLanguageIsAnError() {
    CaseFile c = parse(TWO_LANG_JSON);
    c.getLocalizations().get("ru").getSuspects().clear(); // ru drops the suspect entirely
    assertTrue(hasError(CaseValidator.validate(c).issues(), "not present in every language"));
  }

  // ---- Watson hint localization coverage (warn-only): .scratch/gui-localized-watson-hints ----

  /**
   * TWO_LANG_JSON (en+ru) with a {@code watson.hints} block whose general hint matches {@code
   * text}.
   */
  private static CaseFile twoLangWithWatson(String hintTextJson) {
    String watson =
        "\"watson\":{\"hints\":{\"general\":[{\"id\":\"g1\",\"text\":" + hintTextJson + "}]}},";
    return parse(
        TWO_LANG_JSON.replace(
            "\"universal_title\": \"Two Lang Case\",",
            "\"universal_title\": \"Two Lang Case\"," + watson));
  }

  @Test
  public void watsonHintMissingATranslationIsAWarningNotAnError() {
    // g1 is authored only in English; the case also localizes "ru".
    CaseValidator.Report report = CaseValidator.validate(twoLangWithWatson("{\"en\":\"clue\"}"));
    assertTrue(hasWarning(report.issues(), "Watson hint"));
    assertTrue("a missing hint translation must not block loading", report.isValid());
  }

  @Test
  public void fullyTranslatedWatsonHintProducesNoWarning() {
    CaseFile c = twoLangWithWatson("{\"en\":\"clue\",\"ru\":\"улика\"}");
    assertFalse(
        "a hint translated for every localized language should not warn",
        hasWarning(CaseValidator.validate(c).issues(), "Watson hint"));
  }

  // ---- Resource budgets (SECURITY_PLAN A/P0-2): over-budget cases are ERRORs ----

  @Test
  public void tooManyRoomsIsAnError() {
    CaseFile c = validCase();
    for (int i = 0; i < CaseLimits.MAX_ROOMS + 1; i++) {
      CaseFile.RoomData r = new CaseFile.RoomData();
      r.name = "Extra" + i;
      r.neighbors = new java.util.HashMap<>();
      c.getRooms().add(r);
    }
    assertTrue(hasError(CaseValidator.validate(c).issues(), "Too many rooms"));
  }

  @Test
  public void tooManyObjectsInOneRoomIsAnError() {
    CaseFile c = validCase();
    java.util.List<CaseFile.GameObjectData> objects = c.getRooms().get(0).getObjects();
    for (int i = 0; i < CaseLimits.MAX_OBJECTS_PER_ROOM + 1; i++) {
      CaseFile.GameObjectData o = new CaseFile.GameObjectData();
      o.name = "obj" + i;
      objects.add(o);
    }
    assertTrue(hasError(CaseValidator.validate(c).issues(), "Too many objects"));
  }

  @Test
  public void tooManySuspectsIsAnError() {
    CaseFile c = validCase();
    java.util.List<CaseFile.SuspectData> suspects = c.getLocalizations().get("en").getSuspects();
    for (int i = 0; i < CaseLimits.MAX_SUSPECTS_PER_LANGUAGE + 1; i++) {
      CaseFile.SuspectData s = new CaseFile.SuspectData();
      s.name = "S" + i;
      s.homeRoom = "Hall";
      suspects.add(s);
    }
    assertTrue(hasError(CaseValidator.validate(c).issues(), "Too many suspects"));
  }

  @Test
  public void overLongAuthorTextIsAnError() {
    CaseFile c = validCase();
    c.getRooms().get(0).description = "x".repeat(CaseLimits.MAX_TEXT_LENGTH + 1);
    assertTrue(hasError(CaseValidator.validate(c).issues(), "exceeds the maximum length"));
  }

  @Test
  public void normalCaseIsComfortablyWithinAllBudgets() {
    CaseValidator.Report report = CaseValidator.validate(validCase());
    assertFalse(hasError(report.issues(), "Too many"));
    assertFalse(hasError(report.issues(), "exceeds the maximum length"));
  }

  private static final String VALID_JSON =
      """
      {
        "universal_title": "Test Case",
        "startingRoom": "Hall",
        "rooms": [
          {"name":"Hall","neighbors":{"east":"Library"},"objects":[{"name":"key"}]},
          {"name":"Library","neighbors":{"west":"Hall"},"objects":[{"name":"book"}]}
        ],
        "combine_logic":[
          {"requires":["key","book"],"resultDeductionId":"ded_combo","resultText":{"en":"x"},"tokenReward":1,"repeatable":false}
        ],
        "red_herrings":{"objects":{"key":{"is_red_herring":true,"recoverable_by":"book"}}},
        "localizations":{
          "en":{
            "languageName":"English","title":"Test Case","invitation":"Come quick",
            "suspects":[
              {"name":"Alice","homeRoom":"Hall","initialState":"LIE","states":{
                 "LIE":{"statement":"I was home","contradictions":[{"evidenceId":"key","nextState":"TRUTH","rewardDeductionId":"ded_alice"}]},
                 "TRUTH":{"statement":"Fine, I was there"}
              }}
            ],
            "roomDetails":[{"name":"Hall","description":"a hall"},{"name":"Library","description":"books"}],
            "objectDetails":[{"name":"key","examine":"shiny"},{"name":"book","examine":"dusty"}],
            "final_exam":{"questions":[{"question_prompt":"who?","slots":{"slot1":{"slot_id":"slot1","choices":[{"choice_id":"c1","choice_text":"Alice"}]}},"correct_combination":{"slot1":"c1"}}]},
            "rankingTiers":[{"rankName":"Top","maxDeductions":1},{"rankName":"Default","defaultRank":true}]
          }
        }
      }
      """;

  /** A two-language case with one suspect placed identically in both languages (DEC-9 baseline). */
  private static final String TWO_LANG_JSON =
      """
      {
        "universal_title": "Two Lang Case",
        "startingRoom": "Hall",
        "rooms": [
          {"name":"Hall","neighbors":{"east":"Library"},"objects":[{"name":"key"}]},
          {"name":"Library","neighbors":{"west":"Hall"},"objects":[{"name":"book"}]}
        ],
        "combine_logic":[
          {"requires":["key","book"],"resultDeductionId":"ded_combo","resultText":{"en":"x"},"tokenReward":1,"repeatable":false}
        ],
        "localizations":{
          "en":{
            "languageName":"English","title":"Two Lang Case","invitation":"Come quick",
            "suspects":[
              {"name":"Alice","homeRoom":"Hall","posX":0.5,"posY":0.6,"stationary":false,"initialState":"LIE","states":{
                 "LIE":{"statement":"I was home","contradictions":[{"evidenceId":"key","nextState":"TRUTH","rewardDeductionId":"ded_alice"}]},
                 "TRUTH":{"statement":"Fine, I was there"}
              }}
            ],
            "roomDetails":[{"name":"Hall","description":"a hall"},{"name":"Library","description":"books"}],
            "objectDetails":[{"name":"key","examine":"shiny"},{"name":"book","examine":"dusty"}],
            "final_exam":{"questions":[{"question_prompt":"who?","slots":{"slot1":{"slot_id":"slot1","choices":[{"choice_id":"c1","choice_text":"Alice"}]}},"correct_combination":{"slot1":"c1"}}]},
            "rankingTiers":[{"rankName":"Top","maxDeductions":1},{"rankName":"Default","defaultRank":true}]
          },
          "ru":{
            "languageName":"Russian","title":"Два языка","invitation":"Скорее сюда",
            "suspects":[
              {"name":"Alice","homeRoom":"Hall","posX":0.5,"posY":0.6,"stationary":false,"initialState":"LIE","states":{
                 "LIE":{"statement":"Я была дома","contradictions":[{"evidenceId":"key","nextState":"TRUTH","rewardDeductionId":"ded_alice"}]},
                 "TRUTH":{"statement":"Ладно, я была там"}
              }}
            ],
            "roomDetails":[{"name":"Hall","description":"зал"},{"name":"Library","description":"книги"}],
            "objectDetails":[{"name":"key","examine":"блестящий"},{"name":"book","examine":"пыльный"}],
            "final_exam":{"questions":[{"question_prompt":"кто?","slots":{"slot1":{"slot_id":"slot1","choices":[{"choice_id":"c1","choice_text":"Алиса"}]}},"correct_combination":{"slot1":"c1"}}]},
            "rankingTiers":[{"rankName":"Top","maxDeductions":1},{"rankName":"Default","defaultRank":true}]
          }
        }
      }
      """;
}

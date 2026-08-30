package extractors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import JsonDTO.CaseFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * Load-time resource-budget defenses for untrusted case files (SECURITY_PLAN A/P0-2). Each hostile
 * shape — an oversized file, a gigantic string, and pathological nesting — must be refused (the
 * case is never registered) without exhausting memory, while a normal case in the same directory
 * still loads. The injected padding/junk fields are UNKNOWN properties: were the corresponding
 * guard absent, the case would parse and validate cleanly and register, so its absence pins the
 * guard.
 */
public class CaseLoaderTest {

  private static boolean hasTitle(List<CaseFile> cases, String universalTitle) {
    return cases.stream().anyMatch(c -> universalTitle.equals(c.getUniversalTitle()));
  }

  private static void write(Path dir, String fileName, String content) throws Exception {
    Files.writeString(dir.resolve(fileName), content);
  }

  @Test
  public void loadsNormalCaseButRefusesOversizedOne() throws Exception {
    Path dir = Files.createTempDirectory("sl-cases");
    write(dir, "good.json", SMALL_VALID.replace("__TITLE__", "Small Good Case"));

    // A fully valid case padded past the file-size budget with sub-string-limit strings.
    String padding = "\"padding\":[" + bigStrings(3, 800_000) + "],";
    String oversized =
        SMALL_VALID.replace(
            "\"universal_title\": \"__TITLE__\",",
            "\"universal_title\": \"Oversized Case\"," + padding);
    write(dir, "big.json", oversized);
    assertTrue(
        "precondition: file really is over the 2MB budget", oversized.length() > 2 * 1024 * 1024);

    List<CaseFile> cases = CaseLoader.loadCases(dir.toString());
    assertTrue("a normal case still loads", hasTitle(cases, "Small Good Case"));
    assertFalse("the oversized case is refused", hasTitle(cases, "Oversized Case"));
  }

  @Test
  public void refusesGiganticStringAtParse() throws Exception {
    Path dir = Files.createTempDirectory("sl-cases");
    // File stays under the 2MB size budget, but one string exceeds the parser's max string length,
    // so it is rejected during parsing (not by the size gate). Bound to metadata.soundtrack, an
    // unchecked String field, so absent the parser guard the case would register.
    String huge = "x".repeat(CaseLimits.MAX_JSON_STRING_LENGTH + 1);
    String content =
        SMALL_VALID.replace(
            "\"universal_title\": \"__TITLE__\",",
            "\"universal_title\": \"Huge String Case\",\"metadata\":{\"soundtrack\":\""
                + huge
                + "\"},");
    write(dir, "huge.json", content);
    assertTrue("precondition: file is under the size budget", content.length() < 2 * 1024 * 1024);

    List<CaseFile> cases = CaseLoader.loadCases(dir.toString());
    assertFalse("gigantic-string case is refused", hasTitle(cases, "Huge String Case"));
  }

  @Test
  public void refusesDeeplyNestedJsonAtParse() throws Exception {
    Path dir = Files.createTempDirectory("sl-cases");
    int depth = CaseLimits.MAX_JSON_NESTING_DEPTH + 25;
    String nested = "\"junk\":" + "[".repeat(depth) + "]".repeat(depth) + ",";
    String content =
        SMALL_VALID.replace(
            "\"universal_title\": \"__TITLE__\",", "\"universal_title\": \"Deep Case\"," + nested);
    write(dir, "deep.json", content);

    List<CaseFile> cases = CaseLoader.loadCases(dir.toString());
    assertFalse("deeply-nested case is refused", hasTitle(cases, "Deep Case"));
  }

  @Test
  public void loadsCasesFromNestedSubdirectories() throws Exception {
    // The external scan is recursive: a self-contained case folder (cases/<slug>/<slug>.json) and
    // an even deeper case must both be found.
    Path dir = Files.createTempDirectory("sl-cases");
    Path slug = Files.createDirectories(dir.resolve("a_bitter_cup"));
    write(slug, "a_bitter_cup.json", SMALL_VALID.replace("__TITLE__", "Nested Case"));
    Path deep = Files.createDirectories(dir.resolve("x").resolve("y"));
    write(deep, "deep.json", SMALL_VALID.replace("__TITLE__", "Deeper Case"));

    List<CaseFile> cases = CaseLoader.loadCases(dir.toString());
    assertTrue("case in cases/<slug>/ is found", hasTitle(cases, "Nested Case"));
    assertTrue("case nested two levels deep is found", hasTitle(cases, "Deeper Case"));
  }

  @Test
  public void neverThrowsAndCreatesMissingExternalDirectory() throws Exception {
    // loadCases must always return a list (never throw) and create the external cases directory if
    // it is absent, so the app always has a place to drop imported cases.
    Path base = Files.createTempDirectory("sl-cases");
    Path missing = base.resolve("does_not_exist_yet");
    assertFalse("precondition: directory absent", Files.exists(missing));

    List<CaseFile> cases = CaseLoader.loadCases(missing.toString());
    assertNotNull("loadCases returns a list, never null", cases);
    assertTrue("the external cases directory is created", Files.isDirectory(missing));
  }

  private static String bigStrings(int count, int length) {
    String one = "\"" + "x".repeat(length) + "\"";
    return String.join(",", Collections.nCopies(count, one));
  }

  /** A minimal fully-valid case (mirrors CaseValidatorTest.VALID_JSON) with a __TITLE__ slot. */
  private static final String SMALL_VALID =
      """
      {
        "universal_title": "__TITLE__",
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
}

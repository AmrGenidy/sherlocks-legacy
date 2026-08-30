package engine;

import JsonDTO.CaseFile;
import JsonDTO.LocalizedCaseFile;
import extractors.CaseLoader;
import java.util.List;

/**
 * Loads real bundled cases through {@link CaseLoader} for the engine test suite.
 *
 * <p>Tests deliberately exercise the real extraction pipeline (CaseLoader -> LocalizedCaseFile ->
 * extractors) rather than hand-built mocks, so that the behaviour they pin is the behaviour the
 * shipping game produces.
 *
 * <ul>
 *   <li>{@code sapphire} — the bundled "The Stolen Sapphire" case. Two rooms, a LIE->TRUTH suspect,
 *       a combine rule and a final exam.
 *   <li>{@code fsm} — a test-only fixture that adds the TRUTH->PANIC leg the sapphire case lacks,
 *       so the full LIE->TRUTH->PANIC chain can be driven end-to-end through a real case file.
 * </ul>
 *
 * <p>Both fixtures live in {@code src/test/resources/testcases}. The sapphire fixture there is a
 * verbatim copy of the bundled {@code src/main/resources/cases/sapphire_case.json}. We deliberately
 * do NOT load from the {@code "cases"} directory: {@code CaseLoader} merges an external {@code
 * cases/} folder (tracked in this repo) that shadows the bundled resource with a richer variant
 * (different {@code startingInsightTokens}), which would make fixtures depend on developer-local
 * state. See {@code
 * .scratch/engine-test-suite/issues/05-bundled-vs-external-sapphire-divergence.md}.
 */
public final class EngineFixtures {

  public static final String SAPPHIRE_TITLE = "The Stolen Sapphire";
  public static final String FSM_TITLE = "The Butler Confession (Test Fixture)";
  public static final String TWO_QUESTION_EXAM_TITLE = "Two-Question Exam (Test Fixture)";

  private EngineFixtures() {}

  /** The bundled sapphire case, localized to English. */
  public static LocalizedCaseFile sapphire() {
    return localized("testcases", SAPPHIRE_TITLE);
  }

  /** A test-only fixture whose Final Exam has two questions (multi-question advance contract). */
  public static LocalizedCaseFile twoQuestionExam() {
    return localized("testcases", TWO_QUESTION_EXAM_TITLE);
  }

  /** The test-only FSM fixture (full LIE->TRUTH->PANIC chain), localized to English. */
  public static LocalizedCaseFile fsm() {
    return localized("testcases", FSM_TITLE);
  }

  private static LocalizedCaseFile localized(String directory, String universalTitle) {
    List<CaseFile> cases = CaseLoader.loadCases(directory);
    CaseFile match =
        cases.stream()
            .filter(c -> universalTitle.equalsIgnoreCase(c.getUniversalTitle()))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Fixture case '"
                            + universalTitle
                            + "' not found in resource directory '"
                            + directory
                            + "'. Loaded: "
                            + cases.stream().map(CaseFile::getUniversalTitle).toList()));
    return new LocalizedCaseFile(match, "en");
  }
}

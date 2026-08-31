package engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import common.dto.FinalExamChoiceDTO;
import common.dto.FinalExamQuestionDTO;
import common.dto.FinalExamSlotDTO;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * The Final Exam review must read as plain sentences with the player's actual choices — never raw
 * ids and never the correct answer (gui-exam-review). Pure checks on {@link ExamReview}.
 */
public class ExamReviewTest {

  private static final String NO_ANSWER = "(no answer)";

  /** A two-blank question mirroring the real case: murderer + motive. */
  private static FinalExamQuestionDTO twoSlotQuestion() {
    FinalExamSlotDTO slot1 =
        new FinalExamSlotDTO(
            "slot1",
            List.of(
                new FinalExamChoiceDTO("s1_opt1", "Lady Margaret"),
                new FinalExamChoiceDTO("s1_opt2", "Colonel Hastings")));
    FinalExamSlotDTO slot2 =
        new FinalExamSlotDTO(
            "slot2",
            List.of(
                new FinalExamChoiceDTO("s2_opt1", "greed"),
                new FinalExamChoiceDTO("s2_opt2", "revenge")));
    Map<String, FinalExamSlotDTO> slots = new LinkedHashMap<>();
    // Insertion order deliberately reversed to prove slot ORDER, not map order, drives the blanks.
    slots.put("slot2", slot2);
    slots.put("slot1", slot1);
    Map<String, String> correct = new LinkedHashMap<>();
    correct.put("slot1", "s1_opt2"); // Colonel Hastings
    correct.put("slot2", "s2_opt2"); // revenge
    return new FinalExamQuestionDTO(
        "Lord Blackwood was ultimately murdered by ____, whose primary motive was ____.",
        slots,
        correct);
  }

  @Test
  public void fillsBlanksWithChoiceTextInSlotOrder() {
    Map<String, String> given = new LinkedHashMap<>();
    given.put("slot1", "s1_opt2");
    given.put("slot2", "s2_opt2");
    assertEquals(
        "Lord Blackwood was ultimately murdered by Colonel Hastings, whose primary motive was revenge.",
        ExamReview.fillPrompt(twoSlotQuestion(), given, NO_ANSWER));
  }

  @Test
  public void usesThePlayersWrongChoiceNotTheCorrectOne() {
    Map<String, String> given = new LinkedHashMap<>();
    given.put("slot1", "s1_opt1"); // Lady Margaret (wrong)
    given.put("slot2", "s2_opt1"); // greed (wrong)
    String filled = ExamReview.fillPrompt(twoSlotQuestion(), given, NO_ANSWER);
    assertTrue(filled.contains("Lady Margaret"));
    assertTrue(filled.contains("greed"));
    // Never reveal the correct choice text on a wrong answer.
    assertFalse(filled.contains("Colonel Hastings"));
    assertFalse(filled.contains("revenge"));
  }

  @Test
  public void unansweredSlotShowsNoAnswerNotAnId() {
    Map<String, String> given = new LinkedHashMap<>();
    given.put("slot1", "s1_opt2"); // only the first slot answered
    String filled = ExamReview.fillPrompt(twoSlotQuestion(), given, NO_ANSWER);
    assertTrue(filled.contains("Colonel Hastings"));
    assertTrue(filled.contains(NO_ANSWER));
  }

  @Test
  public void neverLeaksSlotOrChoiceIds() {
    Map<String, String> given = new LinkedHashMap<>();
    given.put("slot1", "s1_opt2");
    given.put("slot2", "s2_opt2");
    String filled = ExamReview.fillPrompt(twoSlotQuestion(), given, NO_ANSWER);
    assertFalse(filled.contains("slot"));
    assertFalse(filled.contains("opt"));
    assertFalse(filled.contains("____"));
  }

  @Test
  public void correctnessMatchesTheCombination() {
    FinalExamQuestionDTO q = twoSlotQuestion();
    Map<String, String> right = Map.of("slot1", "s1_opt2", "slot2", "s2_opt2");
    Map<String, String> wrong = Map.of("slot1", "s1_opt1", "slot2", "s2_opt2");
    assertTrue(ExamReview.isCorrect(q, right));
    assertFalse(ExamReview.isCorrect(q, wrong));
    assertFalse(ExamReview.isCorrect(q, null));
  }

  @Test
  public void whollyUnansweredQuestionFillsEveryBlankWithNoAnswer() {
    String filled = ExamReview.fillPrompt(twoSlotQuestion(), null, NO_ANSWER);
    assertEquals(
        "Lord Blackwood was ultimately murdered by (no answer), whose primary motive was (no answer).",
        filled);
  }
}

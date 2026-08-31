package engine;

import common.dto.FinalExamChoiceDTO;
import common.dto.FinalExamQuestionDTO;
import common.dto.FinalExamSlotDTO;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the human-readable Final Exam review (gui-exam-review): the question prompt with its
 * {@code ____} blanks filled by the player's chosen choice <b>text</b>, in slot order. Pure —
 * operates on the exam DTOs and the player's answer map only — so it is unit-testable and shared by
 * single player and multiplayer (the engine composes it once into the result).
 *
 * <p>It deliberately never emits a slot/choice id, and never reads {@code correctCombination} into
 * any text (only into the right/wrong boolean), so the review can be shown without spoiling a
 * retry.
 */
public final class ExamReview {

  private ExamReview() {}

  private static final String BLANK = "____";

  /** Whether the player's answer matches the correct combination exactly. */
  public static boolean isCorrect(FinalExamQuestionDTO question, Map<String, String> given) {
    return given != null && given.equals(question.getCorrectCombination());
  }

  /**
   * The prompt with each {@code ____} replaced, left-to-right, by the chosen choice text for slot1,
   * slot2, … An unanswered (or unrecognised) slot becomes {@code noAnswer} — never a raw id.
   */
  public static String fillPrompt(
      FinalExamQuestionDTO question, Map<String, String> given, String noAnswer) {
    String prompt = question.getQuestionPrompt() == null ? "" : question.getQuestionPrompt();

    List<String> fills = new ArrayList<>();
    for (String slotId : slotOrder(question)) {
      fills.add(chosenText(question, slotId, given, noAnswer));
    }

    StringBuilder out = new StringBuilder();
    int from = 0;
    int blankIndex = 0;
    int pos;
    while ((pos = prompt.indexOf(BLANK, from)) >= 0) {
      out.append(prompt, from, pos);
      out.append(blankIndex < fills.size() ? fills.get(blankIndex) : noAnswer);
      from = pos + BLANK.length();
      blankIndex++;
    }
    out.append(prompt.substring(from));
    return out.toString();
  }

  /** The chosen choice's TEXT for a slot, or {@code noAnswer} if unanswered/unrecognised. */
  private static String chosenText(
      FinalExamQuestionDTO question, String slotId, Map<String, String> given, String noAnswer) {
    String choiceId = given == null ? null : given.get(slotId);
    if (choiceId == null) {
      return noAnswer;
    }
    Map<String, FinalExamSlotDTO> slots = question.getSlots();
    FinalExamSlotDTO slot = slots == null ? null : slots.get(slotId);
    if (slot != null && slot.getChoices() != null) {
      for (FinalExamChoiceDTO choice : slot.getChoices()) {
        if (choiceId.equals(choice.getChoiceId())) {
          return choice.getChoiceText();
        }
      }
    }
    return noAnswer; // unknown id → never leak the id itself
  }

  /** Slot ids ordered by their trailing number (slot1 → slot2 → … → slot10), then lexically. */
  private static List<String> slotOrder(FinalExamQuestionDTO question) {
    Set<String> keys = new LinkedHashSet<>();
    if (question.getSlots() != null) {
      keys.addAll(question.getSlots().keySet());
    }
    if (question.getCorrectCombination() != null) {
      keys.addAll(question.getCorrectCombination().keySet());
    }
    List<String> order = new ArrayList<>(keys);
    order.sort(
        Comparator.comparingInt(ExamReview::trailingNumber)
            .thenComparing(Comparator.naturalOrder()));
    return order;
  }

  private static int trailingNumber(String s) {
    int i = s.length();
    while (i > 0 && Character.isDigit(s.charAt(i - 1))) {
      i--;
    }
    if (i == s.length()) {
      return Integer.MAX_VALUE; // no numeric suffix → sort last
    }
    try {
      return Integer.parseInt(s.substring(i));
    } catch (NumberFormatException e) {
      return Integer.MAX_VALUE;
    }
  }
}

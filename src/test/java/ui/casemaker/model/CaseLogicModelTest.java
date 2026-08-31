package ui.casemaker.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Case-logic model behaviour (slice 4): registries, exam slot/choice integrity. */
public class CaseLogicModelTest {

  @Test
  public void combineResultDeductionFeedsTheEvidenceRegistry() {
    CaseDraft draft = new CaseDraft();
    CombineRuleDraft rule = draft.addCombineRule();
    rule.setResultDeductionId("the_full_picture");

    assertTrue(draft.deductionIds().contains("the_full_picture"));
    assertTrue(draft.evidenceChoices().contains("the_full_picture"));
  }

  @Test
  public void firstChoiceIsCorrectByDefaultAndCorrectTracksRemoval() {
    FinalExamQuestionDraft question = new CaseDraft().addExamQuestion();
    ExamSlotDraft slot = question.addSlot();
    ExamChoiceDraft first = slot.addChoice("c1");
    ExamChoiceDraft second = slot.addChoice("c2");

    assertSame("first choice added is correct by default", first, slot.getCorrectChoice());

    slot.setCorrectChoice(second);
    slot.removeChoice(second); // removing the correct choice falls back to the first remaining
    assertSame(first, slot.getCorrectChoice());
  }

  @Test
  public void slotIdsDefaultToSequentialNames() {
    FinalExamQuestionDraft question = new CaseDraft().addExamQuestion();
    assertEquals("slot1", question.addSlot().getSlotId());
    assertEquals("slot2", question.addSlot().getSlotId());
  }
}

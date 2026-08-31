package ui.casemaker.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One fill-in slot of a Final Exam question: an addressable {@code slotId}, its candidate {@link
 * ExamChoiceDraft}s, and which choice is correct. The correct choice is held as a reference to a
 * choice in this slot, so it can only ever be one of the defined choices (no dangling correct id).
 */
public final class ExamSlotDraft {

  private String slotId;
  private final List<ExamChoiceDraft> choices = new ArrayList<>();
  private ExamChoiceDraft correctChoice;

  ExamSlotDraft(String slotId) {
    this.slotId = slotId;
  }

  public String getSlotId() {
    return slotId;
  }

  public void setSlotId(String slotId) {
    this.slotId = slotId;
  }

  public ExamChoiceDraft addChoice(String choiceId) {
    ExamChoiceDraft choice = new ExamChoiceDraft(choiceId);
    choices.add(choice);
    if (correctChoice == null) {
      correctChoice = choice; // first choice is correct by default
    }
    return choice;
  }

  public void removeChoice(ExamChoiceDraft choice) {
    choices.remove(choice);
    if (correctChoice == choice) {
      correctChoice = choices.isEmpty() ? null : choices.get(0);
    }
  }

  public List<ExamChoiceDraft> getChoices() {
    return Collections.unmodifiableList(choices);
  }

  public ExamChoiceDraft getCorrectChoice() {
    return correctChoice;
  }

  public void setCorrectChoice(ExamChoiceDraft choice) {
    if (choice == null || choices.contains(choice)) {
      this.correctChoice = choice;
    }
  }
}

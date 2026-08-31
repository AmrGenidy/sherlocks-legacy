package ui.casemaker.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One Final Exam question: a {@code prompt} (with blanks) and the {@link ExamSlotDraft}s the
 * detective fills in. Slot ids default to {@code slot1, slot2, …} but are editable.
 */
public final class FinalExamQuestionDraft {

  private final LocalizedText prompt = new LocalizedText();
  private final List<ExamSlotDraft> slots = new ArrayList<>();

  public String getPrompt() {
    return prompt.get();
  }

  public void setPrompt(String prompt) {
    this.prompt.set(prompt);
  }

  public LocalizedText promptText() {
    return prompt;
  }

  /** Adds a slot with a default id ({@code slot<N>}); the author may rename it. */
  public ExamSlotDraft addSlot() {
    ExamSlotDraft slot = new ExamSlotDraft("slot" + (slots.size() + 1));
    slots.add(slot);
    return slot;
  }

  public void removeSlot(ExamSlotDraft slot) {
    slots.remove(slot);
  }

  public List<ExamSlotDraft> getSlots() {
    return Collections.unmodifiableList(slots);
  }
}

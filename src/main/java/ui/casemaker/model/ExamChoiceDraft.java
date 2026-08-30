package ui.casemaker.model;

/**
 * One selectable answer in a Final Exam slot: an addressable {@code choiceId} and its {@code text}.
 */
public final class ExamChoiceDraft {

  private String choiceId;
  private final LocalizedText text = new LocalizedText();

  ExamChoiceDraft(String choiceId) {
    this.choiceId = choiceId;
  }

  public String getChoiceId() {
    return choiceId;
  }

  public void setChoiceId(String choiceId) {
    this.choiceId = choiceId;
  }

  public String getText() {
    return text.get();
  }

  public void setText(String text) {
    this.text.set(text);
  }

  public LocalizedText textLocalized() {
    return text;
  }
}

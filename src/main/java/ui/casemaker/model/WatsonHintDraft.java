package ui.casemaker.model;

/**
 * An authored Watson Hint (CONTEXT.md): a hint Dr. Watson offers, grouped by {@code category} (e.g.
 * {@code general}, {@code contradiction}, {@code red_herring}). {@code id} addresses the hint;
 * {@code text} is the working-language wording (DEC-8).
 */
public final class WatsonHintDraft {

  private String category = "general";
  private String id;
  private final LocalizedText text = new LocalizedText();

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    if (category != null && !category.isBlank()) {
      this.category = category.trim();
    }
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
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

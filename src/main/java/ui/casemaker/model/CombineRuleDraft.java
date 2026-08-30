package ui.casemaker.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An authored Combine Rule (CONTEXT.md): merging the clues named in {@code requires} unlocks a
 * derived Deduction ({@code resultDeductionId}, a mint site), revealing {@code resultText} and
 * awarding {@code tokenReward} Insight Tokens. {@code requires} ids come from the editor's registry
 * (Object ids ∪ Deduction ids — never free-typed, DEC-2).
 */
public final class CombineRuleDraft {

  private final List<String> requires = new ArrayList<>();
  private String resultDeductionId;
  private final LocalizedText resultText = new LocalizedText();
  private Integer tokenReward = 1;
  private boolean repeatable;

  public List<String> getRequires() {
    return Collections.unmodifiableList(requires);
  }

  public void setRequires(List<String> ids) {
    requires.clear();
    if (ids != null) {
      requires.addAll(ids);
    }
  }

  /** Adds a required clue/deduction id if not already present. */
  public void addRequire(String id) {
    if (id != null && !id.isBlank() && !requires.contains(id)) {
      requires.add(id);
    }
  }

  public void removeRequire(String id) {
    requires.remove(id);
  }

  public String getResultDeductionId() {
    return resultDeductionId;
  }

  public void setResultDeductionId(String resultDeductionId) {
    this.resultDeductionId = resultDeductionId;
  }

  public String getResultText() {
    return resultText.get();
  }

  public void setResultText(String resultText) {
    this.resultText.set(resultText);
  }

  public LocalizedText resultTextLocalized() {
    return resultText;
  }

  public Integer getTokenReward() {
    return tokenReward;
  }

  public void setTokenReward(Integer tokenReward) {
    this.tokenReward = tokenReward;
  }

  public boolean isRepeatable() {
    return repeatable;
  }

  public void setRepeatable(boolean repeatable) {
    this.repeatable = repeatable;
  }
}

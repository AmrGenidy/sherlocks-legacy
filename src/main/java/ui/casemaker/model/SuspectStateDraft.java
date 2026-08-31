package ui.casemaker.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One behavioural mode of a {@link SuspectDraft} — a Suspect State (LIE/TRUTH/PANIC): the {@code
 * statement} the suspect gives while in this state and the Contradiction Rules that can move them
 * out of it.
 */
public final class SuspectStateDraft {

  private final LocalizedText statement = new LocalizedText();
  private final List<ContradictionDraft> contradictions = new ArrayList<>();

  public String getStatement() {
    return statement.get();
  }

  public void setStatement(String statement) {
    this.statement.set(statement);
  }

  public LocalizedText statementText() {
    return statement;
  }

  public ContradictionDraft addContradiction() {
    ContradictionDraft rule = new ContradictionDraft();
    contradictions.add(rule);
    return rule;
  }

  public void removeContradiction(ContradictionDraft rule) {
    contradictions.remove(rule);
  }

  public List<ContradictionDraft> getContradictions() {
    return Collections.unmodifiableList(contradictions);
  }
}

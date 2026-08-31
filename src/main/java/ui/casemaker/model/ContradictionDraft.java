package ui.casemaker.model;

/**
 * An authored Contradiction Rule (CONTEXT.md) within a {@link SuspectStateDraft}: presenting {@code
 * evidenceId} to the suspect in this state transitions them to {@code nextState}, optionally
 * minting a Deduction ({@code rewardDeductionId}) and showing {@code successMessage}.
 *
 * <p>{@code evidenceId} is chosen from the editor's id registry (an Object id or an existing
 * Deduction id — never free-typed, so it can't dangle, DEC-2). {@code rewardDeductionId} is a
 * <em>mint site</em>: naming it creates a new Deduction other rules may then reference.
 */
public final class ContradictionDraft {

  private String evidenceId;
  private String nextState;
  private String rewardDeductionId;
  private final LocalizedText successMessage = new LocalizedText();

  public String getEvidenceId() {
    return evidenceId;
  }

  public void setEvidenceId(String evidenceId) {
    this.evidenceId = evidenceId;
  }

  public String getNextState() {
    return nextState;
  }

  public void setNextState(String nextState) {
    this.nextState = nextState;
  }

  public String getRewardDeductionId() {
    return rewardDeductionId;
  }

  public void setRewardDeductionId(String rewardDeductionId) {
    this.rewardDeductionId = rewardDeductionId;
  }

  public String getSuccessMessage() {
    return successMessage.get();
  }

  public void setSuccessMessage(String successMessage) {
    this.successMessage.set(successMessage);
  }

  public LocalizedText successMessageText() {
    return successMessage;
  }
}

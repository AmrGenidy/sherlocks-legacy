package ui.casemaker.model;

/**
 * An authored Rank Tier (CONTEXT.md): a tier of detective performance scored by how few Deductions
 * were used. {@code maxDeductions} is the cap that still qualifies for this tier; {@code
 * defaultRank} marks the fallback tier; {@code winningStatement} is the tier's Winning Message (the
 * JSON field keeps its legacy name).
 */
public final class RankTierDraft {

  private String rankName;
  private int maxDeductions;
  private final LocalizedText description = new LocalizedText();
  private boolean defaultRank;
  private final LocalizedText winningStatement = new LocalizedText(); // Winning Message

  public String getRankName() {
    return rankName;
  }

  public void setRankName(String rankName) {
    this.rankName = rankName;
  }

  public int getMaxDeductions() {
    return maxDeductions;
  }

  public void setMaxDeductions(int maxDeductions) {
    this.maxDeductions = maxDeductions;
  }

  public String getDescription() {
    return description.get();
  }

  public void setDescription(String description) {
    this.description.set(description);
  }

  public LocalizedText descriptionText() {
    return description;
  }

  public boolean isDefaultRank() {
    return defaultRank;
  }

  public void setDefaultRank(boolean defaultRank) {
    this.defaultRank = defaultRank;
  }

  public String getWinningStatement() {
    return winningStatement.get();
  }

  public void setWinningStatement(String winningStatement) {
    this.winningStatement.set(winningStatement);
  }

  public LocalizedText winningStatementText() {
    return winningStatement;
  }
}

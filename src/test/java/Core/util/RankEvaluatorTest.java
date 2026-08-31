package Core.util;

import static org.junit.Assert.*;

import Core.Rank;
import JsonDTO.CaseData;
import JsonDTO.CaseFile;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class RankEvaluatorTest {

  @Test
  public void testEvaluateSherlockRank() {
    MockCaseData caseData = new MockCaseData();
    setupTiers(caseData);

    // Max is 3
    Rank rank = RankEvaluator.evaluate(2, caseData);
    assertEquals("Sherlock Holmes", rank.getRankName());
    assertEquals("You are a genius!", rank.getWinningStatement());
    assertFalse(rank.isDefault());
  }

  @Test
  public void testEvaluateSherlockRankBoundary() {
    MockCaseData caseData = new MockCaseData();
    setupTiers(caseData);

    // Max is 3
    Rank rank = RankEvaluator.evaluate(3, caseData);
    assertEquals("Sherlock Holmes", rank.getRankName());
  }

  @Test
  public void testEvaluateWatsonRank() {
    MockCaseData caseData = new MockCaseData();
    setupTiers(caseData);

    // Holmes <= 3, Watson <= 7
    Rank rank = RankEvaluator.evaluate(4, caseData);
    assertEquals("Dr. Watson", rank.getRankName());
    assertEquals("Good job!", rank.getWinningStatement());
  }

  @Test
  public void testEvaluateDefaultRank() {
    MockCaseData caseData = new MockCaseData();
    setupTiers(caseData);

    // Holmes <= 3, Watson <= 7, Default > 7
    Rank rank = RankEvaluator.evaluate(10, caseData);
    assertEquals("Curious Observer", rank.getRankName());
    assertEquals("Keep trying!", rank.getWinningStatement());
    assertTrue(rank.isDefault());
  }

  @Test
  public void testEvaluateFallbackWhenNoTiers() {
    MockCaseData caseData = new MockCaseData();
    caseData.tiers = new ArrayList<>(); // Empty

    Rank rank = RankEvaluator.evaluate(5, caseData);
    assertEquals("Investigator", rank.getRankName());
    assertTrue(rank.isDefault());
  }

  @Test
  public void testEvaluateZeroDeductionsWithDefaultZero() {
    MockCaseData caseData = new MockCaseData();
    caseData.tiers = new ArrayList<>();

    // Implicit 0 maxDeductions for default
    CaseFile.RankTierData observer = new CaseFile.RankTierData();
    observer.rankName = "Curious Observer";
    observer.defaultRank = true;
    observer.winningStatement = "Keep trying!";
    // maxDeductions NOT set, so 0
    caseData.tiers.add(observer);

    CaseFile.RankTierData holmes = new CaseFile.RankTierData();
    holmes.rankName = "Sherlock Holmes";
    holmes.maxDeductions = 1;
    holmes.winningStatement = "Genius!";
    caseData.tiers.add(holmes);

    // Deductions = 0. Should be Holmes (<=1), NOT Observer (<=0) because Observer
    // is default fallback only.
    Rank rank = RankEvaluator.evaluate(0, caseData);
    assertEquals("Sherlock Holmes", rank.getRankName());
  }

  private void setupTiers(MockCaseData caseData) {
    caseData.tiers = new ArrayList<>();

    CaseFile.RankTierData holmes = new CaseFile.RankTierData();
    holmes.rankName = "Sherlock Holmes";
    holmes.maxDeductions = 3;
    holmes.winningStatement = "You are a genius!";
    caseData.tiers.add(holmes);

    CaseFile.RankTierData watson = new CaseFile.RankTierData();
    watson.rankName = "Dr. Watson";
    watson.maxDeductions = 7;
    watson.winningStatement = "Good job!";
    caseData.tiers.add(watson);

    CaseFile.RankTierData observer = new CaseFile.RankTierData();
    observer.rankName = "Curious Observer";
    observer.defaultRank = true;
    observer.winningStatement = "Keep trying!";
    caseData.tiers.add(observer);
  }

  // Minimal Mock for CaseData
  private static class MockCaseData implements CaseData {
    public List<CaseFile.RankTierData> tiers;

    @Override
    public List<CaseFile.RankTierData> getRankingTiers() {
      return tiers;
    }

    // Stubbed methods
    @Override
    public String getTitle() {
      return "";
    }

    @Override
    public String getInvitation() {
      return "";
    }

    @Override
    public String getDescription() {
      return "";
    }

    @Override
    public String getStartingRoom() {
      return "";
    }

    @Override
    public List<CaseFile.SuspectData> getSuspects() {
      return null;
    }

    @Override
    public List<CaseFile.RoomData> getRooms() {
      return null;
    }

    @Override
    public common.dto.FinalExamDTO getFinalExam() {
      return null;
    }

    @Override
    public List<String> getTasks() {
      return null;
    }

    @Override
    public String getWinningMessage() {
      return "";
    }

    @Override
    public String getWatsonImagePath() {
      return null;
    }

    @Override
    public Integer getStartingInsightTokens() {
      return 0;
    }

    @Override
    public List<CaseFile.CombineRule> getCombineLogic() {
      return null;
    }

    @Override
    public java.util.Map<String, List<JsonDTO.LocalizedCaseFile.LocalizedWatsonHint>>
        getStructuredWatsonHints() {
      return null;
    }

    @Override
    public CaseFile.RedHerringMetadata getRedHerrings() {
      return null;
    }

    @Override
    public String getLanguageCode() {
      return "en";
    }

    @Override
    public JsonDTO.LocalizedCaseFile.LocalizedCaseFileBlock getCaseFile() {
      return null;
    }
  }
}

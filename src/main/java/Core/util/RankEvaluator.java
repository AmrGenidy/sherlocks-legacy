package Core.util;

import Core.Rank;
import JsonDTO.CaseFile;
import java.util.Comparator;
import java.util.List;
import JsonDTO.CaseData;

public class RankEvaluator {

    public static Rank evaluate(int sessionDeduceCount, CaseData caseFile) {
        if (caseFile == null || caseFile.getRankingTiers() == null || caseFile.getRankingTiers().isEmpty()) {
            // Sensible fallback if data is missing, though this should not happen in
            // production
            return new Rank("Investigator", 999, "The case was solved.", "You solved the case.", true);
        }

        List<CaseFile.RankTierData> tiers = caseFile.getRankingTiers();

        // 1. Sort tiers by maxDeductions ASC
        tiers.sort(Comparator.comparingInt(CaseFile.RankTierData::getMaxDeductions));

        Rank defaultRank = null;

        // 2. Find the FIRST tier where sessionDeduceCount <= maxDeductions
        for (CaseFile.RankTierData tier : tiers) {
            boolean isDefault = tier.isDefaultRank();

            if (isDefault) {
                // Capture default rank for fallback but DO NOT match it directly based on
                // deductions
                // (This prevents default rank with 0 maxDeductions from shadowing real ranks)
                defaultRank = new Rank(
                        tier.getRankName(),
                        Integer.MAX_VALUE, // Default rank has no effective max for fallback purposes
                        tier.getDescription(),
                        tier.getWinningStatement(),
                        true);
                continue; // Skip the standard deduction check for default tiers
            }

            // Check if this tier is a match based on deductions
            if (sessionDeduceCount <= tier.getMaxDeductions()) {
                return new Rank(
                        tier.getRankName(),
                        tier.getMaxDeductions(),
                        tier.getDescription(),
                        tier.getWinningStatement(),
                        false);
            }
        }

        // 3. Fallback to default rank if no specific tier matched
        if (defaultRank != null) {
            return defaultRank;
        }

        // 4. Absolute fallback if configuration is broken (no default specified)
        return new Rank("Investigator", 999, "The case was solved.", "You solved the case.", true);
    }
}
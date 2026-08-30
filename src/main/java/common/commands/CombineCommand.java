package common.commands;

import JsonDTO.CaseData;
import JsonDTO.CaseFile;
import common.dto.JournalEntryDTO;
import common.dto.JournalEntryType;
import common.dto.TextMessage;
import common.interfaces.GameActionContext;
import java.io.Serial;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class CombineCommand extends BaseCommand {
    @Serial
    private static final long serialVersionUID = 1L;

    private String noteId1;
    private String noteId2;

    // Jackson needs a default constructor
    public CombineCommand() {
        super(true);
    }

    @com.fasterxml.jackson.annotation.JsonCreator
    public CombineCommand(
            @com.fasterxml.jackson.annotation.JsonProperty("noteId1") String noteId1,
            @com.fasterxml.jackson.annotation.JsonProperty("noteId2") String noteId2) {
        super(true); // Is actionable
        common.WireLimits.requireLength(noteId1, common.WireLimits.MAX_ID_LENGTH, "noteId1");
        common.WireLimits.requireLength(noteId2, common.WireLimits.MAX_ID_LENGTH, "noteId2");
        this.noteId1 = noteId1;
        this.noteId2 = noteId2;
    }

    public String getNoteId1() {
        return noteId1;
    }

    public String getNoteId2() {
        return noteId2;
    }

    @Override
    protected void executeCommandLogic(GameActionContext context) {
        if (context.isCombineOnCooldown()) {
            long remaining = context.getCombineCooldownRemaining();
            context.sendResponseToPlayer(getPlayerId(),
                    new TextMessage("[SERVER] Combine is on cooldown for " + remaining + " seconds.", true,
                            "game.combine.cooldownActive", java.util.List.of(String.valueOf(remaining))));
            // Also surface it as a popup so it is seen while the Pinboard covers the terminal.
            context.sendResponseToPlayer(getPlayerId(),
                    new common.dto.DialogueEventDTO("Combine on Cooldown",
                            "Combine is locked. Try again in " + remaining + " seconds.",
                            common.dto.DialogueType.CONTRADICTION,
                            "game.popup.combineCooldown",
                            "game.combine.cooldownActive",
                            java.util.List.of(String.valueOf(remaining))));
            return;
        }

        if (noteId1 == null || noteId2 == null) {
            context.sendResponseToPlayer(getPlayerId(), new TextMessage("Usage: combine <noteId1> <noteId2>", true,
                    "game.combine.usage", null));
            return;
        }

        // 1. Verify availability in Journal
        // We generally allow combining any journal entries (clues, statements, etc.)
        // But we must ensure the player actually HAS these entries.
        // We'll check by ID or SourceID.

        List<JournalEntryDTO> journal = context.getJournalEntries(getPlayerId());

        boolean hasNote1 = journal.stream()
                .anyMatch(e -> e.getId().equalsIgnoreCase(noteId1) || e.getSourceId().equalsIgnoreCase(noteId1));
        boolean hasNote2 = journal.stream()
                .anyMatch(e -> e.getId().equalsIgnoreCase(noteId2) || e.getSourceId().equalsIgnoreCase(noteId2));

        if (!hasNote1 || !hasNote2) {
            context.sendResponseToPlayer(getPlayerId(),
                    new TextMessage("You must discover both notes before combining them.", true,
                            "game.combine.needBoth", null));
            return;
        }

        // 2. Find matching rule
        CaseData caseData = context.getSelectedCase();
        List<CaseFile.CombineRule> rules = caseData.getCombineLogic();

        if (rules == null || rules.isEmpty()) {
            context.reportCombineFailure();
            context.sendResponseToPlayer(getPlayerId(),
                    new TextMessage("[SERVER] Those notes don't connect (no combine logic).", false,
                            "game.combine.noLogic", null));
            // Also surface it as a popup so the player sees the result while the Pinboard covers
            // the terminal.
            context.sendResponseToPlayer(getPlayerId(),
                    new common.dto.DialogueEventDTO("No Combination",
                            "Those notes don't connect.", common.dto.DialogueType.CONTRADICTION,
                            "game.popup.noCombination", "game.combine.noLogic", null));
            return;
        }

        CaseFile.CombineRule matchingRule = null;
        for (CaseFile.CombineRule rule : rules) {
            if (rule.getRequires() != null && rule.getRequires().size() == 2) {
                Set<String> required = new HashSet<>(rule.getRequires());
                // We check against input IDs. Note: inputs might be IDs or SourceIDs.
                // The rule "requires" likely refers to source IDs (e.g. "cigar_stub").
                // But the command inputs might be "obj:cigar_stub" or just "cigar_stub".
                // We should normalize or check looser.

                // Let's resolve the input args to their SourceIDs if possible
                String source1 = resolveSourceId(journal, noteId1);
                String source2 = resolveSourceId(journal, noteId2);

                if (required.contains(source1) && required.contains(source2)) {
                    matchingRule = rule;
                    break;
                }
            }
        }

        if (matchingRule == null) {
            context.reportCombineFailure();
            context.sendResponseToPlayer(getPlayerId(),
                    new TextMessage("[SERVER] Those notes don't connect (yet).", false,
                            "game.combine.noMatch", null));
            // Also surface it as a popup so the player sees the result while the Pinboard covers
            // the terminal.
            context.sendResponseToPlayer(getPlayerId(),
                    new common.dto.DialogueEventDTO("No Combination",
                            "Those notes don't connect (yet).", common.dto.DialogueType.CONTRADICTION,
                            "game.popup.noCombination", "game.combine.noMatch", null));
            return;
        }

        // 3. Process Success
        context.reportCombineSuccess();

        // Check for duplicate reward matching the RESULT ID
        String resultDeductionId = matchingRule.getResultDeductionId();
        JournalEntryDTO existingEntry = context.getJournalEntryById(getPlayerId(), "ded:" + resultDeductionId);

        if (existingEntry != null && !matchingRule.isRepeatable()) {
            context.sendResponseToPlayer(getPlayerId(),
                    new TextMessage("[SERVER] You've already drawn that conclusion.", false,
                            "game.combine.duplicate", null));
            return;
        }

        // Award Tokens
        int tokens = matchingRule.getTokenReward();
        if (tokens > 0) {
            for (int i = 0; i < tokens; i++) {
                context.awardInsightToken();
            }
            context.sendResponseToPlayer(getPlayerId(),
                    new TextMessage("[SERVER] Combine success! +" + tokens + " Insight Token(s).", false,
                            "game.combine.success", java.util.List.of(String.valueOf(tokens))));
        }

        // Add Journal Entry (Deduction). The result text is authored case content localized in the
        // case JSON; resolve it in the language the case was loaded in (falling back to English),
        // instead of always using English.
        String lang =
                (caseData.getLanguageCode() != null && !caseData.getLanguageCode().isBlank())
                        ? caseData.getLanguageCode()
                        : "en";
        String text = matchingRule.getResultText().getOrDefault(lang, matchingRule.getResultText().get("en"));

        JournalEntryDTO newEntry = new JournalEntryDTO(
                "ded:" + resultDeductionId,
                JournalEntryType.DEDUCTION,
                resultDeductionId,
                "Reasoning: " + resultDeductionId, // Title could be better?
                text,
                getPlayerId(),
                System.currentTimeMillis());
        context.addJournalEntry(newEntry);
        context.sendResponseToPlayer(getPlayerId(), new TextMessage("[SERVER] Combine success: " + text, false,
                "game.combine.successResult", java.util.List.of(text)));
        // Trigger UI Pop-up (Broadcast to all allowed, similar to Contradiction)
        context.broadcastToSession(
                new common.dto.DialogueEventDTO("Combination Result", text, common.dto.DialogueType.DEDUCTION,
                        "game.popup.combinationResult", null, null), null);
    }

    private String resolveSourceId(List<JournalEntryDTO> journal, String inputId) {
        // Try to find the entry and return its sourceId. result to inputId if not
        // found/null.
        Optional<JournalEntryDTO> entry = journal.stream()
                .filter(e -> e.getId().equalsIgnoreCase(inputId) || e.getSourceId().equalsIgnoreCase(inputId))
                .findFirst();
        return entry.map(journalEntryDTO -> journalEntryDTO.getSourceId() != null ? journalEntryDTO.getSourceId()
                : journalEntryDTO.getId()).orElse(inputId);
    }

    @Override
    public String getDescription() {
        return "Combine two notes to gain new insights. Usage: combine <id1> <id2>";
    }
}

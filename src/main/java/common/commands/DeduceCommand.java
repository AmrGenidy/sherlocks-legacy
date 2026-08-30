package common.commands;

import Core.Detective;
import Core.GameObject;
import Core.Room;
import Core.Suspect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import common.dto.DialogueEventDTO;
import common.dto.DialogueType;
import common.dto.JournalEntryDTO;
import common.dto.JournalEntryType;
import common.dto.TextMessage;
import common.interfaces.GameActionContext;
import java.io.Serial;
import java.util.Optional;

public class DeduceCommand extends BaseCommand {
    @Serial
    private static final long serialVersionUID = 1L;
    private final String targetName; // Renamed from objectName

    @JsonCreator
    public DeduceCommand(@JsonProperty("targetName") String targetName) {
        super(true);
        if (targetName == null || targetName.trim().isEmpty()) {
            throw new IllegalArgumentException("Target name cannot be null or empty for DeduceCommand.");
        }
        common.WireLimits.requireLength(targetName, common.WireLimits.MAX_NAME_LENGTH, "targetName");
        this.targetName = targetName.trim();
    }

    public String getTargetName() {
        return targetName;
    }

    @Override
    protected void executeCommandLogic(GameActionContext context) {
        Detective playerDetective = context.getPlayerDetective(getPlayerId());
        Room currentRoom = context.getCurrentRoomForPlayer(getPlayerId());

        if (currentRoom == null || playerDetective == null) {
            context.sendResponseToPlayer(getPlayerId(),
                    new TextMessage("Error: Cannot perform deduction. Invalid player or room state.", true));
            return;
        }

        // --- NEW UNIFIED LOGIC ---

        // Step 1: Check if the target is a GameObject in the room.
        GameObject objectToDeduce = currentRoom.getObject(this.targetName);
        if (objectToDeduce != null) {
            handleDeduceGameObject(context, playerDetective, objectToDeduce);
            return;
        }

        // Step 2: If not an object, check if it's a Suspect in the room.
        // We get ALL suspects and filter for those in the current room.
        Optional<Suspect> suspectToDeduce = context.getAllSuspects().stream()
                .filter(s -> s.getCurrentRoom() != null && s.getCurrentRoom().equals(currentRoom))
                .filter(s -> s.getName().equalsIgnoreCase(this.targetName))
                .findFirst();

        if (suspectToDeduce.isPresent()) {
            handleDeduceSuspect(context, playerDetective, suspectToDeduce.get());
            return;
        }

        // Step 3: If it's neither, the target is not here.
        context.sendResponseToPlayer(getPlayerId(),
                new TextMessage("There is no '" + this.targetName + "' here to deduce from.", false,
                        "game.deduce.noTarget", java.util.List.of(this.targetName)));
    }

    // --- HELPER METHODS FOR CLEANLINESS ---
    private void handleDeduceGameObject(GameActionContext context, Detective detective, GameObject object) {
        String deductionId = "ded:" + object.getId();

        // DUPLICATE CHECK: If entry exists in SHARED journal, it's free/already paid.
        common.dto.JournalEntryDTO existing = context.getJournalEntryById(getPlayerId(), deductionId);
        if (existing != null) {
            // Already deduced by someone (or this player)
            context.sendResponseToPlayer(getPlayerId(), new TextMessage(
                    "You recall your previous deduction about " + object.getName() + ": " + existing.getText(), false,
                    "game.deduce.recall", java.util.List.of(object.getName(), existing.getText())));
            return;
        }

        // Check if we can spend a shared token FIRST
        boolean spentToken = context.trySpendInsightToken();

        if (spentToken) {
            context.sendResponseToPlayer(getPlayerId(), new TextMessage("[SERVER] Spent 1 Insight Token.", false,
                    "game.deduce.spentToken", null));
            // DO NOT increment session deduce count here
        } else {
            context.sendResponseToPlayer(getPlayerId(),
                    new TextMessage("[SERVER] No Insight Tokens available. Deduction count increased.", false,
                            "game.deduce.noTokens", null));

            // Only increment penalty if NO token was spent
            if (!detective.incrementDeduceCount(object.getName())) {
                // This local check might still be useful, but the Session Journal check above
                // is the authority now.
            }
            // Increment session stats only if NO token was spent
            context.incrementSessionDeduceCount();
        }

        String clue = object.getDeduce();
        if (clue == null || clue.trim().isEmpty()) {
            clue = "You ponder about the " + object.getName() + " but gain no new insights.";
        }

        // Send Dialogue Event (Local Only). Title shows the per-language Display Name; the journal
        // source id stays keyed on the Universal id.
        context.sendResponseToPlayer(getPlayerId(), new DialogueEventDTO(
                "Deduction: " + object.getDisplayName(),
                clue,
                DialogueType.DEDUCTION));

        JournalEntryDTO entry = new JournalEntryDTO(
                deductionId,
                JournalEntryType.DEDUCTION,
                object.getId(),
                "Deduction: " + object.getDisplayName(),
                clue,
                getPlayerId(),
                System.currentTimeMillis());
        context.addJournalEntry(entry);

        context.sendResponseToPlayer(getPlayerId(),
                new TextMessage("Team deductions used: " + context.getSessionDeduceCount(), false,
                        "game.deduce.teamCount", java.util.List.of(String.valueOf(context.getSessionDeduceCount()))));
    }

    private void handleDeduceSuspect(GameActionContext context, Detective detective, Suspect suspect) {
        String deductionId = "ded:" + suspect.getId();

        // DUPLICATE CHECK
        common.dto.JournalEntryDTO existing = context.getJournalEntryById(getPlayerId(), deductionId);
        if (existing != null) {
            context.sendResponseToPlayer(getPlayerId(),
                    new TextMessage(
                            "You recall your previous deduction about " + suspect.getName() + ": " + existing.getText(),
                            false,
                            "game.deduce.recall",
                            java.util.List.of(suspect.getName(), existing.getText())));
            return;
        }

        // Check if we can spend a shared token FIRST
        boolean spentToken = context.trySpendInsightToken();

        if (spentToken) {
            context.sendResponseToPlayer(getPlayerId(), new TextMessage("[SERVER] Spent 1 Insight Token.", false,
                    "game.deduce.spentToken", null));
            // DO NOT increment session deduce count
        } else {
            context.sendResponseToPlayer(getPlayerId(),
                    new TextMessage("[SERVER] No Insight Tokens available. Deduction count increased.", false,
                            "game.deduce.noTokens", null));

            // Increment session stats only if NO token was spent
            context.incrementSessionDeduceCount();

            if (!detective.incrementDeduceCount(suspect.getName())) {
                // See note above
            }
        }

        String clue = suspect.getClue();
        if (clue == null || clue.trim().isEmpty()) {
            clue = "You observe " + suspect.getName() + " carefully, but gain no new insights beyond their statement.";
        }

        // Send Dialogue Event (Local Only). Title shows the per-language Display Name; the journal
        // source id stays keyed on the Universal id.
        context.sendResponseToPlayer(getPlayerId(), new DialogueEventDTO(
                "Deduction: " + suspect.getDisplayName(),
                clue,
                DialogueType.DEDUCTION));

        JournalEntryDTO entry = new JournalEntryDTO(
                deductionId,
                JournalEntryType.DEDUCTION,
                suspect.getId(),
                "Deduction: " + suspect.getDisplayName(),
                clue,
                getPlayerId(),
                System.currentTimeMillis());
        context.addJournalEntry(entry);

        context.sendResponseToPlayer(getPlayerId(),
                new TextMessage("Team deductions used: " + context.getSessionDeduceCount(), false,
                        "game.deduce.teamCount", java.util.List.of(String.valueOf(context.getSessionDeduceCount()))));
    }

    @Override
    public String getDescription() {
        return "Makes a deduction about an object or suspect, revealing a clue. Affects rank. Usage: deduce [target_name]";
    }

}

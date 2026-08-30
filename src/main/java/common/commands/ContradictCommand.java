package common.commands;

import Core.Suspect;
import JsonDTO.CaseFile.ContradictionRule;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import common.dto.DialogueEventDTO;
import common.dto.DialogueType;
import common.dto.JournalEntryDTO;
import common.dto.JournalEntryType;
import common.dto.TextMessage;
import common.dto.pinboard.PinboardLinkDTO;
import common.dto.pinboard.PinboardUpdateDTO;
import common.interfaces.GameActionContext;
import java.io.Serial;
import java.util.List;
import java.util.Optional;

public class ContradictCommand extends BaseCommand {
  @Serial private static final long serialVersionUID = 1L;

  private final String suspectName; // Used for lookup
  private final String
      evidenceId; // This is the JournalEntry.sourceId (e.g. "obj:timecard" or just "timecard"

  // depending on journal)

  @JsonCreator
  public ContradictCommand(
      @JsonProperty("suspectName") String suspectName,
      @JsonProperty("evidenceId") String evidenceId) {
    super(true); // Is actionable
    if (suspectName == null || suspectName.trim().isEmpty()) {
      throw new IllegalArgumentException("Suspect name cannot be null or empty.");
    }
    if (evidenceId == null || evidenceId.trim().isEmpty()) {
      throw new IllegalArgumentException("Evidence ID cannot be null or empty.");
    }
    common.WireLimits.requireLength(suspectName, common.WireLimits.MAX_NAME_LENGTH, "suspectName");
    common.WireLimits.requireLength(evidenceId, common.WireLimits.MAX_ID_LENGTH, "evidenceId");
    this.suspectName = suspectName.trim();
    this.evidenceId = evidenceId.trim();
  }

  public String getSuspectName() {
    return suspectName;
  }

  public String getEvidenceId() {
    return evidenceId;
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    if (context.isContradictOnCooldown()) {
      long remaining = context.getContradictCooldownRemaining();
      context.sendResponseToPlayer(
          getPlayerId(),
          new TextMessage(
              "[SERVER] Contradict is on cooldown for " + remaining + " seconds.",
              true,
              "game.contradict.cooldownActive",
              java.util.List.of(String.valueOf(remaining))));
      // Also surface it as a popup so it is seen while the Pinboard covers the terminal.
      context.sendResponseToPlayer(
          getPlayerId(),
          new DialogueEventDTO(
              "Contradict on Cooldown",
              "Contradict is locked. Try again in " + remaining + " seconds.",
              DialogueType.CONTRADICTION,
              "game.popup.contradictCooldown",
              "game.contradict.cooldownActive",
              java.util.List.of(String.valueOf(remaining))));
      return;
    }

    // 1. Find Suspect
    Optional<Suspect> suspectOpt =
        context.getAllSuspects().stream()
            .filter(
                s ->
                    s.getName().equalsIgnoreCase(suspectName)
                        || s.getId().equalsIgnoreCase(suspectName))
            .findFirst();

    if (suspectOpt.isEmpty()) {
      context.sendResponseToPlayer(
          getPlayerId(),
          new TextMessage(
              "Suspect '" + suspectName + "' not found.",
              true,
              "game.suspect.notFound",
              java.util.List.of(suspectName)));
      return;
    }
    Suspect suspect = suspectOpt.get();

    // 1b. The suspect must be in the player's room (server-side adjacency check, matching
    // question/deduce semantics — security-pass issue 06). Absence is not a cooldown strike.
    Core.Room currentRoom = context.getCurrentRoomForPlayer(getPlayerId());
    if (currentRoom == null
        || suspect.getCurrentRoom() == null
        || !suspect.getCurrentRoom().getName().equalsIgnoreCase(currentRoom.getName())) {
      context.sendResponseToPlayer(
          getPlayerId(),
          new TextMessage(
              "Suspect '" + suspectName + "' is not in this room.",
              false,
              "game.suspect.notInRoom",
              java.util.List.of(suspectName)));
      // Also surface it as a popup — a player working from the Pinboard cannot see the terminal.
      context.sendResponseToPlayer(
          getPlayerId(),
          new DialogueEventDTO(
              "Suspect Not Here",
              "Suspect '" + suspectName + "' is not in this room.",
              DialogueType.CONTRADICTION,
              "game.popup.suspectNotHere",
              "game.suspect.notInRoom",
              java.util.List.of(suspectName)));
      return;
    }

    // 2. Validate Evidence and Resolve Source ID
    String resolvedEvidenceSourceId = evidenceId;
    List<JournalEntryDTO> journal = context.getJournalEntries(getPlayerId());

    Optional<JournalEntryDTO> matchingEntry =
        journal.stream()
            .filter(
                e ->
                    e.getId().equalsIgnoreCase(evidenceId)
                        || e.getSourceId().equalsIgnoreCase(evidenceId))
            .findFirst();

    if (matchingEntry.isPresent()) {
      if (matchingEntry.get().getSourceId() != null) {
        resolvedEvidenceSourceId = matchingEntry.get().getSourceId();
      }
    }

    ContradictionRule rule = suspect.checkContradiction(resolvedEvidenceSourceId);

    if (rule != null) {
      // MATCH!
      context.reportContradictSuccess();

      // 3. Transition State
      suspect.transitionState(rule.getNextState());

      // 4. Broadcast Dialogue Event
      String reaction = rule.getSuccessMessage();
      if (reaction == null || reaction.isEmpty()) {
        reaction = "You have exposed a contradiction!";
      }
      String newStatement = suspect.getStatement();
      String fullText = reaction + "\n\n\"" + (newStatement != null ? newStatement : "...") + "\"";

      context.broadcastToSession(
          new DialogueEventDTO(
              "Contradiction: " + suspect.getName(), fullText, DialogueType.CONTRADICTION),
          null);

      // 4b. Add NEW Statement to Journal
      if (newStatement != null && !newStatement.isEmpty()) {
        String stmtId = "stmt:" + suspect.getId() + ":" + rule.getNextState();
        JournalEntryDTO stmtEntry =
            new JournalEntryDTO(
                stmtId,
                JournalEntryType.SUSPECT_STATEMENT,
                suspect.getId(),
                suspect.getName() + " Statement (" + rule.getNextState() + ")",
                newStatement,
                getPlayerId(),
                System.currentTimeMillis());
        context.addJournalEntry(stmtEntry);
      }

      // 5. Reward Deduction (Mandatory + Shared)
      String rewardId;
      String rewardTitle;
      String rewardText;
      String rewardSourceId; // Source ID for the entry

      if (rule.getRewardDeductionId() != null) {
        rewardSourceId = rule.getRewardDeductionId();
        rewardId = "ded:" + rewardSourceId;
        rewardTitle = "Contradiction Revealed";
        rewardText = "Contradiction exposed using " + evidenceId + ". New deduction unlocked.";
      } else {
        // Deterministic ID for generic reward
        rewardSourceId =
            "GENERIC_CONTRADICTION_"
                + suspect.getName().toUpperCase()
                + "_"
                + resolvedEvidenceSourceId.toUpperCase();
        rewardId =
            "CONTRADICTION::"
                + suspect.getName().toUpperCase()
                + "::"
                + resolvedEvidenceSourceId.toUpperCase();
        rewardTitle = "Contradiction: " + suspect.getName();
        rewardText =
            "You confronted "
                + suspect.getName()
                + " with "
                + evidenceId
                + ". They revised their story.";
      }

      // Check if already awarded (Duplicate check)
      // Using ID check on the Journal
      common.dto.JournalEntryDTO existingReward =
          context.getJournalEntryById(getPlayerId(), rewardId);

      if (existingReward == null) {
        // A) Award Shared Insight Token
        context.awardInsightToken();
        context.sendResponseToPlayer(
            getPlayerId(),
            new TextMessage(
                "[SERVER] Contradiction successful! +1 Insight Token.",
                false,
                "game.contradict.success",
                null));

        // B) Add Journal Entry
        JournalEntryDTO dedEntry =
            new JournalEntryDTO(
                rewardId,
                JournalEntryType.DEDUCTION,
                rewardSourceId,
                rewardTitle,
                rewardText,
                getPlayerId(),
                System.currentTimeMillis());
        context.addJournalEntry(dedEntry);
      } else {
        // Optional: Notify duplicate or do nothing
        // context.sendResponseToPlayer(getPlayerId(), new TextMessage("You recall this
        // contradiction.", false));
      }

      // 6. Update Pinboard (Link)
      String suspectNodeId = "suspect:" + suspect.getName().toLowerCase().replace(" ", "_");
      String evidenceNodeId = null;
      for (JournalEntryDTO entry : journal) {
        if (entry.getSourceId().equalsIgnoreCase(evidenceId)
            || entry.getId().equalsIgnoreCase(evidenceId)) {
          evidenceNodeId = entry.getId();
          break;
        }
      }

      if (evidenceNodeId != null) {
        PinboardLinkDTO linkDTO = new PinboardLinkDTO(evidenceNodeId, suspectNodeId, "RED");
        PinboardUpdateDTO update = new PinboardUpdateDTO();
        update.setType(PinboardUpdateDTO.UpdateType.ADD_LINK);
        update.setLink(linkDTO);
        context.broadcastToSession(
            new common.commands.pinboard.UpdatePinboardCommand(update), null);
      }

    } else {
      context.reportContradictFailure();
      context.sendResponseToPlayer(
          getPlayerId(),
          new TextMessage(
              "No contradiction found with that evidence.",
              false,
              "game.contradict.none",
              null));
      // Also surface it as a popup so the player sees the result while the Pinboard (a window on
      // top of the terminal) is open — the failure otherwise only reaches the hidden terminal.
      context.sendResponseToPlayer(
          getPlayerId(),
          new DialogueEventDTO(
              "No Contradiction",
              "No contradiction found with that evidence.",
              DialogueType.CONTRADICTION,
              "game.popup.noContradiction",
              "game.contradict.none",
              null));
    }
  }

  @Override
  public String getDescription() {
    return "Present evidence to a suspect to find contradictions. Usage: contradict <evidence> with <suspect>";
  }
}

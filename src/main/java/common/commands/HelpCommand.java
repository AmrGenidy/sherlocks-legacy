package common.commands;

import common.dto.TextMessage;
import common.interfaces.GameActionContext;
import java.io.Serial;
import java.util.LinkedHashMap;
import java.util.Map;

public class HelpCommand extends BaseCommand {
  @Serial private static final long serialVersionUID = 1L;

  public HelpCommand() {
    super(false);
  }

  public static String getHelpText() {
    Map<String, String> commandsToShow = new LinkedHashMap<>();

    // Core exploration
    commandsToShow.put("look", "Show the current room description, objects, occupants, and exits.");
    commandsToShow.put(
        "move [north|south|east|west|up|down]", "Move to a neighboring room in that direction.");
    commandsToShow.put(
        "examine [object]",
        "Inspect an object to learn details and add its note to the journal (shared in MP).");

    // Interactions
    commandsToShow.put(
        "question [suspect]",
        "Question a suspect to hear their statement and add their note to the journal (shared in MP).");

    // Reasoning mechanics
    commandsToShow.put(
        "deduce [object|suspect]",
        "Spend 1 Insight Token to create a deduction note. If tokens are 0, increases Team Deductions Used instead.");
    commandsToShow.put(
        "contradict [evidence] with [suspect]",
        "Present a discovered note (evidence) to challenge a suspect's statement. May change suspect state and reward tokens/deductions. Alias: present [evidence] to [suspect].");
    commandsToShow.put(
        "combine [noteA_id] [noteB_id]",
        "Combine two discovered notes to unlock a new deduction (and possibly reward tokens). Only works if a combine rule exists.");

    // Information panels
    commandsToShow.put("journal", "Open the journal/pinboard (shared in MP).");
    commandsToShow.put("journal [word]", "To search for a specific word in the journal.");
    commandsToShow.put(
        "journal add [note]", "Manually add a custom note to the journal/pinboard (shared in MP).");

    // Case progress + hints
    commandsToShow.put("tasks", "View current case tasks/leads (shared in MP).");
    commandsToShow.put("ask watson", "Get a free general hint from Watson.");
    commandsToShow.put(
        "ask watson [object|suspect]",
        "Ask Watson about a specific object/suspect (costs Insight Tokens if configured).");

    // Endgame
    commandsToShow.put("final exam", "Start the final exam (if available).");

    // System
    commandsToShow.put("exit", "Exit the current case (MP) or quit the game (SP).");
    commandsToShow.put("help", "Display this help message.");

    StringBuilder helpMessage = new StringBuilder("Available commands:\n");
    for (Map.Entry<String, String> entry : commandsToShow.entrySet()) {
      helpMessage.append(String.format("  %-32s - %s\n", entry.getKey(), entry.getValue()));
    }
    return helpMessage.toString().trim();
  }

  @Override
  protected boolean allowedDuringReview() {
    return true; // help is always available, including while reviewing
  }

  @Override
  protected void executeCommandLogic(GameActionContext context) {
    context.sendResponseToPlayer(getPlayerId(), new TextMessage(getHelpText(), false));
  }

  @Override
  public String getDescription() {
    return "Displays a list of available commands and their descriptions.";
  }
}

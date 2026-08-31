package singleplayer.util;

import common.commands.*;

// No multiplayer DTOs needed for a single-player factory.

/**
 * CommandFactorySinglePlayer Creates Command objects for the Single Player mode based on parsed
 * user input.
 */
public class CommandFactorySinglePlayer {

  private static final org.slf4j.Logger logger =
      org.slf4j.LoggerFactory.getLogger(CommandFactorySinglePlayer.class);

  // Utility class - no instances.
  private CommandFactorySinglePlayer() {}

  /**
   * Creates a Command based on parsed input.
   *
   * @param parsedInput String array: [0] is command name, [1] (optional) is argument.
   * @return A Command object, or null if input is invalid or command unknown.
   */
  public static Command createCommand(String[] parsedInput) {
    if (parsedInput == null || parsedInput.length == 0) {
      return null;
    }

    String commandName = parsedInput[0].toLowerCase();
    String arg = (parsedInput.length > 1) ? parsedInput[1] : null;

    return switch (commandName) {
      case "look" -> new LookCommand();
      case "move" -> {
        if (arg != null && !arg.isEmpty()) yield new MoveCommand(arg);
        else logger.warn("Usage: move <direction>");
        yield null;
      }
      case "examine" -> {
        if (arg != null && !arg.isEmpty()) yield new ExamineCommand(arg);
        else logger.warn("Usage: examine <object_name>");
        yield null;
      }
      case "question" -> {
        if (arg != null && !arg.isEmpty()) yield new QuestionCommand(arg);
        else logger.warn("Usage: question <suspect_name>");
        yield null;
      }
      case "journal" -> new JournalCommand(arg); // Null arg means view all.
      case "journal add" -> {
        if (arg != null && !arg.isEmpty()) yield new JournalAddCommand(arg);
        else logger.warn("Usage: journal add <note_text>");
        yield null;
      }
      case "deduce" -> {
        if (arg != null && !arg.isEmpty()) yield new DeduceCommand(arg);
        else logger.warn("Usage: deduce <object_name>");
        yield null;
      }
      case "ask watson" -> new AskWatsonCommand(arg);
      case "final exam" -> // User types "final exam"
          new InitiateFinalExamCommand(); // SP context will handle this.
      case "submit answer" -> {
        // This is handled directly by the SP game loop when answering questions.
        logger.warn("Error: 'submit answer' is used automatically during the exam Q&A.");
        yield null;
      }
      case "tasks" -> new TaskCommand();
      // Contradiction verb (.scratch/gui-contradict-syntax): canonical
      // "contradict <evidence> with <suspect>". The legacy "present"/"contradict … to …" still
      // parses for back-compat — both map onto the same ContradictCommand.
      case "contradict", "present" -> {
        if (arg != null && !arg.isEmpty()) {
          // Canonical separator "with" wins; " to " is the back-compat fallback, so it only
          // splits when no " with " is present (and an evidence id containing "to" stays intact).
          String[] parts = arg.split(" (?i)with ", 2);
          if (parts.length < 2) {
            parts = arg.split(" (?i)to ", 2);
          }
          if (parts.length >= 2) {
            yield new ContradictCommand(parts[1].trim(), parts[0].trim());
          }
        }
        logger.warn(
            "Usage: contradict <evidence> with <suspect>  (alias: present <evidence> to <suspect>)");
        yield null;
      }
      case "combine" -> {
        if (arg != null && !arg.isEmpty()) {
          String[] parts = arg.split("\\s+");
          if (parts.length >= 2) {
            yield new CombineCommand(parts[0].trim(), parts[1].trim());
          }
        }
        logger.warn("Usage: combine <note_id_1> <note_id_2>");
        yield null;
      }
      case "help" -> new HelpCommand();
      case "start case" -> new StartCaseCommand();
      case "exit" -> new ExitCommand();
      case "add case" -> {
        // 'add case' requires specific file system interaction.
        // Handled by SinglePlayerMain using CaseFileUtil or a dedicated SP command.
        // This factory won't create a common.commands.AddCaseCommand for SP
        // unless that command is designed to work with a local SP context.
        if (arg != null && !arg.isEmpty()) {
          logger.warn("Note: 'add case' uses utility. For manual typing, ensure path is correct.");
          // Example: return new AddCaseSPCommand(arg); // If you had a specific SP
          // version
          // Or, SinglePlayerMain calls CaseFileUtil.addCaseFromFile(arg) directly.
          // For now, returning null as SPMain handles "add case" input directly.
        } else {
          logger.warn("Usage: add case <file_path>");
        }
        yield null;
      }
      default ->
          // Unknown command. SinglePlayerMain's loop will inform the user.
          null;
    };
  }
}

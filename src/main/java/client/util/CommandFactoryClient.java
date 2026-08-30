package client.util;

import client.ClientState;
import common.commands.*;
import common.dto.JoinPrivateGameRequestDTO;
import common.dto.JoinPublicGameRequestDTO;
import common.dto.UpdateDisplayNameRequestDTO;

/** Creates a Command DTO based on parsed user input and the client's role. */
public class CommandFactoryClient {

  private static final org.slf4j.Logger logger =
      org.slf4j.LoggerFactory.getLogger(CommandFactoryClient.class);

  private CommandFactoryClient() {}

  public static Command createCommand(
      CommandParserClient.ParsedCommandData parsedData,
      boolean isHost,
      ClientState currentClientState) {

    if (parsedData == null || parsedData.commandName == null || parsedData.commandName.isEmpty()) {
      return null;
    }

    String commandName = parsedData.commandName;
    String arg = parsedData.getFirstArgument();

    switch (commandName) {
      case "list public games":
        return new ListPublicGamesCommand();

      case "join public game":
        if (arg == null || arg.isEmpty()) {
          logger.warn("Usage: join public game <session_id_or_number>");
          return null;
        }
        return new JoinPublicGameCommand(new JoinPublicGameRequestDTO(arg));

      case "join private game":
        if (arg == null || arg.isEmpty()) {
          logger.warn("Usage: join private game <game_code>");
          return null;
        }
        return new JoinPrivateGameCommand(new JoinPrivateGameRequestDTO(arg.toUpperCase()));

      case "start case":
        if (currentClientState == ClientState.IN_LOBBY_AWAITING_START
            || currentClientState == ClientState.SHOWING_INVITATION) {
          return isHost ? new StartCaseCommand() : new RequestStartCaseCommand();
        } else {
          logger.warn(
              "CLIENT_FACTORY_HINT: 'start case' can only be used when the lobby is full and awaiting the game to start.");
          return null;
        }

      case "initiate final exam":
      case "final exam":
        if (currentClientState == ClientState.IN_GAME) {
          return isHost ? new InitiateFinalExamCommand() : new RequestInitiateExamCommand();
        } else {
          logger.warn(
              "CLIENT_FACTORY_HINT: 'final exam' can only be used when a game is actively in progress.");
          return null;
        }

      case "request start case":
        if (currentClientState == ClientState.IN_LOBBY_AWAITING_START && !isHost) {
          return new RequestStartCaseCommand();
        } else if (isHost) {
          logger.warn("Host should use 'start case'. This command is for guests.");
        } else {
          logger.warn("Not in correct state for 'request start case'.");
        }
        return null;

      case "request final exam":
        if (currentClientState == ClientState.IN_GAME && !isHost) {
          return new RequestInitiateExamCommand();
        } else if (isHost) {
          logger.warn("Host should use 'final exam'. This command is for guests.");
          return new InitiateFinalExamCommand();
        } else {
          logger.warn("Not in correct state for 'request final exam'.");
        }
        return null;

      case "look":
        return new LookCommand();

      case "move":
        if (arg == null || arg.isEmpty()) {
          logger.warn("Usage: move <direction>");
          return null;
        }
        return new MoveCommand(arg);

      case "examine":
        if (arg == null || arg.isEmpty()) {
          logger.warn("Usage: examine <object_name>");
          return null;
        }
        return new ExamineCommand(arg);

      case "question":
        if (arg == null || arg.isEmpty()) {
          logger.warn("Usage: question <suspect_name>");
          return null;
        }
        return new QuestionCommand(arg);

      case "journal":
        return new JournalCommand(arg);

      case "journal add":
        if (arg == null || arg.isEmpty()) {
          logger.warn("Usage: journal add <note_text>");
          return null;
        }
        return new JournalAddCommand(arg);

      case "deduce":
        if (arg == null || arg.isEmpty()) {
          logger.warn("Usage: deduce <object_name>");
          return null;
        }
        return new DeduceCommand(arg);

      case "ask watson":
        return new AskWatsonCommand(arg);

      case "tasks":
        return new TaskCommand();

      case "submit exam answer":
        if (arg == null) {
          logger.warn("Usage: submit answer <q_num> <answer>");
          return null;
        }
        String[] parts = arg.split("\\s+", 2);
        if (parts.length < 2) {
          logger.warn("Usage: submit answer <q_num> <answer>");
          return null;
        }
        try {
          int qNum = Integer.parseInt(parts[0]);
          return new SubmitExamAnswerCommand(qNum, parts[1]);
        } catch (NumberFormatException e) {
          logger.warn("Invalid question number for 'submit answer'.");
          return null;
        }

      case "/setname":
        if (arg == null || arg.isEmpty()) {
          logger.warn("Usage: /setname <new_display_name>");
          return null;
        }
        return new UpdateDisplayNameCommand(new UpdateDisplayNameRequestDTO(arg));

      // Contradiction verb (.scratch/gui-contradict-syntax): canonical
      // "contradict <evidence> with <suspect>". The legacy "present"/"contradict … to …" still
      // parses for back-compat — both map onto the same ContradictCommand.
      case "contradict":
      case "present":
        if (arg == null || arg.isEmpty()) {
          logger.warn(
              "Usage: contradict <evidence> with <suspect>  (alias: present <evidence> to <suspect>)");
          return null;
        }
        // Canonical separator "with" wins; " to " is the back-compat fallback, so it only splits
        // when no " with " is present (and an evidence id containing "to" stays intact).
        String[] presentParts = arg.split(" (?i)with ", 2);
        if (presentParts.length < 2) {
          presentParts = arg.split(" (?i)to ", 2);
        }
        if (presentParts.length < 2) {
          logger.warn(
              "Usage: contradict <evidence> with <suspect>  (alias: present <evidence> to <suspect>)");
          return null;
        }
        String evidenceId = presentParts[0].trim();
        String suspectName = presentParts[1].trim();
        return new ContradictCommand(suspectName, evidenceId);

      case "combine":
        if (arg == null || arg.isEmpty()) {
          logger.warn("Usage: combine <note_id_1> <note_id_2>");
          return null;
        }
        String[] combineArgs = arg.split("\\s+");
        if (combineArgs.length < 2) {
          logger.warn("Usage: combine <note_id_1> <note_id_2>");
          return null;
        }
        return new CombineCommand(combineArgs[0], combineArgs[1]);

      case "help":
        return new HelpCommand();

      case "exit":
        if (currentClientState == ClientState.IN_GAME) {
          return new ExitCommand();
        } else if (currentClientState == ClientState.HOSTING_LOBBY_WAITING
            || currentClientState == ClientState.IN_LOBBY_AWAITING_START) {
          logger.warn("CLIENT_FACTORY_HINT: To leave a lobby, please use the 'cancel' command.");
          return null;
        } else {
          return new ExitCommand();
        }

      default:
        return null;
    }
  }
}

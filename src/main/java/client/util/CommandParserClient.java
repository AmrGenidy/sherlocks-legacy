package client.util;

public class CommandParserClient {

  private CommandParserClient() {}

  /** Parses raw user input into a command name and arguments. */
  public static ParsedCommandData parse(String rawInput) {
    if (rawInput == null || rawInput.trim().isEmpty()) {
      return null;
    }

    String normalizedInput = rawInput.trim().toLowerCase();

    // Handle specific multi-word commands first.
    if (normalizedInput.startsWith("host game") || normalizedInput.startsWith("host case")) {
      return new ParsedCommandData("host game", extractArgs(normalizedInput, "host game"));
    } else if (normalizedInput.startsWith("list games")
            || normalizedInput.startsWith("list public games")) {
      return new ParsedCommandData(
              "list public games", extractArgs(normalizedInput, "list public games"));
    } else if (normalizedInput.startsWith("join public game")) {
      return new ParsedCommandData(
              "join public game", extractArgs(normalizedInput, "join public game"));
    } else if (normalizedInput.startsWith("join private game")
            || normalizedInput.startsWith("join game")) {
      return new ParsedCommandData(
              "join private game", extractArgs(normalizedInput, "join private game"));
    } else if (normalizedInput.equals("request start case")) {
      return new ParsedCommandData("request start case", new String[0]);
    } else if (normalizedInput.equals("request final exam")) {
      return new ParsedCommandData("request final exam", new String[0]);
    } else if (normalizedInput.startsWith("start case")) {
      return new ParsedCommandData("start case", extractArgs(normalizedInput, "start case"));
    } else if (normalizedInput.startsWith("initiate final exam")
            || normalizedInput.startsWith("final exam")) {
      return new ParsedCommandData(
              "initiate final exam", extractArgs(normalizedInput, "initiate final exam"));
    } else if (normalizedInput.startsWith("submit answer")
            || normalizedInput.startsWith("submit exam answer")) {
      return new ParsedCommandData(
              "submit exam answer", extractArgs(normalizedInput, "submit exam answer"));
    } else if (normalizedInput.startsWith("journal add")) {
      return new ParsedCommandData("journal add", extractArgs(normalizedInput, "journal add"));
    } else if (normalizedInput.startsWith("ask watson")) {
      return new ParsedCommandData("ask watson", extractArgs(normalizedInput, "ask watson"));
    } else if (normalizedInput.startsWith("add case")) {
      return new ParsedCommandData("add case", extractArgs(normalizedInput, "add case"));
    } else if (normalizedInput.startsWith("/setname ")) {
      return new ParsedCommandData("/setname", extractArgs(normalizedInput, "/setname"));
    }

    // Fallback: first word is the command, rest are arguments.
    String[] tokens = normalizedInput.split("\\s+", 2);
    String commandName = tokens[0];
    String[] args =
            (tokens.length > 1 && !tokens[1].isEmpty())
                    ? new String[] {tokens[1].trim()}
                    : new String[0];

    return new ParsedCommandData(commandName, args);
  }

  /** Extracts arguments after a given command prefix. */
  private static String[] extractArgs(String fullInput, String commandPrefix) {
    String effectivePrefix = commandPrefix.endsWith(" ") ? commandPrefix : commandPrefix + " ";

    if (fullInput.startsWith(effectivePrefix) && fullInput.length() > effectivePrefix.length()) {
      String argPart = fullInput.substring(effectivePrefix.length()).trim();
      if (!argPart.isEmpty()) {
        return new String[] {argPart};
      }
    } else if (fullInput.equals(commandPrefix.trim())) {
      return new String[0];
    }

    return new String[0];
  }

  /** Container for parsed command name and its arguments. */
  public static class ParsedCommandData {
    public final String commandName;
    public final String[] arguments;

    public ParsedCommandData(String commandName, String[] arguments) {
      this.commandName = commandName;
      this.arguments = arguments;
    }

    /** Gets the first (often only) argument. */
    public String getFirstArgument() {
      return (this.arguments != null && this.arguments.length > 0) ? this.arguments[0] : null;
    }
  }
}

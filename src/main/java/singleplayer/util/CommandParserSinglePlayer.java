package singleplayer.util;

// This parser focuses on identifying the command name and basic tokenization.
// Argument validation (e.g., ensuring 'move' has a direction) is often
// handled by the command factory or the command constructor itself.
public class CommandParserSinglePlayer {

  // Simpler version, if you want to handle multi-word args more generally in
  // factory
  public static String[] parseInputSimple(String input) {
    if (input == null || input.trim().isEmpty()) {
      return new String[0];
    }
    // Normalize: lowercase, trim, collapse multiple spaces
    String normalizedInput = input.trim().replaceAll("\\s+", " ").toLowerCase();

    // Handle specific multi-word commands first
    if (normalizedInput.startsWith("journal add ") || normalizedInput.equals("journal add")) {
      return new String[] {
        "journal add",
        normalizedInput.length() > "journal add".length()
            ? normalizedInput.substring("journal add ".length()).trim()
            : ""
      };
    }
    if (normalizedInput.startsWith("ask watson")) {
      String arg = normalizedInput.substring("ask watson".length()).trim();
      if (arg.isEmpty()) {
        return new String[] {"ask watson"};
      } else {
        return new String[] {"ask watson", arg};
      }
    }

    switch (normalizedInput) {
      case "start case" -> {
        return new String[] {"start case"};
      }
      // "initiate final exam" is the multiplayer phrasing — accepted here as an alias so
      // both modes take the same commands (navigation-ux-smoothness issue 01).
      case "final exam", "initiate final exam" -> {
        return new String[] {"final exam"};
      }
    }

    // For single-word commands or commands where the first word is the command
    // and the rest is a single argument string.
    return normalizedInput.split(" ", 2);
  }
}

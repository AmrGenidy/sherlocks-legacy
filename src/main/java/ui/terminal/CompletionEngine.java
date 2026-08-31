package ui.terminal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Turns the raw terminal input line plus a {@link CompletionContext} into ordered suggestions
 * (.scratch/terminal-autocomplete issue 01). Pure Java — no JavaFX.
 *
 * <p>Two positions, resolved deliberately for multi-word commands:
 *
 * <ul>
 *   <li><b>Command position</b> — the line is not yet a complete command followed by whitespace.
 *       The whole line is matched against full command phrases (so {@code "ask w"} completes to
 *       {@code "ask watson"} — word-by-word typing and whole-phrase completion both work) and
 *       against the screen's bare menu options. Accepting a command that expects an argument
 *       appends a trailing space so the argument domain opens immediately.
 *   <li><b>Argument position</b> — the line starts with a complete command. Everything typed after
 *       the command is matched against that command's domain (so multi-word names like {@code
 *       "writing desk"} complete from {@code "wr"} or {@code "writing d"}). Longer command phrases
 *       that extend the typed text (e.g. {@code "journal"} → {@code "journal add"}) are still
 *       offered first, which resolves the multi-word command prefix ambiguity.
 * </ul>
 *
 * <p>Accepting a suggestion always yields the full replacement line.
 */
public final class CompletionEngine {

  /** One suggestion: the chip text and the full input line produced by accepting it. */
  public static final class Suggestion {
    private final String label;
    private final String replacement;

    Suggestion(String label, String replacement) {
      this.label = label;
      this.replacement = replacement;
    }

    public String label() {
      return label;
    }

    public String replacement() {
      return replacement;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Suggestion)) {
        return false;
      }
      Suggestion that = (Suggestion) o;
      return label.equals(that.label) && replacement.equals(that.replacement);
    }

    @Override
    public int hashCode() {
      return Objects.hash(label, replacement);
    }

    @Override
    public String toString() {
      return label + " -> '" + replacement + "'";
    }
  }

  private CompletionEngine() {}

  /** Ordered suggestions for {@code line}; empty when the line is blank or nothing matches. */
  public static List<Suggestion> suggest(String line, CompletionContext context) {
    if (line == null || line.isBlank() || context == null) {
      return List.of();
    }
    String typed = line.stripLeading();
    String typedLower = typed.toLowerCase(Locale.ROOT);

    // Deduplicate by replacement, preserving order.
    Map<String, Suggestion> results = new LinkedHashMap<>();

    CompletionContext.CommandSpec command = findCompletedCommand(typedLower, context);
    if (command == null) {
      suggestCommandPosition(typed, context, results);
    } else {
      suggestCommandExtensions(typedLower, context, results);
      suggestArguments(typed, typedLower, command, results);
    }
    return List.copyOf(results.values());
  }

  /** The longest command phrase the line already contains completely (followed by a boundary). */
  private static CompletionContext.CommandSpec findCompletedCommand(
      String typedLower, CompletionContext context) {
    CompletionContext.CommandSpec best = null;
    for (CompletionContext.CommandSpec spec : context.commandSpecs().values()) {
      if (typedLower.equals(spec.phrase) || typedLower.startsWith(spec.phrase + " ")) {
        if (best == null || spec.phrase.length() > best.phrase.length()) {
          best = spec;
        }
      }
    }
    return best;
  }

  /** Command position: rank command phrases and bare menu options against the whole line. */
  private static void suggestCommandPosition(
      String typed, CompletionContext context, Map<String, Suggestion> results) {
    Map<String, CompletionContext.CommandSpec> specsByPhrase = context.commandSpecs();
    Map<String, CompletionContext.BareOption> optionsByValue = new LinkedHashMap<>();
    List<String> candidates = new ArrayList<>(specsByPhrase.keySet());
    for (CompletionContext.BareOption option : context.bareOptions()) {
      String key = option.value.toLowerCase(Locale.ROOT);
      if (optionsByValue.putIfAbsent(key, option) == null) {
        candidates.add(option.value);
      }
    }
    // One suggestion per command: an alias and its canonical phrase both matching ("final exam"
    // and "initiate final exam") would otherwise crowd the strip with the same action.
    java.util.Set<String> suggestedCommands = new java.util.HashSet<>();
    for (String match : CompletionMatcher.match(typed, candidates)) {
      String key = match.toLowerCase(Locale.ROOT);
      CompletionContext.CommandSpec spec = specsByPhrase.get(key);
      if (spec != null) {
        if (suggestedCommands.add(spec.canonical)) {
          addSuggestion(results, spec.phrase, commandReplacement(spec));
        }
      } else {
        CompletionContext.BareOption option = optionsByValue.get(key);
        if (option != null) {
          addSuggestion(results, option.label, option.value);
        }
      }
    }
  }

  /**
   * Longer command phrases extending the typed text ({@code "journal"} → {@code "journal add"}) —
   * offered even when a shorter command is already complete.
   */
  private static void suggestCommandExtensions(
      String typedLower, CompletionContext context, Map<String, Suggestion> results) {
    List<CompletionContext.CommandSpec> extensions = new ArrayList<>();
    String typedTrimmed = typedLower.trim();
    for (CompletionContext.CommandSpec spec : context.commandSpecs().values()) {
      if (spec.phrase.startsWith(typedLower) && !spec.phrase.equals(typedTrimmed)) {
        extensions.add(spec);
      }
    }
    extensions.sort((a, b) -> a.phrase.compareTo(b.phrase));
    for (CompletionContext.CommandSpec spec : extensions) {
      addSuggestion(results, spec.phrase, commandReplacement(spec));
    }
  }

  /** Argument position: complete what was typed after the command from its domain. */
  private static void suggestArguments(
      String typed,
      String typedLower,
      CompletionContext.CommandSpec command,
      Map<String, Suggestion> results) {
    // The command segment as the user typed it (preserves their casing/alias choice).
    String commandSegment = typed.substring(0, command.phrase.length());
    String argText = typed.substring(command.phrase.length()).stripLeading();
    boolean trailingSpace =
        !typed.isEmpty() && Character.isWhitespace(typed.charAt(typed.length() - 1));

    switch (command.argKind) {
      case DOMAIN:
        for (String candidate : CompletionMatcher.match(argText, command.domain)) {
          addSuggestion(results, candidate, commandSegment + " " + candidate);
        }
        break;
      case COMPOUND:
        suggestCompoundArguments(commandSegment, argText, trailingSpace, command, results);
        break;
      case NONE:
      case FREE_TEXT:
      default:
        break;
    }
  }

  /**
   * Two-slot completion: {@code contradict <evidence> with <suspect>}, {@code combine <id1> <id2>}.
   */
  private static void suggestCompoundArguments(
      String commandSegment,
      String argText,
      boolean trailingSpace,
      CompletionContext.CommandSpec command,
      Map<String, Suggestion> results) {
    CompletionContext.CompoundArgs compound = command.compound;
    String argLower = argText.toLowerCase(Locale.ROOT);

    if (compound.separator != null) {
      String separatorLower = compound.separator.toLowerCase(Locale.ROOT);
      String separatorToken = " " + separatorLower + " ";
      int separatorIndex = argLower.lastIndexOf(separatorToken);
      if (separatorIndex >= 0) {
        // Slot 2: everything after the separator is the suspect query.
        String first = argText.substring(0, separatorIndex).trim();
        String slot2Query = argText.substring(separatorIndex + separatorToken.length());
        addSlot2WithSeparator(commandSegment, first, compound, slot2Query, results);
      } else if (argLower.stripTrailing().endsWith(" " + separatorLower)) {
        // Separator just typed ("contradict clue:x with") — open slot 2.
        String stripped = argText.stripTrailing();
        String first = stripped.substring(0, stripped.length() - separatorLower.length()).trim();
        addSlot2WithSeparator(commandSegment, first, compound, "", results);
      } else if (trailingSpace && !argText.isBlank()) {
        // Slot 1 finished ("contradict clue:x ") — auto-insert the separator.
        addSlot2WithSeparator(commandSegment, argText.trim(), compound, "", results);
      } else {
        // Slot 1: accepting appends the separator so slot 2 opens immediately.
        for (String candidate : CompletionMatcher.match(argText, compound.firstDomain)) {
          addSuggestion(
              results,
              candidate,
              commandSegment + " " + candidate + " " + compound.separator + " ");
        }
      }
      return;
    }

    // Whitespace-separated slots (combine <id1> <id2>).
    int whitespaceIndex = indexOfWhitespace(argText);
    if (whitespaceIndex >= 0) {
      String first = argText.substring(0, whitespaceIndex);
      String slot2Query = argText.substring(whitespaceIndex).stripLeading();
      for (String candidate : CompletionMatcher.match(slot2Query, compound.secondDomain)) {
        if (candidate.equalsIgnoreCase(first)) {
          continue; // combining a note with itself is never valid
        }
        addSuggestion(results, candidate, commandSegment + " " + first + " " + candidate);
      }
    } else {
      for (String candidate : CompletionMatcher.match(argText, compound.firstDomain)) {
        addSuggestion(results, candidate, commandSegment + " " + candidate + " ");
      }
    }
  }

  private static void addSlot2WithSeparator(
      String commandSegment,
      String first,
      CompletionContext.CompoundArgs compound,
      String slot2Query,
      Map<String, Suggestion> results) {
    for (String candidate : CompletionMatcher.match(slot2Query, compound.secondDomain)) {
      addSuggestion(
          results,
          candidate,
          commandSegment + " " + first + " " + compound.separator + " " + candidate);
    }
  }

  private static String commandReplacement(CompletionContext.CommandSpec spec) {
    return spec.expectsArgument() ? spec.phrase + " " : spec.phrase;
  }

  private static void addSuggestion(
      Map<String, Suggestion> results, String label, String replacement) {
    results.putIfAbsent(replacement, new Suggestion(label, replacement));
  }

  private static int indexOfWhitespace(String text) {
    for (int i = 0; i < text.length(); i++) {
      if (Character.isWhitespace(text.charAt(i))) {
        return i;
      }
    }
    return -1;
  }
}

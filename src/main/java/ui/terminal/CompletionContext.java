package ui.terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What is completable right now (.scratch/terminal-autocomplete issue 01): the provider seam each
 * {@code ui.shell.ScreenController} implements. Immutable; pure Java — no JavaFX.
 *
 * <p>A context holds:
 *
 * <ul>
 *   <li><b>Commands</b> — canonical command phrases plus aliases (multi-word phrases like {@code
 *       "ask watson"} are single entries), each with an optional argument domain: a flat candidate
 *       list, a free-text marker (argument expected but not completable, e.g. {@code journal add}),
 *       or a two-slot compound (e.g. {@code contradict <evidence> with <suspect>}).
 *   <li><b>Bare options</b> — inputs valid at the command position that are not commands, i.e. the
 *       numeric menu choices ("1", "00") on the menu/lobby screens, optionally labeled.
 * </ul>
 */
public final class CompletionContext {

  /** How a command's argument position completes. */
  enum ArgKind {
    /** Command takes no argument; accepting it completes the whole line. */
    NONE,
    /** Command takes a free-text argument; accepting appends a trailing space, no candidates. */
    FREE_TEXT,
    /** Command takes one argument completed from {@link CommandSpec#domain}. */
    DOMAIN,
    /** Command takes two argument slots, see {@link CompoundArgs}. */
    COMPOUND
  }

  /**
   * Two-slot argument spec: {@code <first> [separator] <second>}. A null separator means the slots
   * are split on whitespace ({@code combine <id1> <id2>}); a non-null separator is a keyword
   * between them ({@code contradict <evidence> with <suspect>}).
   */
  static final class CompoundArgs {
    final List<String> firstDomain;
    final String separator;
    final List<String> secondDomain;

    CompoundArgs(List<String> firstDomain, String separator, List<String> secondDomain) {
      this.firstDomain = List.copyOf(firstDomain);
      this.separator = separator;
      this.secondDomain = List.copyOf(secondDomain);
    }
  }

  /** One completable command phrase (canonical or alias) and its argument behaviour. */
  static final class CommandSpec {
    final String phrase;

    /** The canonical phrase this entry belongs to — equals {@link #phrase} for non-aliases. */
    final String canonical;

    final ArgKind argKind;
    final List<String> domain;
    final CompoundArgs compound;

    CommandSpec(
        String phrase,
        String canonical,
        ArgKind argKind,
        List<String> domain,
        CompoundArgs compound) {
      this.phrase = phrase;
      this.canonical = canonical;
      this.argKind = argKind;
      this.domain = domain == null ? List.of() : List.copyOf(domain);
      this.compound = compound;
    }

    boolean expectsArgument() {
      return argKind != ArgKind.NONE;
    }
  }

  /** A non-command input valid at the command position (numeric menu choice). */
  static final class BareOption {
    final String value;
    final String label;

    BareOption(String value, String label) {
      this.value = value;
      this.label = label == null || label.isBlank() ? value : label;
    }
  }

  private static final CompletionContext EMPTY = builder().build();

  /** Lowercased phrase -> spec; insertion-ordered, aliases included as their own entries. */
  private final Map<String, CommandSpec> commands;

  private final List<BareOption> bareOptions;

  private CompletionContext(Map<String, CommandSpec> commands, List<BareOption> bareOptions) {
    this.commands = Collections.unmodifiableMap(new LinkedHashMap<>(commands));
    this.bareOptions = List.copyOf(bareOptions);
  }

  /** A context with nothing completable — the {@code ScreenController} default. */
  public static CompletionContext empty() {
    return EMPTY;
  }

  public static Builder builder() {
    return new Builder();
  }

  Map<String, CompletionContext.CommandSpec> commandSpecs() {
    return commands;
  }

  List<BareOption> bareOptions() {
    return bareOptions;
  }

  /** Builder; phrases are stored lowercase, candidate casing is preserved. */
  public static final class Builder {
    private final Map<String, CommandSpec> commands = new LinkedHashMap<>();
    private final List<BareOption> bareOptions = new ArrayList<>();

    private Builder() {}

    /** A command taking no argument (e.g. {@code look}, {@code final exam}). */
    public Builder command(String canonical, String... aliases) {
      return add(canonical, ArgKind.NONE, null, null, aliases);
    }

    /** A command whose argument completes from {@code domain} (e.g. {@code move} from exits). */
    public Builder commandWithArgs(String canonical, List<String> domain, String... aliases) {
      return add(canonical, ArgKind.DOMAIN, domain, null, aliases);
    }

    /** A command taking a free-text argument (e.g. {@code journal add <note>}). */
    public Builder commandWithFreeArgs(String canonical, String... aliases) {
      return add(canonical, ArgKind.FREE_TEXT, null, null, aliases);
    }

    /**
     * A command with two argument slots, e.g. {@code contradict <evidence> with <suspect>}
     * (separator {@code "to"}) or {@code combine <id1> <id2>} (null separator = whitespace split).
     */
    public Builder commandWithCompoundArgs(
        String canonical,
        List<String> firstDomain,
        String separator,
        List<String> secondDomain,
        String... aliases) {
      return add(
          canonical,
          ArgKind.COMPOUND,
          null,
          new CompoundArgs(firstDomain, separator, secondDomain),
          aliases);
    }

    /** A bare menu choice ("1", "00"); {@code label} is shown on the chip, value is inserted. */
    public Builder bareOption(String value, String label) {
      if (value != null && !value.isBlank()) {
        bareOptions.add(new BareOption(value.trim(), label));
      }
      return this;
    }

    private Builder add(
        String canonical,
        ArgKind kind,
        List<String> domain,
        CompoundArgs compound,
        String... aliases) {
      if (canonical == null || canonical.isBlank()) {
        return this;
      }
      String canonicalKey = canonical.trim().toLowerCase(Locale.ROOT);
      put(canonical, canonicalKey, kind, domain, compound);
      if (aliases != null) {
        for (String alias : aliases) {
          put(alias, canonicalKey, kind, domain, compound);
        }
      }
      return this;
    }

    private void put(
        String phrase, String canonical, ArgKind kind, List<String> domain, CompoundArgs compound) {
      if (phrase == null || phrase.isBlank()) {
        return;
      }
      String normalized = phrase.trim().toLowerCase(Locale.ROOT);
      commands.put(normalized, new CommandSpec(normalized, canonical, kind, domain, compound));
    }

    public CompletionContext build() {
      return new CompletionContext(commands, bareOptions);
    }
  }
}

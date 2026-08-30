package ui.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.Test;
import ui.terminal.CompletionEngine.Suggestion;

/**
 * Engine contract (.scratch/terminal-autocomplete issue 01): command-position completion
 * (multi-word phrases, aliases, bare menu numbers), argument completion from per-command domains
 * (multi-word names from any prefix), and the two-slot syntaxes the parsers actually support
 * ({@code contradict <evidence> with <suspect>}, {@code combine <id1> <id2>}). Pure Java — no FX
 * toolkit. Domains mirror the bundled sapphire case.
 */
public class CompletionEngineTest {

  private static final List<String> EXITS = List.of("east", "west");
  private static final List<String> OBJECTS = List.of("Shattered Glass", "Writing Desk");
  private static final List<String> SUSPECTS = List.of("Lord Ashworth", "Mademoiselle Dupont");
  private static final List<String> JOURNAL_IDS =
      List.of("clue:cigar_stub", "ded:theory", "stmt:ashworth:LIE");

  /** The in-game vocabulary as GameScreenController builds it (multiplayer aliases included). */
  private static CompletionContext gameContext() {
    List<String> deduceTargets = new ArrayList<>(OBJECTS);
    deduceTargets.addAll(SUSPECTS);
    return CompletionContext.builder()
        .command("look")
        .commandWithArgs("move", EXITS)
        .commandWithArgs("examine", OBJECTS)
        .commandWithArgs("question", SUSPECTS)
        .commandWithArgs("deduce", deduceTargets)
        .command("journal")
        .commandWithFreeArgs("journal add")
        .commandWithArgs("ask watson", SUSPECTS)
        .commandWithCompoundArgs("contradict", JOURNAL_IDS, "with", SUSPECTS, "present")
        .commandWithCompoundArgs("combine", JOURNAL_IDS, null, JOURNAL_IDS)
        .command("tasks")
        .command("help")
        .command("exit")
        .command("final exam", "initiate final exam")
        .build();
  }

  private static CompletionContext menuContext() {
    return CompletionContext.builder()
        .bareOption("1", "1. Single Player")
        .bareOption("2", "2. Host Multiplayer")
        .bareOption("0", "0. Refresh")
        .bareOption("00", "00. Join by code")
        .bareOption("000", "000. Back")
        .build();
  }

  private static List<String> labels(List<Suggestion> suggestions) {
    return suggestions.stream().map(Suggestion::label).toList();
  }

  private static List<String> replacements(List<Suggestion> suggestions) {
    return suggestions.stream().map(Suggestion::replacement).toList();
  }

  // ====================== Empty / unknown input ======================

  @Test
  public void emptyInputYieldsNoSuggestions() {
    assertTrue(CompletionEngine.suggest("", gameContext()).isEmpty());
    assertTrue(CompletionEngine.suggest("   ", gameContext()).isEmpty());
    assertTrue(CompletionEngine.suggest(null, gameContext()).isEmpty());
  }

  @Test
  public void unknownCommandYieldsNoSuggestions() {
    assertTrue(CompletionEngine.suggest("zzz qqq", gameContext()).isEmpty());
  }

  @Test
  public void emptyContextYieldsNoSuggestions() {
    assertTrue(CompletionEngine.suggest("examine", CompletionContext.empty()).isEmpty());
  }

  // ====================== Command position ======================

  @Test
  public void commandPrefixBeatsFuzzyAtCommandPosition() {
    List<Suggestion> suggestions = CompletionEngine.suggest("exam", gameContext());
    // "examine" is a prefix match; "final exam" only a subsequence match.
    assertEquals(List.of("examine", "final exam"), labels(suggestions));
  }

  @Test
  public void acceptingCommandWithArgumentDomainAppendsTrailingSpace() {
    List<Suggestion> suggestions = CompletionEngine.suggest("mov", gameContext());
    assertEquals(List.of("move "), replacements(suggestions));
  }

  @Test
  public void acceptingNoArgCommandYieldsBarePhrase() {
    List<Suggestion> suggestions = CompletionEngine.suggest("loo", gameContext());
    assertEquals(List.of("look"), replacements(suggestions));
  }

  @Test
  public void multiWordCommandCompletesAsWholePhraseFromPartialSecondWord() {
    // The multi-word command prefix problem: "ask w" must complete to "ask watson".
    List<Suggestion> suggestions = CompletionEngine.suggest("ask w", gameContext());
    assertEquals(List.of("ask watson"), labels(suggestions));
    assertEquals(List.of("ask watson "), replacements(suggestions));
  }

  @Test
  public void multiWordCommandCompletesWordByWordPastACompleteShorterCommand() {
    // "journal a" already contains the complete command "journal"; the longer phrase
    // "journal add" must still be offered.
    List<Suggestion> suggestions = CompletionEngine.suggest("journal a", gameContext());
    assertEquals(List.of("journal add"), labels(suggestions));
    assertEquals(List.of("journal add "), replacements(suggestions));
  }

  @Test
  public void completeCommandStillOffersLongerPhrases() {
    List<Suggestion> suggestions = CompletionEngine.suggest("journal", gameContext());
    assertEquals(List.of("journal add"), labels(suggestions));
  }

  @Test
  public void aliasCompletesAtCommandPosition() {
    List<Suggestion> suggestions = CompletionEngine.suggest("initiate f", gameContext());
    assertEquals(List.of("initiate final exam"), labels(suggestions));
    assertEquals(List.of("initiate final exam"), replacements(suggestions));
  }

  // ====================== Argument position ======================

  @Test
  public void afterMoveSuggestionsAreExactlyTheExits() {
    List<Suggestion> suggestions = CompletionEngine.suggest("move ", gameContext());
    assertEquals(List.of("east", "west"), labels(suggestions));
    assertEquals(List.of("move east", "move west"), replacements(suggestions));
  }

  @Test
  public void moveArgumentFilters() {
    List<Suggestion> suggestions = CompletionEngine.suggest("move e", gameContext());
    assertEquals(List.of("move east"), replacements(suggestions));
  }

  @Test
  public void afterExamineSuggestionsAreExactlyTheRoomObjects() {
    List<Suggestion> suggestions = CompletionEngine.suggest("examine ", gameContext());
    assertEquals(List.of("Shattered Glass", "Writing Desk"), labels(suggestions));
  }

  @Test
  public void multiWordObjectCompletesFromShortPrefix() {
    List<Suggestion> suggestions = CompletionEngine.suggest("examine wr", gameContext());
    assertEquals(List.of("examine Writing Desk"), replacements(suggestions));
  }

  @Test
  public void multiWordObjectCompletesFromPartialSecondWord() {
    List<Suggestion> suggestions = CompletionEngine.suggest("examine writing d", gameContext());
    assertEquals(List.of("examine Writing Desk"), replacements(suggestions));
  }

  @Test
  public void afterQuestionSuggestionsAreExactlyTheSuspectsPresent() {
    List<Suggestion> suggestions = CompletionEngine.suggest("question ", gameContext());
    assertEquals(List.of("Lord Ashworth", "Mademoiselle Dupont"), labels(suggestions));
  }

  @Test
  public void multiWordSuspectCompletesFromAnyPrefixOfTheFullName() {
    List<Suggestion> suggestions = CompletionEngine.suggest("question lord a", gameContext());
    assertEquals(List.of("question Lord Ashworth"), replacements(suggestions));
  }

  @Test
  public void typedCommandCasingIsPreservedInReplacements() {
    List<Suggestion> suggestions = CompletionEngine.suggest("EXAMINE wr", gameContext());
    assertEquals(List.of("EXAMINE Writing Desk"), replacements(suggestions));
  }

  @Test
  public void noArgCommandOffersNothingAfterTrailingSpace() {
    assertTrue(CompletionEngine.suggest("look ", gameContext()).isEmpty());
  }

  @Test
  public void freeTextArgumentOffersNothing() {
    assertTrue(CompletionEngine.suggest("journal add my note", gameContext()).isEmpty());
  }

  // ====================== contradict <evidence> with <suspect> ======================

  @Test
  public void contradictFirstSlotCompletesEvidenceIdsAndAppendsSeparator() {
    List<Suggestion> suggestions = CompletionEngine.suggest("contradict clue", gameContext());
    assertEquals(List.of("contradict clue:cigar_stub with "), replacements(suggestions));
  }

  @Test
  public void contradictEmptyArgumentOffersAllEvidenceIds() {
    List<Suggestion> suggestions = CompletionEngine.suggest("contradict ", gameContext());
    assertEquals(
        List.of("clue:cigar_stub", "ded:theory", "stmt:ashworth:LIE"), labels(suggestions));
  }

  @Test
  public void contradictSecondSlotCompletesSuspectsAfterSeparator() {
    List<Suggestion> suggestions =
        CompletionEngine.suggest("contradict clue:cigar_stub with lor", gameContext());
    assertEquals(
        List.of("contradict clue:cigar_stub with Lord Ashworth"), replacements(suggestions));
  }

  @Test
  public void contradictOpensSecondSlotWhenSeparatorJustTyped() {
    List<Suggestion> suggestions =
        CompletionEngine.suggest("contradict clue:cigar_stub with", gameContext());
    assertEquals(List.of("Lord Ashworth", "Mademoiselle Dupont"), labels(suggestions));
  }

  @Test
  public void contradictAutoInsertsSeparatorOnceFirstSlotIsFinished() {
    List<Suggestion> suggestions =
        CompletionEngine.suggest("contradict clue:cigar_stub ", gameContext());
    assertEquals(
        List.of(
            "contradict clue:cigar_stub with Lord Ashworth",
            "contradict clue:cigar_stub with Mademoiselle Dupont"),
        replacements(suggestions));
  }

  // ====================== combine <id1> <id2> ======================

  @Test
  public void combineFirstSlotCompletesJournalIds() {
    List<Suggestion> suggestions = CompletionEngine.suggest("combine clue", gameContext());
    assertEquals(List.of("combine clue:cigar_stub "), replacements(suggestions));
  }

  @Test
  public void combineSecondSlotExcludesTheFirstNote() {
    List<Suggestion> suggestions =
        CompletionEngine.suggest("combine clue:cigar_stub ", gameContext());
    assertEquals(
        List.of("combine clue:cigar_stub ded:theory", "combine clue:cigar_stub stmt:ashworth:LIE"),
        replacements(suggestions));
  }

  @Test
  public void combineSecondSlotFilters() {
    List<Suggestion> suggestions =
        CompletionEngine.suggest("combine clue:cigar_stub ded", gameContext());
    assertEquals(List.of("combine clue:cigar_stub ded:theory"), replacements(suggestions));
  }

  // ====================== Bare menu options ======================

  @Test
  public void menuNumericOptionsCompleteWithLabels() {
    List<Suggestion> suggestions = CompletionEngine.suggest("0", menuContext());
    assertEquals(List.of("0", "00", "000"), replacements(suggestions));
    assertEquals(List.of("0. Refresh", "00. Join by code", "000. Back"), labels(suggestions));
  }

  @Test
  public void menuDigitCompletesToItsValue() {
    List<Suggestion> suggestions = CompletionEngine.suggest("1", menuContext());
    assertEquals(List.of("1"), replacements(suggestions));
    assertEquals(List.of("1. Single Player"), labels(suggestions));
  }

  // ====================== Reachability (PRD acceptance) ======================

  @Test
  public void anyDomainEntryIsReachableWithinThreeKeystrokesPlusTab() {
    // PRD: any object/suspect/direction reachable with <= 3 keystrokes + Tab. With the strip
    // showing six chips, the target must rank among the first six after typing at most the
    // first three characters of its name behind the command.
    assertReachable("move ", EXITS);
    assertReachable("examine ", OBJECTS);
    assertReachable("question ", SUSPECTS);
  }

  private static void assertReachable(String commandPrefix, List<String> domain) {
    for (String target : domain) {
      String typedPrefix =
          target.substring(0, Math.min(3, target.length())).toLowerCase(Locale.ROOT);
      List<Suggestion> suggestions =
          CompletionEngine.suggest(commandPrefix + typedPrefix, gameContext());
      List<String> topSix = labels(suggestions.subList(0, Math.min(6, suggestions.size())));
      assertTrue(
          "'" + target + "' not in top suggestions for '" + commandPrefix + typedPrefix + "'",
          topSix.contains(target));
    }
  }

  // ====================== Context immutability ======================

  @Test
  public void contextIsImmutableAgainstLaterDomainMutation() {
    List<String> domain = new ArrayList<>(List.of("north"));
    CompletionContext context = CompletionContext.builder().commandWithArgs("move", domain).build();
    domain.add("south");
    List<Suggestion> suggestions = CompletionEngine.suggest("move ", context);
    assertEquals(List.of("move north"), replacements(suggestions));
  }
}

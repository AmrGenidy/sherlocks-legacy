package ui.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/**
 * Matcher ranking contract (.scratch/terminal-autocomplete issue 01): case-insensitive prefix
 * matches first, subsequence (fuzzy) matches second, alphabetical within each rank, stable and
 * deduplicated. Pure Java — no FX toolkit.
 */
public class CompletionMatcherTest {

  @Test
  public void prefixMatchesRankAboveFuzzyMatches() {
    List<String> result =
        CompletionMatcher.match("ex", List.of("final exam", "examine", "exit", "look"));
    assertEquals(List.of("examine", "exit", "final exam"), result);
  }

  @Test
  public void matchingIsCaseInsensitive() {
    List<String> result =
        CompletionMatcher.match("lord a", List.of("Lord Ashworth", "Mademoiselle Dupont"));
    assertEquals(List.of("Lord Ashworth"), result);
  }

  @Test
  public void candidateCasingIsPreservedInResults() {
    List<String> result = CompletionMatcher.match("WR", List.of("Writing Desk"));
    assertEquals(List.of("Writing Desk"), result);
  }

  @Test
  public void alphabeticalOrderingWithinEachRank() {
    List<String> result =
        CompletionMatcher.match("s", List.of("south", "shattered glass", "north", "east"));
    // Both prefix matches, alphabetical.
    assertEquals(List.of("shattered glass", "south"), result);
  }

  @Test
  public void emptyQueryReturnsAllCandidatesSorted() {
    List<String> result = CompletionMatcher.match("", List.of("west", "east", "north"));
    assertEquals(List.of("east", "north", "west"), result);
  }

  @Test
  public void nullQueryBehavesLikeEmptyQuery() {
    List<String> result = CompletionMatcher.match(null, List.of("b", "a"));
    assertEquals(List.of("a", "b"), result);
  }

  @Test
  public void noMatchReturnsEmptyList() {
    assertTrue(CompletionMatcher.match("zzz", List.of("north", "south")).isEmpty());
  }

  @Test
  public void emptyCandidatesReturnsEmptyList() {
    assertTrue(CompletionMatcher.match("a", List.of()).isEmpty());
    assertTrue(CompletionMatcher.match("a", null).isEmpty());
  }

  @Test
  public void duplicatesAreDroppedCaseInsensitively() {
    List<String> result = CompletionMatcher.match("n", List.of("north", "North", "NORTH"));
    assertEquals(List.of("north"), result);
  }

  @Test
  public void subsequenceMatchCatchesScatteredCharacters() {
    // "wd" is not a prefix of "writing desk" but is a subsequence of it.
    List<String> result = CompletionMatcher.match("wd", List.of("writing desk", "window"));
    assertEquals(List.of("window", "writing desk"), result);
    // "window": w-i-n-d-o-w contains w then d — subsequence too; both fuzzy, alphabetical.
  }

  @Test
  public void nonSubsequenceDoesNotMatch() {
    assertFalse(CompletionMatcher.isSubsequence("dw", "writing"));
    assertTrue(CompletionMatcher.isSubsequence("wr", "writing"));
    assertTrue(CompletionMatcher.isSubsequence("wtg", "writing"));
  }

  @Test
  public void blankCandidatesAreSkipped() {
    List<String> result = CompletionMatcher.match("n", java.util.Arrays.asList("north", "", null));
    assertEquals(List.of("north"), result);
  }
}

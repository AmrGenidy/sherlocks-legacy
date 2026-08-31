package ui.terminal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Ranks completion candidates for the terminal autocomplete (.scratch/terminal-autocomplete issue
 * 01). Pure Java — no JavaFX — so it is unit-testable without the FX toolkit.
 *
 * <p>Ranking: case-insensitive prefix matches first — a match anchored at the start of the
 * candidate or at any word boundary inside it ({@code "ex"} matches {@code "final exam"}).
 * Subsequence ("fuzzy") matches are a FALLBACK: offered only when no prefix match exists, so a
 * precise prefix is never diluted by scattered-character hits. Ordering is stable: alphabetical
 * (case-insensitive) within the served rank. Duplicate candidates (case-insensitive) are dropped,
 * keeping the first occurrence.
 */
public final class CompletionMatcher {

  private CompletionMatcher() {}

  /**
   * Ranks {@code candidates} against {@code query}.
   *
   * @param query what the user typed so far for this token; a null/blank query matches every
   *     candidate (alphabetical), so an empty argument position offers the whole domain.
   * @return prefix matches (alphabetical); if none, subsequence matches (alphabetical).
   */
  public static List<String> match(String query, Collection<String> candidates) {
    if (candidates == null || candidates.isEmpty()) {
      return List.of();
    }
    String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

    List<String> prefixMatches = new ArrayList<>();
    List<String> fuzzyMatches = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (String candidate : candidates) {
      if (candidate == null || candidate.isBlank()) {
        continue;
      }
      String key = candidate.toLowerCase(Locale.ROOT);
      if (!seen.add(key)) {
        continue;
      }
      if (normalizedQuery.isEmpty() || isPrefixAtWordBoundary(normalizedQuery, key)) {
        prefixMatches.add(candidate);
      } else if (isSubsequence(normalizedQuery, key)) {
        fuzzyMatches.add(candidate);
      }
    }
    if (!prefixMatches.isEmpty()) {
      prefixMatches.sort(String.CASE_INSENSITIVE_ORDER);
      return prefixMatches;
    }
    fuzzyMatches.sort(String.CASE_INSENSITIVE_ORDER);
    return fuzzyMatches;
  }

  /** True if {@code haystack} starts with {@code needle} at index 0 or at any word start. */
  static boolean isPrefixAtWordBoundary(String needle, String haystack) {
    if (haystack.startsWith(needle)) {
      return true;
    }
    for (int i = 1; i < haystack.length(); i++) {
      if (haystack.charAt(i - 1) == ' ' && haystack.startsWith(needle, i)) {
        return true;
      }
    }
    return false;
  }

  /** True if every character of {@code needle} appears in {@code haystack} in order. */
  static boolean isSubsequence(String needle, String haystack) {
    int needleIndex = 0;
    for (int i = 0; i < haystack.length() && needleIndex < needle.length(); i++) {
      if (haystack.charAt(i) == needle.charAt(needleIndex)) {
        needleIndex++;
      }
    }
    return needleIndex == needle.length();
  }
}

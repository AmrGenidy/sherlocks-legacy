package ui.util;

/**
 * Pure (no-FX) helpers for turning machine ids into human-readable display names
 * (.scratch/casefile-tabs issue 02). Used as the fallback when a real display name is not available
 * from the loaded case data — so a tab or label never shows a raw id like {@code DrArisThorne}.
 */
public final class DisplayNames {

  private DisplayNames() {}

  /**
   * Humanizes an identifier into a spaced, title-cased name: splits {@code camelCase}/{@code
   * PascalCase} boundaries (including acronym runs) and {@code snake_case}/{@code kebab-case}
   * separators, collapses whitespace, and capitalizes each word's first letter (keeping any existing
   * inner capitals). E.g. {@code "DrArisThorne" -> "Dr Aris Thorne"}, {@code "lord_ashworth" ->
   * "Lord Ashworth"}, {@code "FBIAgent" -> "FBI Agent"}. Returns {@code ""} for null/blank input.
   *
   * <p>It cannot restore punctuation that was never in the id (so {@code "DrArisThorne"} becomes
   * "Dr Aris Thorne", not "Dr. Aris Thorne") — the authored display name from the case data is
   * preferred when it exists; this is the graceful fallback.
   */
  public static String humanizeId(String id) {
    if (id == null) {
      return "";
    }
    String s = id.trim();
    if (s.isEmpty()) {
      return "";
    }
    s = s.replace('_', ' ').replace('-', ' ');
    // lower/digit -> Upper boundary: "ArisThorne" -> "Aris Thorne"
    s = s.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
    // acronym run -> Upper+lower boundary: "FBIAgent" -> "FBI Agent"
    s = s.replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");
    s = s.replaceAll("\\s+", " ").trim();

    StringBuilder out = new StringBuilder(s.length());
    for (String word : s.split(" ")) {
      if (word.isEmpty()) {
        continue;
      }
      if (out.length() > 0) {
        out.append(' ');
      }
      out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
    }
    return out.toString();
  }
}

package ui.casemaker.model;

import java.util.Map;

/**
 * Canonical navigation directions and their opposites for the Case Maker room graph.
 *
 * <p>Mirrors the opposite-direction table used by {@code extractors.CaseValidator} so the editor's
 * bidirectional linking and the validator's reciprocity check agree on what "the reverse direction"
 * means. Kept as a small model-local helper to avoid the editor depending on validator internals; a
 * later refactor may consolidate the two into one source.
 */
public final class Directions {

  private static final Map<String, String> OPPOSITE =
      Map.ofEntries(
          Map.entry("north", "south"),
          Map.entry("south", "north"),
          Map.entry("east", "west"),
          Map.entry("west", "east"),
          Map.entry("up", "down"),
          Map.entry("down", "up"),
          Map.entry("northeast", "southwest"),
          Map.entry("southwest", "northeast"),
          Map.entry("northwest", "southeast"),
          Map.entry("southeast", "northwest"));

  private Directions() {}

  /**
   * The opposite of {@code direction} (case-insensitive), or {@code null} if it has no opposite.
   */
  public static String opposite(String direction) {
    return direction == null ? null : OPPOSITE.get(direction.toLowerCase());
  }
}

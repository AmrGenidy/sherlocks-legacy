package ui.terminal;

import java.util.List;

/**
 * Pure (no-FX) state machine for the terminal suggestion strip's keyboard flow
 * (.scratch/terminal-autocomplete issue 04). It owns the current suggestion list, the highlighted
 * chip index ({@code -1} = none highlighted) and the dismissed flag; {@link TerminalAutocomplete}
 * owns the JavaFX widgets and delegates every key/text decision here. Keeping the flow in a plain
 * object makes it unit-testable without the JavaFX toolkit, exactly like {@link CompletionEngine} /
 * {@link CompletionMatcher}.
 *
 * <p>Flow: typing recomputes the suggestions with <b>no chip highlighted</b>; ↑/↓ (and ←/→ at line
 * end) move a highlight across the chips, cycling through a "no highlight" slot so the player can
 * return to send-as-typed; <b>Tab or Enter</b> accept the highlighted chip into the input
 * <i>without</i> sending; a plain <b>Enter with no chip highlighted</b> sends the typed line;
 * <b>Escape</b> dismisses the strip.
 */
public final class SuggestionStripModel {

  /** What the view should do in response to a key press. */
  public enum KeyResult {
    /** Not handled here — let the key through (e.g. Enter with no highlight → send the line). */
    IGNORE,
    /** Highlight moved: repaint the chips and consume the key. */
    HIGHLIGHT_MOVED,
    /** Accept the highlighted chip into the input and consume the key (do NOT send). */
    ACCEPT,
    /** Strip dismissed: hide it and consume the key. */
    DISMISS
  }

  private List<CompletionEngine.Suggestion> suggestions = List.of();
  private int highlighted = -1;
  private boolean dismissed = false;

  /** Recompute on text change: new suggestions, nothing highlighted, not dismissed. */
  public void setSuggestions(List<CompletionEngine.Suggestion> newSuggestions) {
    this.suggestions = List.copyOf(newSuggestions);
    this.highlighted = -1;
    this.dismissed = false;
  }

  /** True while chips should be visible. */
  public boolean isShowing() {
    return !dismissed && !suggestions.isEmpty();
  }

  /**
   * The highlighted chip index, or {@code -1} when none is highlighted (or the strip is hidden).
   */
  public int highlightedIndex() {
    return isShowing() ? highlighted : -1;
  }

  /** The replacement string to insert when accepting, or {@code null} if nothing is highlighted. */
  public String highlightedReplacement() {
    int index = highlightedIndex();
    return index >= 0 ? suggestions.get(index).replacement() : null;
  }

  /**
   * The replacement the inline "ghost" should mirror: the highlighted chip, or — with nothing
   * highlighted — the first (best) suggestion; {@code null} when the strip is hidden. This is the
   * SAME source the chips render from, so the ghost and the highlighted chip can never disagree.
   */
  public String ghostReplacement() {
    if (!isShowing()) {
      return null;
    }
    int index = highlighted >= 0 ? highlighted : 0;
    return suggestions.get(index).replacement();
  }

  /**
   * ↑ (or ← at line end): move the highlight toward the FIRST (leftmost) chip. From the "no
   * highlight" rest state this lands on the first chip; further presses walk rightward and cycle
   * back through the no-highlight slot.
   */
  public KeyResult onUp() {
    return onArrow(1);
  }

  /**
   * ↓ (or → at line end): move the highlight toward the LAST (rightmost) chip. From the "no
   * highlight" rest state this lands on the last chip; further presses walk leftward and cycle back
   * through the no-highlight slot.
   */
  public KeyResult onDown() {
    return onArrow(-1);
  }

  /**
   * Pure index-stepper primitive: {@code delta} steps the highlighted index, cycling through a "no
   * highlight" slot (-1). The "no highlight" sentinel sits <i>before</i> index 0, so {@code +1}
   * enters the strip at the leftmost chip and {@code -1} wraps to the rightmost. Physical keys are
   * mapped to a direction by {@link #onUp()} / {@link #onDown()}.
   */
  public KeyResult onArrow(int delta) {
    if (!isShowing()) {
      return KeyResult.IGNORE;
    }
    int size = suggestions.size();
    int next = highlighted + delta;
    // Cycle through a "no highlight" slot (-1 .. size-1) so the player can step back to
    // send-as-typed.
    if (next < -1) {
      next = size - 1;
    } else if (next >= size) {
      next = -1;
    }
    highlighted = next;
    return KeyResult.HIGHLIGHT_MOVED;
  }

  /** Tab: accept the highlighted chip, or — with nothing highlighted — the first (best) match. */
  public KeyResult onTab() {
    if (!isShowing()) {
      return KeyResult.IGNORE;
    }
    if (highlighted < 0) {
      highlighted = 0;
    }
    return KeyResult.ACCEPT;
  }

  /** Enter: accept iff a chip is highlighted; otherwise let the line be sent. */
  public KeyResult onEnter() {
    return (isShowing() && highlighted >= 0) ? KeyResult.ACCEPT : KeyResult.IGNORE;
  }

  /** Escape: dismiss the strip if it is showing. */
  public KeyResult onEscape() {
    if (!isShowing()) {
      return KeyResult.IGNORE;
    }
    dismissed = true;
    return KeyResult.DISMISS;
  }
}

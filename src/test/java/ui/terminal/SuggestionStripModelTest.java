package ui.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;
import ui.terminal.CompletionEngine.Suggestion;
import ui.terminal.SuggestionStripModel.KeyResult;

/**
 * Key-event state machine for the terminal suggestion strip (.scratch/terminal-autocomplete issue
 * 04). Pure Java — no FX toolkit. Encodes the required keyboard accept flow:
 * typing→highlight→accept→send and typing→Escape→send, plus "Enter accepts (does not send) while a
 * chip is highlighted; a plain Enter with no highlight sends".
 */
public class SuggestionStripModelTest {

  private static List<Suggestion> chips(String... replacements) {
    return java.util.Arrays.stream(replacements)
        .map(r -> new Suggestion(r, r))
        .collect(java.util.stream.Collectors.toList());
  }

  private static SuggestionStripModel withChips(String... replacements) {
    SuggestionStripModel model = new SuggestionStripModel();
    model.setSuggestions(chips(replacements));
    return model;
  }

  @Test
  public void typingShowsChipsWithNothingHighlighted() {
    SuggestionStripModel model = withChips("examine", "exit");
    assertTrue(model.isShowing());
    assertEquals(-1, model.highlightedIndex());
    assertNull(model.highlightedReplacement());
  }

  @Test
  public void typingThenHighlightThenAcceptThenSend() {
    SuggestionStripModel model = withChips("examine", "exit");

    // Up moves the highlight onto the first (leftmost) chip.
    assertEquals(KeyResult.HIGHLIGHT_MOVED, model.onUp());
    assertEquals(0, model.highlightedIndex());

    // Enter accepts the highlighted chip — it must NOT send.
    assertEquals(KeyResult.ACCEPT, model.onEnter());
    assertEquals("examine", model.highlightedReplacement());

    // Accepting re-fires the suggestion refresh (the view calls setText → setSuggestions); the
    // highlight clears, so the NEXT Enter sends (IGNORE = not consumed = onAction sends).
    model.setSuggestions(chips("examine desk", "examine letter"));
    assertEquals(-1, model.highlightedIndex());
    assertEquals(KeyResult.IGNORE, model.onEnter());
  }

  @Test
  public void ghostMirrorsTheFirstChipWhenNothingHighlighted() {
    SuggestionStripModel model = withChips("examine", "exit");
    // No highlight yet: the ghost reflects the first (best) suggestion — same as what Tab accepts.
    assertEquals(-1, model.highlightedIndex());
    assertEquals("examine", model.ghostReplacement());
  }

  @Test
  public void ghostFollowsTheHighlightedChip() {
    SuggestionStripModel model = withChips("examine", "exit");
    model.onDown(); // -> last chip
    assertEquals(1, model.highlightedIndex());
    assertEquals("exit", model.ghostReplacement());
    model.onDown(); // -> middle-step toward first
    assertEquals(0, model.highlightedIndex());
    assertEquals("examine", model.ghostReplacement());
  }

  @Test
  public void ghostIsNullWhenNoChipsOrDismissed() {
    assertNull(new SuggestionStripModel().ghostReplacement()); // no suggestions
    SuggestionStripModel model = withChips("examine");
    model.onEscape();
    assertNull("dismissed strip has no ghost", model.ghostReplacement());
  }

  @Test
  public void typingThenEscapeThenSend() {
    SuggestionStripModel model = withChips("examine", "exit");
    assertEquals(KeyResult.DISMISS, model.onEscape());
    assertFalse(model.isShowing());
    // With the strip dismissed, Enter is not consumed → the typed line is sent.
    assertEquals(KeyResult.IGNORE, model.onEnter());
  }

  @Test
  public void plainEnterWithNoHighlightSends() {
    SuggestionStripModel model = withChips("examine", "exit");
    assertEquals(-1, model.highlightedIndex());
    assertEquals(KeyResult.IGNORE, model.onEnter());
  }

  @Test
  public void enterWithHighlightDoesNotSend() {
    SuggestionStripModel model = withChips("examine", "exit");
    model.onUp();
    assertEquals(KeyResult.ACCEPT, model.onEnter());
  }

  @Test
  public void tabWithNoHighlightAcceptsFirstMatch() {
    SuggestionStripModel model = withChips("examine", "exit");
    assertEquals(KeyResult.ACCEPT, model.onTab());
    assertEquals("examine", model.highlightedReplacement());
  }

  @Test
  public void tabAcceptsTheHighlightedChipWhenOneIsSelected() {
    SuggestionStripModel model = withChips("examine", "exit");
    model.onUp(); // -> 0 (first / leftmost)
    model.onUp(); // -> 1 (rightward)
    assertEquals(KeyResult.ACCEPT, model.onTab());
    assertEquals("exit", model.highlightedReplacement());
  }

  @Test
  public void upHighlightsTheFirstChipFirstThenWalksRightward() {
    SuggestionStripModel model = withChips("a", "b", "c");
    assertEquals(-1, model.highlightedIndex());

    // Up enters the strip at the FIRST (leftmost) chip.
    assertEquals(KeyResult.HIGHLIGHT_MOVED, model.onUp());
    assertEquals(0, model.highlightedIndex());
    // Further Up presses step rightward, then cycle back through the no-highlight slot.
    model.onUp();
    assertEquals(1, model.highlightedIndex());
    model.onUp();
    assertEquals(2, model.highlightedIndex());
    model.onUp();
    assertEquals(-1, model.highlightedIndex());
  }

  @Test
  public void downHighlightsTheLastChipFirstThenWalksLeftward() {
    SuggestionStripModel model = withChips("a", "b", "c");
    assertEquals(-1, model.highlightedIndex());

    // Down enters the strip at the LAST (rightmost) chip.
    assertEquals(KeyResult.HIGHLIGHT_MOVED, model.onDown());
    assertEquals(2, model.highlightedIndex());
    // Further Down presses step leftward, then cycle back through the no-highlight slot.
    model.onDown();
    assertEquals(1, model.highlightedIndex());
    model.onDown();
    assertEquals(0, model.highlightedIndex());
    model.onDown();
    assertEquals(-1, model.highlightedIndex());
  }

  @Test
  public void arrowsCycleThroughTheNoHighlightSlot() {
    // Low-level index-stepper primitive: +1 enters at index 0, -1 wraps to the last chip.
    SuggestionStripModel model = withChips("a", "b");
    assertEquals(-1, model.highlightedIndex());
    model.onArrow(1);
    assertEquals(0, model.highlightedIndex());
    model.onArrow(1);
    assertEquals(1, model.highlightedIndex());
    model.onArrow(1); // wraps past the end back to "no highlight"
    assertEquals(-1, model.highlightedIndex());

    // Backward from no-highlight lands on the last chip.
    model.onArrow(-1);
    assertEquals(1, model.highlightedIndex());
    model.onArrow(-1);
    assertEquals(0, model.highlightedIndex());
    model.onArrow(-1);
    assertEquals(-1, model.highlightedIndex());
  }

  @Test
  public void keysAreIgnoredWhenNotShowing() {
    SuggestionStripModel model = new SuggestionStripModel(); // no suggestions
    assertFalse(model.isShowing());
    assertEquals(KeyResult.IGNORE, model.onArrow(1));
    assertEquals(KeyResult.IGNORE, model.onTab());
    assertEquals(KeyResult.IGNORE, model.onEnter());
    assertEquals(KeyResult.IGNORE, model.onEscape());
  }

  @Test
  public void emptySuggestionListNeverShows() {
    SuggestionStripModel model = withChips();
    assertFalse(model.isShowing());
    assertEquals(-1, model.highlightedIndex());
  }
}

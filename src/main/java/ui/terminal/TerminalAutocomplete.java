package ui.terminal;

import java.util.List;
import java.util.function.Supplier;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;

/**
 * The suggestion strip inside the terminal panel (.scratch/terminal-autocomplete issues 02 + 04): a
 * single horizontal row of flat vellum chips above the input field — no floating {@code
 * Popup}/{@code ContextMenu} windows, per DESIGN.md.
 *
 * <p>Keyboard flow (issue 04) is decided by the pure {@link SuggestionStripModel}; this class only
 * binds it to the FX widgets. Typing recomputes suggestions with <b>no chip highlighted</b>; ↑/↓
 * (and ←/→ while the caret sits at line end) move a visible petrol highlight across the chips — ↑/←
 * toward the first (leftmost) chip, ↓/→ toward the last (rightmost) — Tab
 * accepts the highlighted chip (or the first match), and <b>Enter</b> accepts the highlighted chip
 * <i>without sending</i> — the next Enter sends, because accepting re-fires the suggestion refresh
 * and clears the highlight. A plain Enter with no chip highlighted is not consumed, so the input's
 * {@code onAction} sends the line. Escape dismisses the strip only — the shell's Escape chain
 * checks {@link #isShowingSuggestions()} first. Chips remain clickable and never steal focus. The
 * strip stays LEFT_TO_RIGHT in every UI language: commands are Latin-script, same rule as the input
 * field.
 */
public final class TerminalAutocomplete {

  private static final int MAX_CHIPS = 6;

  private final TextField input;
  private final HBox strip;
  private final Supplier<CompletionContext> contextSupplier;
  private final SuggestionStripModel model = new SuggestionStripModel();

  // Inline "ghost": the currently-relevant suggestion's remaining suffix, drawn faded in the input
  // right after the typed text. Mirrors the chip strip (same model) so the two never disagree.
  private final Label ghost = new Label();

  private List<CompletionEngine.Suggestion> suggestions = List.of();

  public TerminalAutocomplete(
      TextField input, HBox strip, Supplier<CompletionContext> contextSupplier) {
    this.input = input;
    this.strip = strip;
    this.contextSupplier = contextSupplier;

    strip.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
    strip.managedProperty().bind(strip.visibleProperty());
    strip.setVisible(false);
    strip.setFocusTraversable(false);

    ghost.getStyleClass().add("terminal-ghost");
    ghost.setMouseTransparent(true);
    ghost.setPadding(Insets.EMPTY);
    ghost.setVisible(false);
    installGhostOverlay();

    input.textProperty().addListener((obs, oldText, newText) -> refresh());
    input.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
  }

  /**
   * Overlays the (non-interactive) ghost {@link Label} on top of the input by wrapping the {@link
   * TextField} in a {@link StackPane} in place, so a TextField (which can't render two-tone text)
   * keeps its normal committed text while the faded suffix is drawn after it. If the input has no
   * {@link HBox} host (e.g. a bare test harness) the overlay is skipped and the chips still work.
   */
  private void installGhostOverlay() {
    Parent parent = input.getParent();
    if (!(parent instanceof HBox hbox)) {
      return;
    }
    int index = hbox.getChildren().indexOf(input);
    if (index < 0) {
      return;
    }
    Priority hgrow = HBox.getHgrow(input);
    hbox.getChildren().remove(input);
    StackPane wrapper = new StackPane(input, ghost);
    wrapper.setAlignment(Pos.CENTER_LEFT);
    StackPane.setAlignment(ghost, Pos.CENTER_LEFT);
    HBox.setHgrow(wrapper, hgrow != null ? hgrow : Priority.ALWAYS);
    // Keep a long suffix from spilling past the field into the panel.
    Rectangle clip = new Rectangle();
    clip.widthProperty().bind(wrapper.widthProperty());
    clip.heightProperty().bind(wrapper.heightProperty());
    wrapper.setClip(clip);
    hbox.getChildren().add(index, wrapper);
  }

  /** True while suggestion chips are visible — the shell's Escape chain checks this first. */
  public boolean isShowingSuggestions() {
    return model.isShowing();
  }

  /** Hides the strip until the input text changes again (Escape, or the shell's Escape chain). */
  public void dismiss() {
    model.onEscape();
    syncStrip();
  }

  private void onKeyPressed(KeyEvent event) {
    switch (event.getCode()) {
      case TAB:
        // The terminal input never yields focus on Tab — always consume.
        if (model.onTab() == SuggestionStripModel.KeyResult.ACCEPT) {
          acceptHighlighted();
        }
        event.consume();
        break;
      case ENTER:
        // Accept-without-send when a chip is highlighted; otherwise let onAction send the line.
        if (model.onEnter() == SuggestionStripModel.KeyResult.ACCEPT) {
          acceptHighlighted();
          event.consume();
        }
        break;
      case UP:
        // Up moves toward the first (leftmost) chip; ← does the same at line end.
        handleArrow(model.onUp(), event);
        break;
      case DOWN:
        // Down moves toward the last (rightmost) chip; → does the same at line end.
        handleArrow(model.onDown(), event);
        break;
      case LEFT:
        if (caretAtEnd()) {
          handleArrow(model.onUp(), event);
        }
        break;
      case RIGHT:
        if (caretAtEnd()) {
          handleArrow(model.onDown(), event);
        }
        break;
      case ESCAPE:
        if (model.onEscape() == SuggestionStripModel.KeyResult.DISMISS) {
          syncStrip();
          event.consume();
        }
        break;
      default:
        break;
    }
  }

  private void handleArrow(SuggestionStripModel.KeyResult result, KeyEvent event) {
    if (result == SuggestionStripModel.KeyResult.HIGHLIGHT_MOVED) {
      paintHighlight();
      event.consume();
    }
  }

  private boolean caretAtEnd() {
    return input.getCaretPosition() >= input.getLength();
  }

  private void refresh() {
    String text = input.getText();
    if (text == null || text.isBlank()) {
      render(List.of());
      return;
    }
    CompletionContext context = contextSupplier.get();
    List<CompletionEngine.Suggestion> all = CompletionEngine.suggest(text, context);
    // Drop suggestions that would not change the line, cap at the strip width.
    List<CompletionEngine.Suggestion> visible =
        all.stream().filter(s -> !s.replacement().equals(text)).limit(MAX_CHIPS).toList();
    render(visible);
  }

  private void render(List<CompletionEngine.Suggestion> newSuggestions) {
    suggestions = newSuggestions;
    model.setSuggestions(newSuggestions);
    strip.getChildren().clear();
    for (int i = 0; i < suggestions.size(); i++) {
      final int index = i;
      Label chip = new Label(suggestions.get(i).label());
      chip.getStyleClass().add("suggestion-chip");
      chip.setFocusTraversable(false);
      chip.setOnMouseClicked(
          event -> {
            acceptAt(index);
            input.requestFocus();
          });
      strip.getChildren().add(chip);
    }
    paintHighlight();
    strip.setVisible(model.isShowing());
  }

  /** Repaint the active-chip class from the model's highlighted index (-1 = none). */
  private void paintHighlight() {
    int active = model.highlightedIndex();
    for (int i = 0; i < strip.getChildren().size(); i++) {
      strip.getChildren().get(i).getStyleClass().remove("suggestion-chip--active");
      if (i == active) {
        strip.getChildren().get(i).getStyleClass().add("suggestion-chip--active");
      }
    }
    // The ghost mirrors the same highlight state, so it moves in lock-step with the chips.
    updateGhost();
  }

  /** Hide/show the strip to match the model after a dismiss. */
  private void syncStrip() {
    strip.setVisible(model.isShowing());
    updateGhost();
  }

  /**
   * Draws the ghost as the remaining suffix of the currently-relevant suggestion (the highlighted
   * chip, or the first when none is highlighted), positioned right after the typed text; hides it
   * when there is no clean suffix to add or the strip is not showing.
   */
  private void updateGhost() {
    String suffix = ghostSuffix(input.getText(), model.ghostReplacement());
    if (suffix.isEmpty()) {
      ghost.setVisible(false);
      ghost.setText("");
      return;
    }
    ghost.setFont(input.getFont()); // match the input's live (theme/scale-aware) font
    ghost.setText(suffix);
    ghost.setTranslateX(input.getInsets().getLeft() + measureWidth(input.getText()));
    ghost.setVisible(true);
  }

  /**
   * The part of {@code replacement} that extends past what is already typed — the suffix the ghost
   * shows. Empty when there is no clean prefix-extension (e.g. nothing to add, or a case-mismatch),
   * so the ghost simply stays hidden. Pure/static for unit testing.
   */
  static String ghostSuffix(String typed, String replacement) {
    if (typed == null || replacement == null || !replacement.startsWith(typed)) {
      return "";
    }
    return replacement.substring(typed.length());
  }

  /** Pixel width of {@code text} in the input's current font (for placing the ghost). */
  private double measureWidth(String text) {
    Text probe = new Text(text);
    probe.setFont(input.getFont());
    return probe.getLayoutBounds().getWidth();
  }

  /** Test seam: the suffix currently shown by the ghost ("" when hidden). */
  String ghostTextForTest() {
    return ghost.isVisible() ? ghost.getText() : "";
  }

  private void acceptHighlighted() {
    acceptText(model.highlightedReplacement());
  }

  private void acceptAt(int index) {
    if (index < 0 || index >= suggestions.size()) {
      return;
    }
    acceptText(suggestions.get(index).replacement());
  }

  private void acceptText(String replacement) {
    if (replacement == null) {
      return;
    }
    // setText re-fires refresh() → the model resets the highlight to none, so the next Enter sends.
    input.setText(replacement);
    input.positionCaret(replacement.length());
  }
}

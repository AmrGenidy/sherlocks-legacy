package ui.terminal;

import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.HitInfo;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import ui.i18n.L10n;
import ui.util.Motion;

/**
 * The in-game terminal transcript: a deep, warm <b>sunken well</b> built on a {@link TextFlow}
 * inside a {@link ScrollPane} (.scratch/ingame-terminal-polish issue 01). Unlike the {@code
 * TextArea} it replaces, a {@code TextFlow} lets every line carry its own ink colour ({@link
 * TerminalLineKind}) and fade in independently (DEC-1).
 *
 * <p>Each appended chunk becomes one {@link Text} node styled {@code .terminal-line} plus the
 * kind's class, so the colour lives in {@code detective-theme.css}; the node fades in over {@link
 * Motion#TERMINAL_LINE} (DEC-6).
 *
 * <p><b>History-aware auto-scroll</b> (DEC-7, issue 05): the view follows new output to the bottom
 * while pinned there, and leaves the player's position alone once they scroll up to read history,
 * until they scroll back to the bottom or {@link #repinToBottom()} is called (the shell does so on
 * every command submit). Crucially, the pin is dropped <b>only on a genuine user scroll-up</b> —
 * the view guards its own programmatic scrolls and ignores layout-induced {@code vvalue} changes
 * (which preserve the normalized position), so appearing chips / resizes can no longer kill
 * auto-scroll. The decision logic is the pure, unit-tested {@link TerminalScroll}.
 *
 * <p>Constructed with a no-arg constructor so it can be declared directly in {@code main.fxml}. All
 * mutators must run on the FX Application Thread (every caller already marshals there).
 */
public final class TerminalView extends ScrollPane {

  /** "At the bottom" tolerance — generous so returning near the bottom re-pins reliably. */
  private static final double AT_BOTTOM_EPSILON = 0.02;

  /**
   * Minimum downward… er, upward {@code vvalue} drop that counts as a deliberate user scroll-up.
   */
  private static final double USER_SCROLL_EPSILON = 0.0005;

  /** A content-height increase beyond this (px) past the last scroll counts as a growth. */
  private static final double GROWTH_EPSILON = 0.5;

  /** Soft cap on retained lines so a long session does not grow the flow unbounded. */
  private static final int MAX_LINES = 600;

  private final TextFlow flow = new TextFlow();
  private boolean pinnedToBottom = true;

  /**
   * True only while the view is performing its own scroll-to-bottom, so it is not read as a user
   * action by the {@code vvalue} listener.
   */
  private boolean programmaticScroll = false;

  /**
   * The content height the view last scrolled to; growth beyond it is a content growth, not a user
   * scroll (.scratch/terminal-scroll-mp issue 04).
   */
  private double lastScrolledHeight = 0;

  public TerminalView() {
    getStyleClass().add("terminal-area");
    flow.getStyleClass().add("terminal-flow");

    setContent(flow);
    setFitToWidth(true); // the transcript reflows to width — no horizontal scrollbar, no clipping
    setHbarPolicy(ScrollBarPolicy.NEVER);
    setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
    setFocusTraversable(false);

    // One value listener covers wheel, drag, Page-Up and arrow scrolling uniformly. Only a genuine
    // user scroll-up unpins; reaching the bottom re-pins; our own AND content-growth-induced
    // changes
    // are ignored. A tall multi-line block (room change / look) grows the content, and the real-GUI
    // ScrollPane keeps the pixel scrollTop so vvalue DROPS — that drop must NOT be read as a
    // scroll-up
    // (.scratch/terminal-scroll-mp issue 04), or the height listener below would see "not pinned"
    // and
    // never scroll the block into view.
    vvalueProperty()
        .addListener(
            (obs, oldV, newV) -> {
              boolean contentGrew = flow.getHeight() > lastScrolledHeight + GROWTH_EPSILON;
              if (TerminalScroll.isUserScrollUp(
                  oldV.doubleValue(),
                  newV.doubleValue(),
                  programmaticScroll,
                  contentGrew,
                  USER_SCROLL_EPSILON)) {
                pinnedToBottom = false;
              } else if (TerminalScroll.isAtBottom(
                  newV.doubleValue(), getVmax(), AT_BOTTOM_EPSILON)) {
                pinnedToBottom = true;
              }
            });

    // Pin to bottom on CONTENT-HEIGHT GROWTH (issue 04): the height of a multi-line block grows on
    // a
    // later layout pass that the one-shot append-scroll misses, so re-assert the bottom whenever
    // the
    // content gets taller while engaged.
    flow.heightProperty().addListener((obs, oldH, newH) -> scrollToBottomIfPinned());

    installCopySupport();
  }

  // ---- Copy support -------------------------------------------------------------------------

  /**
   * Gives the read-only {@code TextFlow} transcript the copy affordances it otherwise lacks (a
   * TextFlow has no selection): a right-click "Copy all" / "Copy line" menu, plus Ctrl+C / Ctrl+A
   * to copy the whole transcript when the terminal is focused. Per-line ink colours are untouched —
   * we only read the {@code Text} nodes' strings. A click focuses the view so the key shortcuts
   * fire (the view is not in the Tab ring, so this is the only way it takes focus).
   */
  private void installCopySupport() {
    MenuItem copyAll = new MenuItem(L10n.t("terminal.copyAll"));
    copyAll.setOnAction(e -> copyToClipboard(transcriptText()));

    MenuItem copyLine = new MenuItem(L10n.t("terminal.copyLine"));
    ContextMenu menu = new ContextMenu(copyAll, copyLine);

    setOnContextMenuRequested(
        e -> {
          // Resolve the line under the cursor lazily, at request time, from the flow-wide character
          // index the TextFlow hit-tests for us (accurate even within a multi-line chunk node).
          String line = lineUnderCursor(e.getScreenX(), e.getScreenY());
          copyLine.setDisable(line.isEmpty());
          copyLine.setOnAction(ev -> copyToClipboard(line));
          menu.show(this, e.getScreenX(), e.getScreenY());
          e.consume();
        });

    // A press anywhere in the transcript focuses the view (so Ctrl+C/A reach it) and dismisses any
    // open menu on a left click.
    flow.addEventHandler(
        MouseEvent.MOUSE_PRESSED,
        e -> {
          requestFocus();
          if (e.getButton() == MouseButton.PRIMARY) {
            menu.hide();
          }
        });

    addEventHandler(
        KeyEvent.KEY_PRESSED,
        e -> {
          if (e.isShortcutDown() && (e.getCode() == KeyCode.C || e.getCode() == KeyCode.A)) {
            copyToClipboard(transcriptText());
            e.consume();
          }
        });
  }

  /** The whole transcript as plain text (per-line {@code Text} node strings, in order). */
  String transcriptText() {
    List<String> chunks = new ArrayList<>();
    for (var node : flow.getChildren()) {
      if (node instanceof Text text) {
        chunks.add(text.getText());
      }
    }
    return TerminalClipboardText.fullText(chunks);
  }

  /** The transcript line under the given screen point, or "" if the point is past the text. */
  private String lineUnderCursor(double screenX, double screenY) {
    Point2D local = flow.screenToLocal(screenX, screenY);
    if (local == null) {
      return "";
    }
    HitInfo hit = flow.hitTest(local);
    if (hit == null) {
      return "";
    }
    return TerminalClipboardText.lineAt(transcriptText(), hit.getCharIndex());
  }

  private static void copyToClipboard(String text) {
    if (text == null || text.isEmpty()) {
      return;
    }
    ClipboardContent content = new ClipboardContent();
    content.putString(text);
    Clipboard.getSystemClipboard().setContent(content);
  }

  /**
   * Appends one line/chunk (newlines within {@code text} are honoured) coloured by {@code kind}.
   */
  public void appendLine(String text, TerminalLineKind kind) {
    if (text == null || text.isEmpty()) {
      return;
    }
    Text node = new Text(text);
    node.getStyleClass().add("terminal-line");
    node.getStyleClass().add((kind == null ? TerminalLineKind.NORMAL : kind).cssClass());
    node.setOpacity(0.0);
    flow.getChildren().add(node);
    trimToCap();
    Motion.fadeIn(node, Motion.TERMINAL_LINE).play();
    // Scroll on append itself (not only via the height listener) so output always follows.
    scrollToBottomIfPinned();
  }

  /** Clears the transcript and re-pins to the bottom. */
  public void clear() {
    flow.getChildren().clear();
    repinToBottom();
  }

  /** Re-pin to the bottom and scroll there (the shell calls this when the player submits). */
  public void repinToBottom() {
    pinnedToBottom = true;
    scrollToBottomIfPinned();
  }

  private void scrollToBottomIfPinned() {
    if (!pinnedToBottom) {
      return;
    }
    // Defer so the scroll lands after this pulse, then FORCE layout before scrolling: a freshly
    // appended Text node has not been laid out yet, so without applyCss()/layout() the scrollable
    // extent is stale and setVvalue lands at the OLD bottom (the real "doesn't scroll" bug —
    // .scratch/ingame-fixes-2 issue 02). With the layout realised first, vmax is the true bottom.
    Platform.runLater(
        () -> {
          if (!pinnedToBottom) {
            return;
          }
          // Guard the WHOLE operation: the forced layout can itself nudge vvalue, and that nudge
          // must
          // not be misread by the listener as a user scroll-up that unpins us (.scratch/
          // terminal-scroll-mp issue 03).
          programmaticScroll = true;
          try {
            applyCss();
            layout();
            setVvalue(getVmax());
            // Record the height we just scrolled to: any further growth past this is a content
            // growth (not a user scroll) until the next scroll catches up (issue 04).
            lastScrolledHeight = flow.getHeight();
          } finally {
            programmaticScroll = false;
          }
        });
  }

  private void trimToCap() {
    int over = flow.getChildren().size() - MAX_LINES;
    if (over > 0) {
      flow.getChildren().remove(0, over);
    }
  }
}

package ui.terminal;

import java.util.List;

/**
 * Pure text helpers for copying from the {@link TerminalView} transcript (a {@link
 * javafx.scene.text.TextFlow} of per-line {@code Text} nodes, which offers no built-in selection).
 * Kept free of any JavaFX node/clipboard dependency so the copy semantics are unit-testable.
 */
public final class TerminalClipboardText {

  private TerminalClipboardText() {}

  /**
   * The full transcript as plain text: the chunk strings (each already carries its own newlines)
   * concatenated in order, exactly as they were appended. Null chunks are treated as empty.
   */
  public static String fullText(List<String> chunks) {
    if (chunks == null || chunks.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (String chunk : chunks) {
      if (chunk != null) {
        sb.append(chunk);
      }
    }
    return sb.toString();
  }

  /**
   * The single line of {@code text} containing character index {@code offset} — the run between the
   * preceding and following newline, with the newline itself excluded. Used by "Copy line" with the
   * flow-wide character index under the cursor. The offset is clamped into range; an offset sitting
   * on a line's terminating newline (or at the very end) yields that line.
   */
  public static String lineAt(String text, int offset) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    int clamped = Math.max(0, Math.min(offset, text.length()));
    int start = clamped == 0 ? 0 : text.lastIndexOf('\n', clamped - 1) + 1;
    int end = text.indexOf('\n', clamped);
    if (end < 0) {
      end = text.length();
    }
    return text.substring(start, end);
  }
}

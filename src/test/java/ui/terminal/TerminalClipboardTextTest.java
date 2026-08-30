package ui.terminal;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Test;

/** Copy-from-transcript text semantics (Copy all / Copy line) for {@link TerminalClipboardText}. */
public class TerminalClipboardTextTest {

  @Test
  public void fullText_concatenatesChunksVerbatim() {
    // Chunks already carry their own newlines (the terminal appends "line\n").
    assertEquals(
        "Welcome.\nContradiction successful! +1 Insight Token\nStatement added to journal.\n",
        TerminalClipboardText.fullText(
            List.of(
                "Welcome.\n",
                "Contradiction successful! +1 Insight Token\n",
                "Statement added to journal.\n")));
  }

  @Test
  public void fullText_handlesNullAndEmpty() {
    assertEquals("", TerminalClipboardText.fullText(null));
    assertEquals("", TerminalClipboardText.fullText(List.of()));
    assertEquals("a b", TerminalClipboardText.fullText(java.util.Arrays.asList("a ", null, "b")));
  }

  @Test
  public void lineAt_returnsTheEnclosingLineWithoutNewline() {
    String text = "first line\nsecond line\nthird line\n";
    // offset inside "second line"
    assertEquals("second line", TerminalClipboardText.lineAt(text, 15));
    // offset at the very start
    assertEquals("first line", TerminalClipboardText.lineAt(text, 0));
    // offset inside "third line"
    assertEquals("third line", TerminalClipboardText.lineAt(text, 24));
  }

  @Test
  public void lineAt_offsetOnNewlineYieldsThatLine() {
    String text = "alpha\nbeta\n";
    // index 5 is the '\n' terminating "alpha"
    assertEquals("alpha", TerminalClipboardText.lineAt(text, 5));
    // index 6 is the start of "beta"
    assertEquals("beta", TerminalClipboardText.lineAt(text, 6));
  }

  @Test
  public void lineAt_clampsOutOfRangeOffsets() {
    String text = "only line\n";
    assertEquals("only line", TerminalClipboardText.lineAt(text, -5));
    // past the end lands on the trailing empty line after the final newline
    assertEquals("", TerminalClipboardText.lineAt(text, 999));
    assertEquals("", TerminalClipboardText.lineAt("", 0));
  }

  @Test
  public void lineAt_lineWithoutTrailingNewline() {
    assertEquals("no newline", TerminalClipboardText.lineAt("no newline", 3));
  }
}

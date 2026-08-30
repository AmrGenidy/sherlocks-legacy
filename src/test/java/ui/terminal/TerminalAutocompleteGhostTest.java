package ui.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The inline autocomplete "ghost" mirrors the chip strip: it shows the remaining suffix of the
 * highlighted chip (or the first when none), Tab commits it, and typing/backspacing recompute it.
 * Headless-FX; the pure highlight↔ghost source-of-truth is covered by {@link SuggestionStripModelTest}.
 */
public class TerminalAutocompleteGhostTest {

  @BeforeClass
  public static void initJFX() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    try {
      Platform.startup(latch::countDown);
    } catch (IllegalStateException alreadyStarted) {
      latch.countDown();
    }
    latch.await(5, TimeUnit.SECONDS);
  }

  /** An "examine" command that takes an argument (so its replacement gets a trailing space). */
  private static CompletionContext examineContext(List<String> objects) {
    return CompletionContext.builder().commandWithArgs("examine", objects).command("exit").build();
  }

  private static void onFx(Runnable body) throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    Throwable[] error = new Throwable[1];
    Platform.runLater(
        () -> {
          try {
            body.run();
          } catch (Throwable t) {
            error[0] = t;
          } finally {
            latch.countDown();
          }
        });
    assertTrue("FX task timed out", latch.await(5, TimeUnit.SECONDS));
    if (error[0] != null) {
      fail("Exception on FX thread: " + error[0]);
    }
  }

  private static void fireKey(TextField input, KeyCode code) {
    input.fireEvent(
        new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
  }

  @Test
  public void typingShowsGhostAndTabCommitsItAndClears() throws InterruptedException {
    onFx(
        () -> {
          TextField input = new TextField();
          HBox strip = new HBox();
          HBox row = new HBox(input); // the host the ghost overlay wraps
          new Scene(row, 400, 40);
          // examine takes no in-context objects → after "examine " there is nothing more to ghost.
          TerminalAutocomplete auto =
              new TerminalAutocomplete(input, strip, () -> examineContext(List.of()));

          input.setText("exa");
          assertEquals("ghost shows the remaining suffix", "mine ", auto.ghostTextForTest());

          fireKey(input, KeyCode.TAB); // accept whatever the ghost shows
          assertEquals("Tab commits the completion", "examine ", input.getText());
          assertEquals("ghost clears once there is nothing more to add", "", auto.ghostTextForTest());
        });
  }

  @Test
  public void arrowingMovesTheGhostToTheHighlightedChip() throws InterruptedException {
    onFx(
        () -> {
          TextField input = new TextField();
          HBox strip = new HBox();
          HBox row = new HBox(input);
          new Scene(row, 400, 40);
          // Two suggestions for "ex": "examine " and "exit" — arrowing must move the ghost.
          TerminalAutocomplete auto =
              new TerminalAutocomplete(input, strip, () -> examineContext(List.of()));

          input.setText("ex");
          String first = auto.ghostTextForTest();
          assertTrue("a ghost is shown while chips are up", !first.isEmpty());

          fireKey(input, KeyCode.DOWN); // move the highlight → ghost follows to the other chip
          String afterArrow = auto.ghostTextForTest();
          assertNotEquals("the ghost moved with the highlight", first, afterArrow);
        });
  }

  @Test
  public void backspaceRecomputesTheGhostAndEmptyInputHidesIt() throws InterruptedException {
    onFx(
        () -> {
          TextField input = new TextField();
          HBox strip = new HBox();
          HBox row = new HBox(input);
          new Scene(row, 400, 40);
          TerminalAutocomplete auto =
              new TerminalAutocomplete(input, strip, () -> examineContext(List.of()));

          input.setText("exam");
          assertEquals("ine ", auto.ghostTextForTest());

          input.setText("exa"); // backspace
          assertEquals("mine ", auto.ghostTextForTest());

          input.setText(""); // cleared → no chips, no ghost
          assertEquals("", auto.ghostTextForTest());
        });
  }
}

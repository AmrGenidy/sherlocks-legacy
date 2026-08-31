package ui.util;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Headless guard for the TWO independent text-size scales (.scratch/gui-typography-readability;
 * Phase 3 = whole-interface reading scale): the {@code term-scale-NNN} root class resizes ONLY the
 * terminal family (absolute px), and the {@code read-scale-NNN} class re-bases the root font size
 * so EVERYTHING sized in em — reading content, pinboard notes, menu chrome, buttons, plain labels —
 * follows, while the terminal stays put. Resolved against the real {@code detective-theme.css} (+
 * {@code pinboard.css} for the note rules), mirroring {@code InputThemingTest}.
 */
public class ContentScaleStylingTest {

  private static final double EPS = 0.01;

  @BeforeClass
  public static void initJfx() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    try {
      Platform.startup(latch::countDown);
    } catch (IllegalStateException alreadyStarted) {
      latch.countDown();
    }
    assertTrue("JavaFX did not start", latch.await(5, TimeUnit.SECONDS));
    // The real app always loads the bundled faces; font-relative CSS resolves differently once
    // "Special Elite" etc. actually exist, so test the representative condition.
    CountDownLatch fonts = new CountDownLatch(1);
    Platform.runLater(
        () -> {
          try {
            ui.util.FontLoader.loadAll();
          } finally {
            fonts.countDown();
          }
        });
    assertTrue("font loading timed out", fonts.await(10, TimeUnit.SECONDS));
  }

  @Test
  public void terminalScaleResizesOnlyTheTerminal() throws Exception {
    onFx(
        () -> {
          double baseLine = terminalLineSize("term-scale-100");
          double grownLine = terminalLineSize("term-scale-140");
          assertTrue(
              "the terminal slider must enlarge the transcript text ("
                  + baseLine
                  + "→"
                  + grownLine
                  + ")",
              grownLine > baseLine + 0.5);

          // Nothing outside the terminal family carries a term-scale rule, so none of it moves.
          assertClose(
              "reading content must NOT change with the terminal slider",
              labelSize("dialogue-bubble-content", false, "term-scale-100"),
              labelSize("dialogue-bubble-content", false, "term-scale-140"));
          assertClose(
              "the tutorial bubble (reading content) must NOT change with the terminal slider",
              labelSize("tutorial-overlay-label", false, "term-scale-100"),
              labelSize("tutorial-overlay-label", false, "term-scale-140"));
          assertClose(
              "menu chrome must NOT change with the terminal slider",
              labelSize("menu-page-title", false, "term-scale-100"),
              labelSize("menu-page-title", false, "term-scale-140"));
        });
  }

  @Test
  public void readingScaleResizesReadingAndPinboardButNotTerminalOrChrome() throws Exception {
    onFx(
        () -> {
          double baseBody = labelSize("dialogue-bubble-content", false, "read-scale-100");
          double grownBody = labelSize("dialogue-bubble-content", false, "read-scale-140");
          assertTrue(
              "the reading slider must enlarge the popup body (" + baseBody + "→" + grownBody + ")",
              grownBody > baseBody + 0.5);

          double basePin = labelSize("pinboard-item-content", true, "read-scale-100");
          double grownPin = labelSize("pinboard-item-content", true, "read-scale-140");
          assertTrue(
              "the reading slider must enlarge pinboard note text ("
                  + basePin
                  + "→"
                  + grownPin
                  + ")",
              grownPin > basePin + 0.5);

          // Tasks are calibrated to the reading fonts and scale with the same slider.
          double baseTask = labelSize("task-label", false, "read-scale-100");
          double grownTask = labelSize("task-label", false, "read-scale-140");
          assertTrue(
              "the reading slider must enlarge task text (" + baseTask + "→" + grownTask + ")",
              grownTask > baseTask + 0.5);

          // The tutorial guidance bubble is reading content and scales with the same slider
          // (.scratch/gui-typography-readability).
          double baseTut = labelSize("tutorial-overlay-label", false, "read-scale-100");
          double grownTut = labelSize("tutorial-overlay-label", false, "read-scale-140");
          assertTrue(
              "the reading slider must enlarge the tutorial guidance bubble ("
                  + baseTut
                  + "→"
                  + grownTut
                  + ")",
              grownTut > baseTut + 0.5);

          // Phase 3: the reading slider re-bases the root font, so the WHOLE interface follows —
          // menu chrome, buttons, even a plain unclassed label.
          double baseTitle = labelSize("menu-page-title", false, "read-scale-100");
          double grownTitle = labelSize("menu-page-title", false, "read-scale-140");
          assertTrue(
              "the reading slider must enlarge menu chrome (" + baseTitle + "→" + grownTitle + ")",
              grownTitle > baseTitle + 0.5);
          double baseButton = buttonSize("button", "read-scale-100");
          double grownButton = buttonSize("button", "read-scale-140");
          assertTrue(
              "the reading slider must enlarge button text ("
                  + baseButton
                  + "→"
                  + grownButton
                  + ")",
              grownButton > baseButton + 0.5);
          double basePlain = labelSize(null, false, "read-scale-100");
          double grownPlain = labelSize(null, false, "read-scale-140");
          assertTrue(
              "the reading slider must enlarge plain (inherited-size) labels ("
                  + basePlain
                  + "→"
                  + grownPlain
                  + ")",
              grownPlain > basePlain + 0.5);

          // The terminal family pins absolute px sizes, so the reading slider never reaches it.
          assertClose(
              "the terminal must NOT change with the reading slider",
              terminalLineSize("read-scale-100"),
              terminalLineSize("read-scale-140"));
          assertClose(
              "the suggestion chips must NOT change with the reading slider",
              buttonSize("suggestion-chip", "read-scale-100"),
              buttonSize("suggestion-chip", "read-scale-140"));
        });
  }

  @Test
  public void hundredPercentMatchesTheDesignedBaseAndNestingNeverCompounds() throws Exception {
    onFx(
        () -> {
          // The 100% bucket must render the designed sizes: 12px root base, 16px popup body.
          assertClose(
              "100% must keep the 12px root base for plain labels",
              12.0, labelSize(null, false, "read-scale-100"));
          assertClose(
              "100% must keep the designed 16px popup body",
              16.0, labelSize("dialogue-bubble-content", false, "read-scale-100"));

          // Em ratios must not compound through nesting: a .typewriter Label is 14px and its inner
          // .text node takes the SAME 14px (1em of the label's font), not ratio² (16.3px).
          Label typed = new Label("torn letter");
          typed.getStyleClass().add("typewriter");
          build(typed, false, "read-scale-100");
          assertClose("a .typewriter label must be 14px at 100%", 14.0, typed.getFont().getSize());
          Node innerText = typed.lookup(".text");
          assertTrue("the label skin must expose its .text node", innerText instanceof Text);
          assertClose(
              "the label's inner .text must take the label's 14px, not compound",
              14.0,
              ((Text) innerText).getFont().getSize());

          // A TextFlow has no font property, so its spans are sized directly
          // (.lang-suggest-text .banner-span) and must still follow the root re-base.
          Text span = new Text("Русский");
          span.getStyleClass().add("banner-span");
          javafx.scene.text.TextFlow flow = new javafx.scene.text.TextFlow(span);
          flow.getStyleClass().add("lang-suggest-text");
          build(flow, false, "read-scale-100");
          assertClose(
              "a banner span must keep its designed 13px at 100%", 13.0, span.getFont().getSize());
        });
  }

  private static double terminalLineSize(String... rootClasses) {
    Text line = new Text("examine the writing desk");
    line.getStyleClass().add("terminal-line");
    build(line, false, rootClasses);
    return line.getFont().getSize();
  }

  private static double labelSize(
      String styleClass, boolean withPinboardCss, String... rootClasses) {
    Label label = new Label("The Sapphire Falcon");
    if (styleClass != null) {
      label.getStyleClass().add(styleClass);
    }
    build(label, withPinboardCss, rootClasses);
    return label.getFont().getSize();
  }

  private static double buttonSize(String styleClass, String... rootClasses) {
    javafx.scene.control.Button button = new javafx.scene.control.Button("Begin the case");
    if (!button.getStyleClass().contains(styleClass)) {
      button.getStyleClass().add(styleClass);
    }
    build(button, false, rootClasses);
    return button.getFont().getSize();
  }

  /**
   * Lays out the node under a root carrying {@code rootClasses}, against the real stylesheet(s).
   */
  private static void build(Node node, boolean withPinboardCss, String... rootClasses) {
    VBox root = new VBox(node);
    root.getStyleClass().addAll(rootClasses);
    Scene scene = new Scene(root);
    scene.getStylesheets().add(ui.util.Theme.baseStylesheet());
    if (withPinboardCss) {
      scene
          .getStylesheets()
          .add(ContentScaleStylingTest.class.getResource("/css/pinboard.css").toExternalForm());
    }
    root.applyCss();
    root.layout();
  }

  private static void assertClose(String message, double a, double b) {
    assertTrue(message + " (" + a + " vs " + b + ")", Math.abs(a - b) < EPS);
  }

  private interface FxTask {
    void run() throws Exception;
  }

  private static void onFx(FxTask task) throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    Throwable[] error = new Throwable[1];
    Platform.runLater(
        () -> {
          try {
            task.run();
          } catch (Throwable t) {
            error[0] = t;
          } finally {
            done.countDown();
          }
        });
    assertTrue("FX task timed out", done.await(10, TimeUnit.SECONDS));
    if (error[0] != null) {
      if (error[0] instanceof AssertionError) {
        throw (AssertionError) error[0];
      }
      fail(error[0].toString());
    }
  }
}

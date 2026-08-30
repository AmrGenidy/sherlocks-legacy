package ui.screens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.BeforeClass;
import org.junit.Test;
import ui.MainController;

/**
 * Headless coverage for the in-game Settings overlay (.scratch/gui-ingame-settings): the trimmed
 * dossier reuses the shared {@link SettingsSections} but drops the font-slider explanation and the
 * language picker, keeps the two sliders + previews, and opens/closes cleanly (Escape returns to
 * the game). Pins the trim contract (#3) and the no-language / no-dead-end requirements.
 */
public class InGameSettingsTest {

  @BeforeClass
  public static void initJfx() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    try {
      Platform.startup(latch::countDown);
    } catch (IllegalStateException alreadyStarted) {
      latch.countDown();
    }
    assertTrue("JavaFX did not start", latch.await(5, TimeUnit.SECONDS));
  }

  @Test
  public void trimmedTextSizeSectionsKeepSliderAndPreviewButDropTheHint() throws Exception {
    onFx(
        () -> {
          SettingsSections sections = new SettingsSections(new MainController());

          VBox full = (VBox) sections.terminalTextSizeSection(true);
          VBox trimmed = (VBox) sections.terminalTextSizeSection(false);

          assertTrue(
              "the main-menu section keeps its explanatory hint", hasChild(full, "settings-hint"));
          assertFalse(
              "the in-game section drops the explanatory paragraph",
              hasChild(trimmed, "settings-hint"));
          // The live preview + slider survive the trim in BOTH.
          assertTrue("slider survives the trim", hasChild(trimmed, "text-size-slider"));
          assertTrue("the live preview card survives the trim", hasChild(trimmed, "panel"));

          // Same for the reading section.
          assertTrue(hasChild((VBox) sections.readingTextSizeSection(true), "settings-hint"));
          assertFalse(hasChild((VBox) sections.readingTextSizeSection(false), "settings-hint"));
        });
  }

  @Test
  public void overlayOpensAsADimmedTrimmedDossierAndEscapeCloses() throws Exception {
    onFx(
        () -> {
          GameScreenController gsc = new GameScreenController(new MainController());
          StackPane container = (StackPane) gsc.getView();
          // A real Scene + CSS so the ScrollPane skin builds and its content is reachable via
          // lookup.
          javafx.scene.Scene scene = new javafx.scene.Scene(container, 1000, 700);
          scene.getStylesheets().add(ui.util.Theme.baseStylesheet());

          gsc.showInGameSettings();
          container.applyCss();
          container.layout();
          assertTrue("the overlay must be showing", gsc.isInGameSettingsShowing());

          // Dimmed veil + the dossier card are present.
          assertEquals("one scrim veil", 1, container.lookupAll(".pause-scrim").size());
          assertEquals(
              "one settings dossier card", 1, container.lookupAll(".settings-card").size());

          // Both font sliders are present; the language picker is NOT (the font-slider explanation
          // trim is pinned by the section-level test above).
          assertEquals(
              "both text-size sliders", 2, container.lookupAll(".text-size-slider").size());
          assertTrue("no language picker in-game", classesUnder(container, "lang-name-").isEmpty());

          // An always-visible corner close icon (so the player never scrolls to dismiss it).
          assertEquals(
              "a top-right engraved close icon",
              1,
              container.lookupAll(".menu-icon-button").size());

          // Opening again is idempotent (no stacked overlays).
          gsc.showInGameSettings();
          container.applyCss();
          assertEquals(1, container.lookupAll(".settings-card").size());

          // Escape steps back to the game with no dead-end.
          assertTrue("Escape consumes and closes the overlay", gsc.onEscape());
          container.applyCss();
          assertFalse("the overlay is dismissed", gsc.isInGameSettingsShowing());
          assertEquals("the card is gone", 0, container.lookupAll(".settings-card").size());
        });
  }

  /** True when a VBox section has a direct child carrying {@code styleClass}. */
  private static boolean hasChild(VBox section, String styleClass) {
    return section.getChildren().stream().anyMatch(n -> n.getStyleClass().contains(styleClass));
  }

  /** Every style class under {@code root} that starts with {@code prefix} (for absence checks). */
  private static java.util.List<String> classesUnder(Node root, String prefix) {
    java.util.List<String> hits = new java.util.ArrayList<>();
    collect(root, prefix, hits);
    return hits;
  }

  private static void collect(Node node, String prefix, java.util.List<String> hits) {
    for (String sc : node.getStyleClass()) {
      if (sc.startsWith(prefix)) {
        hits.add(sc);
      }
    }
    if (node instanceof Parent parent) {
      for (Node child : parent.getChildrenUnmodifiable()) {
        collect(child, prefix, hits);
      }
    }
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

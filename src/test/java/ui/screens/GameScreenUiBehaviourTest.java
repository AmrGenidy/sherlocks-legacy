package ui.screens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import common.dto.DialogueEventDTO;
import common.dto.DialogueType;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.junit.BeforeClass;
import org.junit.Test;
import ui.MainController;
import ui.pinboard.PinboardController;
import ui.util.RoomView;
import ui.windows.JournalWindow;

/**
 * Headless FX coverage for two in-game UI bugs:
 *
 * <ol>
 *   <li>The journal window auto-opened on every {@code examine} (and any other journal-touching
 *       command) once it had been opened a single time — a content refresh must NOT show the
 *       window; only an explicit open may.
 *   <li>The result/statement popup rendered <em>underneath</em> the room sprites — it must render
 *       authoritatively above all room content.
 * </ol>
 */
public class GameScreenUiBehaviourTest {

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

  /** A non-single-player, client-less shell: keeps the journal content-load a no-op in tests. */
  private static MainController headlessShell() {
    return headlessShell(false);
  }

  /** As {@link #headlessShell()}, with control over whether a tutorial reports active. */
  private static MainController headlessShell(boolean tutorialActive) {
    return new MainController() {
      @Override
      public boolean isSinglePlayerMode() {
        return false;
      }

      @Override
      public client.GameClient getGameClient() {
        return null;
      }

      @Override
      public singleplayer.SinglePlayerMain getSinglePlayerGame() {
        return null;
      }

      @Override
      public boolean isTutorialActive() {
        return tutorialActive;
      }
    };
  }

  /** JournalWindow that records show() calls instead of touching a real (display-bound) Stage. */
  private static final class SpyJournalWindow extends JournalWindow {
    int shows = 0;

    SpyJournalWindow(MainController controller) {
      super(controller);
    }

    @Override
    public void show() {
      shows++;
    }

    @Override
    public boolean isShowing() {
      return shows > 0;
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T field(Object target, String name) throws Exception {
    Field f = GameScreenController.class.getDeclaredField(name);
    f.setAccessible(true);
    return (T) f.get(target);
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    Field f = GameScreenController.class.getDeclaredField(name);
    f.setAccessible(true);
    f.set(target, value);
  }

  /** Runs work on the FX thread and surfaces any throwable as a test failure. */
  private static void onFx(FxWork work) throws InterruptedException {
    CountDownLatch done = new CountDownLatch(1);
    Throwable[] error = new Throwable[1];
    Platform.runLater(
        () -> {
          try {
            work.run(done, error);
          } catch (Throwable t) {
            error[0] = t;
            done.countDown();
          }
        });
    assertTrue("FX work timed out", done.await(10, TimeUnit.SECONDS));
    if (error[0] != null) {
      error[0].printStackTrace();
      fail(error[0].toString());
    }
  }

  private interface FxWork {
    void run(CountDownLatch done, Throwable[] error) throws Exception;
  }

  @Test
  public void journalRefreshNeverOpensWindow_butExplicitOpenDoes() throws InterruptedException {
    onFx(
        (done, error) -> {
          MainController shell = headlessShell();
          GameScreenController gsc = new GameScreenController(shell);
          SpyJournalWindow spy = new SpyJournalWindow(shell);
          setField(gsc, "journalWindow", spy);

          // A journal entry from examine/deduce/etc. funnels here. It must refresh contents only.
          gsc.refreshJournalWindow();
          assertEquals("a content refresh must NEVER force the journal window open", 0, spy.shows);

          // Many refreshes still never open it.
          gsc.refreshJournalWindow();
          gsc.refreshJournalWindow();
          assertEquals("repeated refreshes still must not open the window", 0, spy.shows);

          // The explicit open path (Journal button) is the ONLY thing that shows it.
          gsc.openJournalWindow();
          assertEquals("explicit open must show the journal", 1, spy.shows);

          done.countDown();
        });
  }

  @Test
  public void dialogueBubbleRendersAboveRoomContentInSeparatedRegions()
      throws InterruptedException {
    onFx(
        (done, error) -> {
          MainController shell = headlessShell();
          GameScreenController gsc = new GameScreenController(shell);

          // showDialogueBubble defers its build to a runLater; queue the assertions AFTER it.
          gsc.showDialogueBubble(
              new DialogueEventDTO(
                  "Examining: torn_letter", "A hurried hand.", DialogueType.NARRATIVE));

          Platform.runLater(
              () -> {
                try {
                  StackPane container = field(gsc, "container");
                  RoomView roomView = field(gsc, "roomView");

                  List<Node> children = container.getChildren();
                  Node bubble =
                      children.stream()
                          .filter(n -> "dialogueBubble".equals(n.getId()))
                          .findFirst()
                          .orElse(null);

                  assertNotNull("dialogue bubble must be present in the room container", bubble);
                  // Lower viewOrder renders in front: the bubble must beat the RoomView plate.
                  assertTrue(
                      "bubble must render in front of the room (viewOrder < 0)",
                      bubble.getViewOrder() < 0);
                  assertTrue(
                      "bubble viewOrder must be ahead of the RoomView's",
                      bubble.getViewOrder() < roomView.getViewOrder());
                  assertSame(
                      "bubble must also be the top child of the container",
                      bubble,
                      children.get(children.size() - 1));

                  // The popup body is a single VBox of vertically-flowed rows (a VBox cannot
                  // overlap its children) — title, ochre rule, description, close — so no image
                  // ever sits on the sentence. No subject art resolves headlessly (not
                  // single-player), so the image band is omitted (no empty gap).
                  javafx.scene.layout.VBox card = (javafx.scene.layout.VBox) bubble;
                  assertTrue("first row is the title", card.getChildren().get(0) instanceof Label);
                  assertTrue(
                      "second row is the ochre rule",
                      card.getChildren().get(1).getStyleClass().contains("dialogue-bubble-rule"));
                  // The body is a wrapping content label added directly to the card; with no bound
                  // max height the card stretches vertically to fit it
                  // (.scratch/gui-popup-text-wrap)
                  // — no scroll row, no clipping.
                  Label desc =
                      (Label)
                          card.getChildren().stream()
                              .filter(n -> n.getStyleClass().contains("dialogue-bubble-content"))
                              .findFirst()
                              .orElse(null);
                  assertNotNull("description present as a wrapping label below the rule", desc);
                  assertTrue("description wraps, never single-line", desc.isWrapText());
                  assertFalse(
                      "image band must be omitted when there is no subject image",
                      card.getChildren().stream()
                          .anyMatch(n -> n.getStyleClass().contains("dialogue-bubble-image")));
                  // No row may be a StackPane (which would allow image-over-text overlap).
                  assertFalse(
                      "no row may be a StackPane (no z-overlap of image over text)",
                      card.getChildren().stream().anyMatch(n -> n instanceof StackPane));

                  // Normal play: centered plate.
                  assertEquals(Pos.CENTER, StackPane.getAlignment(bubble));
                } catch (Throwable t) {
                  error[0] = t;
                } finally {
                  done.countDown();
                }
              });
        });
  }

  @Test
  public void dialogueBubblePinsToTopBandWhileTutorialActive() throws InterruptedException {
    onFx(
        (done, error) -> {
          MainController shell = headlessShell(true); // tutorial running
          GameScreenController gsc = new GameScreenController(shell);

          gsc.showDialogueBubble(
              new DialogueEventDTO(
                  "Examining: torn_letter", "A hurried hand.", DialogueType.EXAMINE));

          Platform.runLater(
              () -> {
                try {
                  StackPane container = field(gsc, "container");
                  Node bubble =
                      container.getChildren().stream()
                          .filter(n -> "dialogueBubble".equals(n.getId()))
                          .findFirst()
                          .orElse(null);

                  assertNotNull("dialogue bubble must be present", bubble);
                  // While a tutorial is active the popup must sit in the TOP band so it never
                  // collides with the tutorial guidance card pinned to the bottom-center.
                  assertEquals(
                      "popup must pin to the top while a tutorial runs",
                      Pos.TOP_CENTER,
                      StackPane.getAlignment(bubble));
                } catch (Throwable t) {
                  error[0] = t;
                } finally {
                  done.countDown();
                }
              });
        });
  }

  @Test
  public void examinePopupPutsSubjectImageInItsOwnBandAboveTheDescription()
      throws InterruptedException {
    onFx(
        (done, error) -> {
          // A real single-player case so the examined object resolves to an image.
          singleplayer.SinglePlayerMain game = new singleplayer.SinglePlayerMain();
          game.initializeCase(engine.EngineFixtures.sapphire());
          game.processCommand(
              "start case"); // places the player in the Ballroom (has shattered_glass)

          MainController shell =
              new MainController() {
                @Override
                public boolean isSinglePlayerMode() {
                  return true;
                }

                @Override
                public singleplayer.SinglePlayerMain getSinglePlayerGame() {
                  return game;
                }

                @Override
                public boolean isTutorialActive() {
                  return false;
                }

                @Override
                public ui.util.ImageManager getImageManager() {
                  return new ui.util.ImageManager() {
                    @Override
                    public javafx.scene.image.Image getObjectImage(Core.GameObject object) {
                      return new javafx.scene.image.WritableImage(40, 40);
                    }
                  };
                }
              };

          GameScreenController gsc = new GameScreenController(shell);
          gsc.showDialogueBubble(
              new DialogueEventDTO(
                  "Examining: shattered_glass", "Shards of glass.", DialogueType.EXAMINE));

          Platform.runLater(
              () -> {
                try {
                  StackPane container = field(gsc, "container");
                  javafx.scene.layout.VBox card =
                      (javafx.scene.layout.VBox)
                          container.getChildren().stream()
                              .filter(n -> "dialogueBubble".equals(n.getId()))
                              .findFirst()
                              .orElseThrow();

                  int imageIdx = -1;
                  int textIdx = -1;
                  for (int i = 0; i < card.getChildren().size(); i++) {
                    Node n = card.getChildren().get(i);
                    if (n.getStyleClass().contains("dialogue-bubble-image")) imageIdx = i;
                    if (n.getStyleClass().contains("dialogue-bubble-content")) textIdx = i;
                  }

                  assertTrue("examine popup must include the subject image band", imageIdx >= 0);
                  assertTrue("description must be present", textIdx >= 0);
                  assertTrue(
                      "image band must sit in its OWN row ABOVE the description (no overlap)",
                      imageIdx < textIdx);

                  Node band = card.getChildren().get(imageIdx);
                  assertFalse(
                      "image band must not be a StackPane (which could overlay text)",
                      band instanceof StackPane);
                  javafx.scene.image.ImageView iv =
                      (javafx.scene.image.ImageView)
                          ((javafx.scene.layout.HBox) band).getChildren().get(0);
                  assertTrue(
                      "image must be bounded (<=200x160) and preserve ratio",
                      iv.getFitWidth() <= 200 && iv.getFitHeight() <= 160 && iv.isPreserveRatio());
                } catch (Throwable t) {
                  error[0] = t;
                } finally {
                  done.countDown();
                }
              });
        });
  }

  /** A PinboardController that reports open/closed on demand without showing a real window. */
  private static PinboardController spyBoard(boolean showing) {
    return new PinboardController() {
      @Override
      public boolean isShowing() {
        return showing;
      }
    };
  }

  @Test
  public void revealFloatsOverPinboardWhenBoardIsOpen() throws InterruptedException {
    onFx(
        (done, error) -> {
          MainController shell = headlessShell();
          boolean[] floated = {false};
          // Capture the routing without opening a real top-level popup window headlessly.
          GameScreenController gsc =
              new GameScreenController(shell) {
                @Override
                void showRevealOverPinboard(DialogueEventDTO event) {
                  floated[0] = true;
                }
              };
          setField(gsc, "pinboardController", spyBoard(true));

          gsc.showDialogueBubble(
              new DialogueEventDTO(
                  "Contradiction!", "The alibi cannot hold.", DialogueType.CONTRADICTION));

          Platform.runLater(
              () -> {
                try {
                  assertTrue(
                      "with the Pinboard open, the reveal must float above it, not in the main"
                          + " window",
                      floated[0]);
                  StackPane container = field(gsc, "container");
                  assertFalse(
                      "no in-window bubble may be left behind the board when the reveal is routed"
                          + " to the pinboard popup",
                      container.getChildren().stream()
                          .anyMatch(n -> "dialogueBubble".equals(n.getId())));
                } catch (Throwable t) {
                  error[0] = t;
                } finally {
                  done.countDown();
                }
              });
        });
  }

  @Test
  public void revealStaysInMainWindowWhenPinboardClosed() throws InterruptedException {
    onFx(
        (done, error) -> {
          MainController shell = headlessShell();
          boolean[] floated = {false};
          GameScreenController gsc =
              new GameScreenController(shell) {
                @Override
                void showRevealOverPinboard(DialogueEventDTO event) {
                  floated[0] = true;
                }
              };
          setField(gsc, "pinboardController", spyBoard(false)); // board present but not showing

          gsc.showDialogueBubble(
              new DialogueEventDTO(
                  "Contradiction!", "The alibi cannot hold.", DialogueType.CONTRADICTION));

          Platform.runLater(
              () -> {
                try {
                  assertFalse(
                      "with the Pinboard closed, the reveal must NOT float — normal in-window bubble",
                      floated[0]);
                  StackPane container = field(gsc, "container");
                  assertTrue(
                      "the normal in-window bubble must be present when the board is closed",
                      container.getChildren().stream()
                          .anyMatch(n -> "dialogueBubble".equals(n.getId())));
                } catch (Throwable t) {
                  error[0] = t;
                } finally {
                  done.countDown();
                }
              });
        });
  }
}

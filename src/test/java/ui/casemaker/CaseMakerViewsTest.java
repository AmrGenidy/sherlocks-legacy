package ui.casemaker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.image.Image;
import org.junit.BeforeClass;
import org.junit.Test;
import ui.MainController;
import ui.casemaker.model.CaseDraft;
import ui.casemaker.model.RoomDraft;
import ui.casemaker.model.SuspectDraft;

/**
 * Regression coverage for the new-case path (bug: {@code refreshRooms} called {@code
 * contains(null)} on an immutable empty room list and crashed when "Create a Case" opened a fresh
 * editor). Every Case Maker view — and the whole window — must construct cleanly on a brand-new
 * empty {@link CaseDraft}, and survive a room being added afterwards.
 */
public class CaseMakerViewsTest {

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

  /** Runs {@code work} on the FX thread and fails the test if it throws. */
  private void onFxThread(Runnable work) throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    Throwable[] error = new Throwable[1];
    Platform.runLater(
        () -> {
          try {
            work.run();
          } catch (Throwable t) {
            error[0] = t;
          } finally {
            latch.countDown();
          }
        });
    assertTrue("FX work timed out", latch.await(10, TimeUnit.SECONDS));
    if (error[0] != null) {
      error[0].printStackTrace();
      fail("Case Maker view threw on the new-case path: " + error[0]);
    }
  }

  @Test
  public void everyViewConstructsOnAFreshEmptyCase() throws InterruptedException {
    onFxThread(
        () -> {
          CaseDraft draft = new CaseDraft();
          // These constructors call refreshRooms()/refresh() internally — the crash site.
          ObjectPlacementView objects = new ObjectPlacementView(draft);
          SuspectEditorView suspects = new SuspectEditorView(draft);
          SuspectPlacementView placement = new SuspectPlacementView(draft);
          CaseLogicView logic = new CaseLogicView(draft);
          LocalizationView localization = new LocalizationView(draft);

          // Adding the first room must also refresh cleanly (the non-empty branch).
          draft.addRoom("Hall");
          objects.refreshRooms();
          suspects.refreshRooms();
          placement.refreshRooms();
          placement.refresh();
          localization.refresh();
          // touch the views so they are not optimized away
          assertTrue(
              objects != null
                  && suspects != null
                  && placement != null
                  && logic != null
                  && localization != null);
        });
  }

  /**
   * The unified Placement canvas renders every placeable — objects, suspects, and Watson — for the
   * room as a real-image marker at the in-game sprite size (via {@code PlacementMarkers}). An
   * object with no authored image still gets a marker (the deterministic engraving preset), and
   * building + laying out the whole room's markers must not throw.
   */
  @Test
  public void placementRendersAllEntitiesForARoom() throws InterruptedException {
    onFxThread(
        () -> {
          CaseDraft draft = new CaseDraft();
          RoomDraft study = draft.addRoom("Study");
          draft.setStartingRoom(study);
          study.addObject("teacup"); // no imagePath -> preset-fallback resolution + layout
          SuspectDraft carter = draft.addSuspect("JamesCarter");
          carter.setHomeRoom(study);
          SuspectPlacementView placement = new SuspectPlacementView(draft);
          placement.refresh(); // selects Study; builds object + suspect + Watson markers
          assertTrue(placement != null);
        });
  }

  /**
   * Selecting a marker shows four corner rotation grips; a rotation drag rotates the sprite about
   * its centre and updates the model, and one undo entry reverts it (Case Maker rotation feature).
   */
  @Test
  public void rotationGripsAppearRotateTheSpriteAndUndo() throws InterruptedException {
    onFxThread(
        () -> {
          CaseDraft draft = new CaseDraft();
          RoomDraft study = draft.addRoom("Study");
          draft.setStartingRoom(study);
          SuspectDraft carter = draft.addSuspect("JamesCarter");
          carter.setHomeRoom(study);

          SuspectPlacementView placement = new SuspectPlacementView(draft);
          placement.setBackgroundForTest(new javafx.scene.image.WritableImage(100, 100));
          placement.setCanvasSizeForTest(400, 300);
          placement.selectForTest(0); // objects-first ordering: index 0 is the suspect here

          assertEquals(
              "four corner rotation grips appear for the selection",
              4,
              placement.visibleRotateHandleCountForTest());

          placement.applyRotationForTest(30); // as a rotation-grip drag+release does
          assertEquals(30.0, placement.selectedRotationForTest(), 1e-9);
          assertEquals(
              "the sprite node is rotated live",
              30.0,
              placement.selectedSpriteRotateForTest(),
              1e-9);

          placement.undo();
          assertEquals(
              "Ctrl+Z reverts the rotation", 0.0, placement.selectedRotationForTest(), 1e-9);
          assertEquals(0.0, placement.selectedSpriteRotateForTest(), 1e-9);
        });
  }

  /**
   * Regression: the room background in the Placement canvas must survive a refresh/relayout (the
   * kind a marker drag triggers) instead of vanishing. The artwork lives on a cached ImageView
   * layer, so rebuilding the preview keeps the very same loaded image — never reloaded, never
   * dropped.
   */
  @Test
  public void placementCanvasKeepsTheRoomBackgroundThroughRelayouts() throws InterruptedException {
    onFxThread(
        () -> {
          CaseDraft draft = new CaseDraft();
          RoomDraft study = draft.addRoom("Study");
          // A classpath-resolvable room image so tryLoad succeeds headlessly.
          study.setImagePath(ui.util.PresetArtResolver.roomPresetPath("room_study"));
          draft.setStartingRoom(study);

          SuspectPlacementView placement = new SuspectPlacementView(draft);
          Image img = placement.displayedRoomImage();
          assertNotNull("the placement canvas should show the room background", img);
          placement.rebuildCurrentRoomForTest(); // a relayout, as a drag triggers
          assertSame("cached background survives a rebuild", img, placement.displayedRoomImage());
        });
  }

  /**
   * Case Maker dialogs get the app's Victorian theme, not the default system look: the shared
   * {@code themeDialog} helper attaches the app stylesheet (so {@code .dialog-pane}/{@code .button}
   * style it and it flips in dark mode), tags the pane with the app dialog class, and drops the
   * default header graphic (the blue system {@code "?"}).
   */
  @Test
  public void caseMakerDialogsGetTheAppTheme() throws InterruptedException {
    onFxThread(
        () -> {
          javafx.scene.control.Alert alert =
              new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
          javafx.scene.control.DialogPane pane = alert.getDialogPane();
          // (The system "?" graphic is attached lazily by the skin when shown; we assert the helper
          // leaves no graphic, which holds whether or not one was set yet.)
          CaseMakerWindow.themeDialog(pane);

          assertTrue(
              "app stylesheet attached so the dialog is parchment/ink and dark-aware",
              pane.getStylesheets().stream().anyMatch(s -> s.contains("detective-theme.css")));
          assertTrue(
              "tagged with the app dialog class",
              pane.getStyleClass().contains("casemaker-dialog"));
          assertNull("the blue system header graphic is removed", pane.getGraphic());
        });
  }

  /**
   * A sprite marker's selection box hugs the VISIBLE (opaque) figure, not the full transparent-
   * margined image, and only opaque pixels intercept the mouse — so overlapping markers stay
   * individually clickable. {@code opaqueBounds} reports the opaque rectangle as fractions, and the
   * built ImageView has {@code pickOnBounds=false}.
   */
  @Test
  public void spriteOutlineHugsOpaqueBoundsAndPicksOnOpaquePixels() throws InterruptedException {
    onFxThread(
        () -> {
          javafx.scene.image.WritableImage img = new javafx.scene.image.WritableImage(10, 10);
          javafx.scene.image.PixelWriter pw = img.getPixelWriter();
          for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
              // Opaque only in the 4x4 block x,y in [3,6]; everything else transparent.
              boolean opaque = x >= 3 && x <= 6 && y >= 3 && y <= 6;
              pw.setArgb(x, y, opaque ? 0xFF884422 : 0x00000000);
            }
          }

          double[] b = PlacementMarkers.opaqueBounds(img);
          assertEquals("opaque left fraction", 0.3, b[0], 1e-9);
          assertEquals("opaque top fraction", 0.3, b[1], 1e-9);
          assertEquals("opaque width fraction", 0.4, b[2], 1e-9);
          assertEquals("opaque height fraction", 0.4, b[3], 1e-9);

          javafx.scene.layout.StackPane sprite = PlacementMarkers.buildSprite(img);
          PlacementMarkers.MarkerNodes nodes = (PlacementMarkers.MarkerNodes) sprite.getUserData();
          assertFalse(
              "transparent margins must pass clicks through", nodes.sprite().isPickOnBounds());
        });
  }

  /**
   * Ctrl+Z on the Placement editor reverts the last placement edit (position, then scale, then
   * flip) in order, restoring both the CaseDraft model and the canvas; Ctrl+Y/Shift+Z redoes.
   */
  @Test
  public void placementUndoRedoRevertsEditsInOrder() throws InterruptedException {
    onFxThread(
        () -> {
          CaseDraft draft = new CaseDraft();
          RoomDraft study = draft.addRoom("Study");
          draft.setStartingRoom(study);
          SuspectDraft carter = draft.addSuspect("JamesCarter");
          carter.setHomeRoom(study);
          carter.setPosition(0.3, 0.4);
          SuspectPlacementView placement = new SuspectPlacementView(draft);
          placement.refresh();

          placement.selectForTest(0); // the only suspect (no objects; Watson is last)

          // Edit 1: move.
          carter.setPosition(0.7, 0.8);
          placement.commitEditForTest();
          // Edit 2: scale.
          carter.setImageScaleX(2.0);
          carter.setImageScaleY(2.0);
          placement.commitEditForTest();
          // Edit 3: flip.
          carter.setFlipX(true);
          placement.commitEditForTest();

          placement.undo(); // undo flip
          assertFalse("flip reverted", carter.isFlipX());
          assertEquals("scale kept", 2.0, carter.getImageScaleX(), 1e-9);

          placement.undo(); // undo scale
          assertEquals("scale reverted", 1.0, carter.getImageScaleX(), 1e-9);
          assertEquals("position kept", 0.7, carter.getPosX(), 1e-9);

          placement.undo(); // undo move
          assertEquals("position reverted", 0.3, carter.getPosX(), 1e-9);
          assertEquals(0.4, carter.getPosY(), 1e-9);

          placement.redo(); // redo move
          assertEquals("position re-applied", 0.7, carter.getPosX(), 1e-9);
        });
  }

  /**
   * The resize-handle math: a left/right edge midpoint changes only imageScaleX, a top/bottom
   * midpoint only imageScaleY, a corner both, Shift on a corner keeps the aspect ratio (uniform),
   * and Shift on an edge is ignored (stays single-axis).
   */
  @Test
  public void resizeHandleMathScalesTheRightAxes() {
    // Right-edge midpoint (hy==0): widen only. Handle starts 100px right of anchor; drag to 150px.
    double[] rightEdge =
        SuspectPlacementView.resizedScales(1, 0, 1.0, 1.0, 100, 50, 150, 999, false);
    assertEquals(1.5, rightEdge[0], 1e-9);
    assertEquals(1.0, rightEdge[1], 1e-9);

    // Top midpoint (hx==0): heighten only. Handle 80px above; drag to 120px above.
    double[] topEdge =
        SuspectPlacementView.resizedScales(0, -1, 1.0, 1.0, 50, -80, 999, -120, false);
    assertEquals(1.0, topEdge[0], 1e-9);
    assertEquals(1.5, topEdge[1], 1e-9);

    // Corner: both axes independently.
    double[] corner = SuspectPlacementView.resizedScales(1, 1, 1.0, 1.0, 100, 100, 200, 150, false);
    assertEquals(2.0, corner[0], 1e-9);
    assertEquals(1.5, corner[1], 1e-9);

    // Corner + Shift: uniform (keeps aspect ratio).
    double[] cornerShift =
        SuspectPlacementView.resizedScales(1, 1, 1.0, 1.0, 100, 100, 200, 150, true);
    assertEquals(2.0, cornerShift[0], 1e-9);
    assertEquals(2.0, cornerShift[1], 1e-9);

    // Edge + Shift: still single-axis (Shift only matters on corners).
    double[] edgeShift =
        SuspectPlacementView.resizedScales(1, 0, 1.0, 1.0, 100, 50, 150, 999, true);
    assertEquals(1.5, edgeShift[0], 1e-9);
    assertEquals(1.0, edgeShift[1], 1e-9);
  }

  /** Selecting a marker must show the tight selection box + all 8 resize handles. */
  @Test
  public void selectingAMarkerShowsBoxAndEightHandles() throws InterruptedException {
    onFxThread(
        () -> {
          CaseDraft draft = new CaseDraft();
          RoomDraft study = draft.addRoom("Study");
          study.setImagePath(ui.util.PresetArtResolver.roomPresetPath("room_study"));
          draft.setStartingRoom(study);
          SuspectDraft carter = draft.addSuspect("JamesCarter");
          carter.setHomeRoom(study);

          SuspectPlacementView placement = new SuspectPlacementView(draft);
          // Put it in a scene and force layout so the marker layer gets a real size.
          new javafx.scene.Scene(placement, 800, 600);
          placement.applyCss();
          placement.layout();
          placement.refresh();
          placement.applyCss();
          placement.layout();

          placement.selectForTest(0); // the suspect (objects would come first; none here)

          // All 8 grips visible AND sized (unmanaged nodes must be resized, not just relocated).
          assertEquals(
              "all 8 resize handles visible + sized", 8, placement.visibleHandleCountForTest());
          assertTrue("selection box has non-zero size", placement.selectionBoxWidthForTest() > 0);
          assertTrue(
              "selection box shows its selected border",
              placement.selectedOutlineIsSelectedForTest());
        });
  }

  /**
   * Placement math is anchored to the CONTAINED (letterboxed) room-image rect, not the raw pane, so
   * a marker tracks its background feature and its stored posX/posY are unchanged when the canvas
   * aspect changes (windowed ↔ fullscreen).
   */
  @Test
  public void markersTrackTheContainedImageRectAcrossResize() throws InterruptedException {
    onFxThread(
        () -> {
          CaseDraft draft = new CaseDraft();
          RoomDraft study = draft.addRoom("Study");
          draft.setStartingRoom(study);
          SuspectDraft carter = draft.addSuspect("JamesCarter");
          carter.setHomeRoom(study);
          carter.setPosition(0.25, 0.25); // a background feature

          SuspectPlacementView placement = new SuspectPlacementView(draft);
          // 2:1 background image → letterboxes differently at each pane aspect.
          placement.setBackgroundForTest(new javafx.scene.image.WritableImage(200, 100));

          // Pane 800x600: contained rect = 800x400 at (0,100). anchor(0.25,0.25) = (200, 200).
          placement.setCanvasSizeForTest(800, 600);
          double[] a = placement.markerCenterForTest(0);
          assertEquals(200.0, a[0], 0.5);
          assertEquals(200.0, a[1], 0.5);

          // Pane 1600x600 (fullscreen-ish): contained rect = 1200x600 at (200,0).
          // anchor(0.25,0.25) = (200 + 300, 150) = (500, 150) — tracks the feature.
          placement.setCanvasSizeForTest(1600, 600);
          double[] b = placement.markerCenterForTest(0);
          assertEquals(500.0, b[0], 0.5);
          assertEquals(150.0, b[1], 0.5);

          // Stored normalized position is unchanged by the resize.
          assertEquals(0.25, carter.getPosX(), 1e-9);
          assertEquals(0.25, carter.getPosY(), 1e-9);

          // Toggle back to the original size several times: the marker returns to its exact spot
          // each time (the layout pulse re-anchors it at the settled size — no stale drift).
          for (int i = 0; i < 3; i++) {
            placement.setCanvasSizeForTest(800, 600);
            double[] back = placement.markerCenterForTest(0);
            assertEquals(200.0, back[0], 0.5);
            assertEquals(200.0, back[1], 0.5);

            placement.setCanvasSizeForTest(1600, 600);
            double[] full = placement.markerCenterForTest(0);
            assertEquals(500.0, full[0], 0.5);
            assertEquals(150.0, full[1], 0.5);
          }
          assertEquals(0.25, carter.getPosX(), 1e-9); // still unchanged

          // The selection box + handles track the marker across a further resize.
          placement.selectForTest(0);
          placement.setCanvasSizeForTest(1000, 1000);
          assertEquals(
              "handles stay with the marker on resize", 8, placement.visibleHandleCountForTest());
        });
  }

  /**
   * The placement plate is locked to the room image's aspect ratio (maximized within its frame), so
   * the picture fills it consistently at any window size and the composition just scales.
   */
  @Test
  public void plateFitsToRoomImageAspect() {
    // Frame wider than a 2:1 image → limited by height.
    double[] wide = SuspectPlacementView.fittedSize(1600, 600, 200, 100);
    assertEquals(1200.0, wide[0], 1e-9);
    assertEquals(600.0, wide[1], 1e-9);
    // Frame narrower than 2:1 → limited by width.
    double[] narrow = SuspectPlacementView.fittedSize(800, 600, 200, 100);
    assertEquals(800.0, narrow[0], 1e-9);
    assertEquals(400.0, narrow[1], 1e-9);
    // Both preserve the image aspect exactly, so the composition scales without reshaping.
    assertEquals(2.0, wide[0] / wide[1], 1e-9);
    assertEquals(2.0, narrow[0] / narrow[1], 1e-9);
    // No image → fill the frame.
    double[] none = SuspectPlacementView.fittedSize(800, 600, 0, 0);
    assertEquals(800.0, none[0], 1e-9);
    assertEquals(600.0, none[1], 1e-9);
  }

  @Test
  public void caseMakerWindowOpensOnAFreshEmptyCase() throws InterruptedException {
    onFxThread(
        () -> {
          MainController shell = new MainController();
          CaseMakerWindow window = new CaseMakerWindow(shell); // mirrors the "Create a Case" path
          assertTrue(window.getScene() != null);
        });
  }

  /**
   * The unsaved-changes close guard is a snapshot compare: a freshly opened case is clean, and any
   * edit to the shared draft marks it dirty (which is what makes the window X prompt). Exercised
   * without driving the modal dialog.
   */
  @Test
  public void windowTracksUnsavedChangesAgainstItsOpenSnapshot() throws InterruptedException {
    onFxThread(
        () -> {
          CaseDraft draft = new CaseDraft();
          draft.addRoom("Study");
          CaseMakerWindow window = new CaseMakerWindow(new MainController(), draft);
          assertFalse("a freshly opened case has no unsaved changes", window.isDirty());

          draft.addRoom("Kitchen"); // an authoring edit
          assertTrue("editing the draft marks the window dirty", window.isDirty());
        });
  }
}

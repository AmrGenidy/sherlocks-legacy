package ui.casemaker;

import java.io.File;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import ui.casemaker.model.CaseDraft;
import ui.casemaker.model.ObjectDraft;
import ui.casemaker.model.RoomDraft;
import ui.casemaker.model.SuspectDraft;
import ui.i18n.L10n;
import ui.util.RoomViewLayout;

/**
 * The Case Maker's <b>Placement</b> tab — one spatial editor showing every placeable entity for the
 * chosen room together on a single room-background canvas: its Objects, the Suspects whose home
 * room is that room, and Dr. Watson. Each is a draggable marker rendered as its real image at true
 * in-game size (via {@link PlacementMarkers} + {@link RoomViewLayout}), so the author positions and
 * scales them relative to each other in one place.
 *
 * <p>Dragging a marker's sprite updates its normalized {@code posX/posY}; dragging its name-caption
 * handle authors the label offset; selecting any marker shows its size slider in the shared side
 * panel. Everything writes into the same {@link CaseDraft} fields the serializer/loader round-trip,
 * so persistence/export is unchanged. Per-type <em>content</em> editing stays on its own tab
 * (object text on Objects, suspect statements on Suspects); this tab owns only spatial layout.
 *
 * <p>Object vs suspect vs Watson differences (image resolution, base sprite factor, where the
 * scale/ position live) are hidden behind the {@link Placeable} adapter so the canvas treats them
 * uniformly.
 */
public final class SuspectPlacementView extends BorderPane {

  private static final double GAP = 8;
  private static final double PAD = 16;
  private static final double DEFAULT_OBJECT_X = 0.5;
  private static final double DEFAULT_OBJECT_Y = 0.5;
  private static final double DEFAULT_SUSPECT_X = 0.5;
  private static final double DEFAULT_SUSPECT_Y = 0.55;
  // Default Watson spot for a room that has not authored one yet. Kept in sync with RoomView's
  // WATSON_DEFAULT_* so an unplaced room previews Watson exactly where he'll render in-game.
  private static final double WATSON_DEFAULT_X = 0.85;
  private static final double WATSON_DEFAULT_Y = 0.6;
  private static final double HANDLE_SIZE = 10; // px, the resize grips on the selection box
  private static final double ROTATE_HANDLE_SIZE = 12; // px, the rotation grips at the corners
  // How far outside each corner the rotation grip sits (px, before rotation).
  private static final double ROTATE_HANDLE_MARGIN = 14;
  private static final double ROTATION_SNAP_DEGREES = 15; // Shift-drag snaps to this step
  private static final double MIN_SCALE = 0.1;
  private static final double MAX_SCALE = 6.0;

  private final CaseDraft draft;

  private final javafx.scene.control.ComboBox<RoomDraft> roomPicker =
      new javafx.scene.control.ComboBox<>();
  private final ListView<Placeable> entityList = new ListView<>();

  private final Slider sizeSlider = new Slider(0.25, 3.0, 1.0);
  private final javafx.scene.control.ToggleButton flipXToggle =
      new javafx.scene.control.ToggleButton(L10n.t("casemaker.placement.flipX"));
  private final javafx.scene.control.ToggleButton flipYToggle =
      new javafx.scene.control.ToggleButton(L10n.t("casemaker.placement.flipY"));
  private final VBox sizePanel = new VBox(GAP);
  // Resize handles for the selected marker (8: 4 corners + 4 sides), drawn on the marker layer.
  private final List<Handle> handles = new ArrayList<>();
  // Rotation grips (4: one just outside each corner of the selection box).
  private final List<RotateHandle> rotateHandles = new ArrayList<>();

  // Preview.
  private final Region previewBackground = new Region(); // vellum mat behind the artwork
  private final ImageView roomImageView = new ImageView(); // the room artwork, contained + centered
  // A custom Pane whose layoutChildren() repositions every marker (sprite + caption + resize
  // handles) against the CURRENT contained-image rect on every settled layout pass — including
  // after
  // a fullscreen toggle, when width/height change listeners alone don't reliably fire at the final
  // size and markers would otherwise render at stale pixels until a rebuild.
  private final Pane markerLayer =
      new Pane() {
        @Override
        protected void layoutChildren() {
          super.layoutChildren();
          relayoutAll();
        }
      };
  private final Label positionLabel = new Label();
  // The image+marker plate is locked to the room image's aspect ratio and centered in this frame,
  // so
  // the contained rect ≈ the whole plate (letterboxing stays minimal + constant across window
  // sizes).
  private StackPane plate;
  private StackPane plateFrame;
  // Every placeable draws as two independently-draggable nodes: a sprite + a name caption.
  private final Map<Placeable, StackPane> spriteBy = new IdentityHashMap<>();
  private final Map<Placeable, Label> captionBy = new IdentityHashMap<>();
  // Cached room artwork: the loaded Image + the path it came from. Reloaded ONLY when the room's
  // imagePath changes (never on a marker move) and shown on its own ImageView layer, so a
  // CSS/layout
  // pass can't drop it — the old Region-background approach vanished on drag.
  private Image backgroundImage; // natural-size image (also feeds the RoomViewLayout math)
  private String backgroundPath; // the imagePath backgroundImage was loaded from (cache key)

  private Placeable selected;
  private boolean binding; // guards control listeners during programmatic population

  // Undo/redo of placement edits (position, scale, flip, label offset). Each completed edit pushes
  // one entry; the pending* fields hold the state at the START of the in-progress edit so a
  // drag/slider/toggle commits a single reversible entry.
  private final Deque<PlacementEdit> undoStack = new ArrayDeque<>();
  private final Deque<PlacementEdit> redoStack = new ArrayDeque<>();
  private PlacementSnapshot pendingBefore;
  private Placeable pendingTarget;
  private boolean restoring; // guards commits while an undo/redo re-applies a snapshot

  public SuspectPlacementView(CaseDraft draft) {
    this.draft = draft;
    setLeft(buildSidePanel());
    setCenter(buildPreview());
    setPadding(new Insets(0, PAD, 0, 0));
    createHandles();
    createRotateHandles();
    refreshRooms();
  }

  // ---- Side panel: room picker, entity list, size slider -----------------------------------

  private Region buildSidePanel() {
    Label roomLabel = label("casemaker.objects.room");
    roomPicker.setConverter(
        new StringConverter<>() {
          @Override
          public String toString(RoomDraft room) {
            return room == null ? "" : room.getName();
          }

          @Override
          public RoomDraft fromString(String s) {
            return null;
          }
        });
    roomPicker.setMaxWidth(Double.MAX_VALUE);
    roomPicker
        .getSelectionModel()
        .selectedItemProperty()
        .addListener((obs, old, room) -> loadRoom(room));

    Label heading = section("casemaker.placement.heading");
    entityList.setCellFactory(list -> entityCell());
    VBox.setVgrow(entityList, Priority.ALWAYS);
    entityList
        .getSelectionModel()
        .selectedItemProperty()
        .addListener((obs, old, placeable) -> selectPlaceable(placeable));

    buildSizePanel();

    Label note = new Label(L10n.t("casemaker.placement.watsonNote"));
    note.getStyleClass().add("casemaker-hint");
    note.setWrapText(true);

    VBox panel = new VBox(GAP, roomLabel, roomPicker, heading, entityList, sizePanel, note);
    panel.getStyleClass().add("panel");
    panel.setPadding(new Insets(PAD));
    panel.setPrefWidth(360);
    return panel;
  }

  private void buildSizePanel() {
    sizeSlider.setShowTickMarks(true);
    sizeSlider
        .valueProperty()
        .addListener(
            (obs, old, val) -> {
              if (!binding && selected != null) {
                // The slider is a uniform resize (both axes); the handles resize independently.
                selected.setScaleX(val.doubleValue());
                selected.setScaleY(val.doubleValue());
                layoutMarker(selected);
                positionHandles();
                // A drag commits once when it ends (valueChanging); a click/keypress commits now.
                if (!sizeSlider.isValueChanging()) {
                  commitEdit();
                }
              }
            });
    sizeSlider
        .valueChangingProperty()
        .addListener(
            (obs, was, now) -> {
              if (was && !now) {
                commitEdit(); // slider drag ended
              }
            });
    flipXToggle
        .selectedProperty()
        .addListener(
            (obs, old, val) -> {
              if (!binding && selected != null) {
                selected.setFlipX(val);
                layoutMarker(selected);
                commitEdit();
              }
            });
    flipYToggle
        .selectedProperty()
        .addListener(
            (obs, old, val) -> {
              if (!binding && selected != null) {
                selected.setFlipY(val);
                layoutMarker(selected);
                commitEdit();
              }
            });
    HBox flipRow = new HBox(GAP, flipXToggle, flipYToggle);
    sizePanel
        .getChildren()
        .setAll(
            label("casemaker.suspects.size"),
            sizeSlider,
            label("casemaker.placement.flip"),
            flipRow);
    sizePanel.setVisible(false);
    sizePanel.setManaged(false);
  }

  // ---- Preview -----------------------------------------------------------------------------

  private Region buildPreview() {
    previewBackground.getStyleClass().add("casemaker-preview");
    markerLayer.getStyleClass().add("casemaker-preview-markers");
    // Repositioning is driven by markerLayer.layoutChildren() (see the field) — the layout pulse is
    // the source of truth, so markers are re-anchored at the settled size on every
    // resize/fullscreen
    // change, not just on intermediate width/height listener firings.

    // Room artwork on its own ImageView layer (fills the aspect-locked plate — so no letterbox
    // inside the plate) between the vellum mat and the markers. CSS can't reset an ImageView's
    // image
    // the way it wiped the Region background on drag.
    roomImageView.setPreserveRatio(true);
    roomImageView.fitWidthProperty().bind(markerLayer.widthProperty());
    roomImageView.fitHeightProperty().bind(markerLayer.heightProperty());
    plate = new StackPane(previewBackground, roomImageView, markerLayer);
    plate.getStyleClass().add("casemaker-graph-frame");

    // Lock the plate to the room image's aspect ratio and centre it in this frame, so the picture
    // fills the plate consistently at any window size and fraction-positioned sprites never spill
    // into the mat. fitPlate() re-computes on frame resize (windowed↔fullscreen) and image change.
    plateFrame = new StackPane(plate);
    plateFrame.getStyleClass().add("casemaker-plate-frame");
    plateFrame.setMinSize(320, 240);
    plateFrame.widthProperty().addListener((obs, old, w) -> fitPlate());
    plateFrame.heightProperty().addListener((obs, old, h) -> fitPlate());
    VBox.setVgrow(plateFrame, Priority.ALWAYS);

    Label hint = new Label(L10n.t("casemaker.placement.dragHint"));
    hint.getStyleClass().add("casemaker-hint");
    positionLabel.getStyleClass().add("casemaker-hint");
    Button roomImage = new Button(L10n.t("casemaker.placement.roomImage"));
    roomImage.setOnAction(e -> chooseRoomImage());
    HBox toolbar = new HBox(GAP, hint, spacer(), positionLabel, roomImage);
    toolbar.setAlignment(Pos.CENTER_LEFT);

    VBox area = new VBox(GAP, toolbar, plateFrame);
    area.setFillWidth(true);
    VBox.setVgrow(plateFrame, Priority.ALWAYS);
    return area;
  }

  /**
   * Sizes the plate so its CONTENT area (the marker layer) has the room image's aspect ratio,
   * maximized + centred within its frame. The plate's CSS border/padding insets are added back so
   * they don't reintroduce a letterbox — the contained rect then equals the whole marker layer.
   */
  private void fitPlate() {
    if (plate == null || plateFrame == null) {
      return;
    }
    double iw = backgroundImage != null ? backgroundImage.getWidth() : 0;
    double ih = backgroundImage != null ? backgroundImage.getHeight() : 0;
    javafx.geometry.Insets in = plate.getInsets();
    double hInset = in.getLeft() + in.getRight();
    double vInset = in.getTop() + in.getBottom();
    double[] content =
        fittedSize(plateFrame.getWidth() - hInset, plateFrame.getHeight() - vInset, iw, ih);
    if (content[0] <= 0 || content[1] <= 0) {
      return;
    }
    double w = content[0] + hInset;
    double h = content[1] + vInset;
    plate.setMinSize(w, h);
    plate.setPrefSize(w, h);
    plate.setMaxSize(w, h);
  }

  /**
   * The largest {@code (w, h)} with the image's aspect ratio that fits inside {@code
   * frameW×frameH}. With no image, fills the frame. Pure, so the aspect-lock is unit-testable.
   */
  static double[] fittedSize(double frameW, double frameH, double imageW, double imageH) {
    if (frameW <= 0 || frameH <= 0) {
      return new double[] {0, 0};
    }
    if (imageW <= 0 || imageH <= 0) {
      return new double[] {frameW, frameH};
    }
    double aspect = imageW / imageH;
    if (frameW / frameH > aspect) {
      return new double[] {frameH * aspect, frameH}; // frame wider than image → limit by height
    }
    return new double[] {frameW, frameW / aspect}; // frame taller → limit by width
  }

  // ---- Room / entity lifecycle -------------------------------------------------------------

  /**
   * Re-syncs the room picker with the draft's rooms (call when rooms are added/renamed/removed).
   */
  public void refreshRooms() {
    RoomDraft previous = roomPicker.getSelectionModel().getSelectedItem();
    roomPicker.getItems().setAll(draft.getRooms());
    if (previous != null && draft.getRooms().contains(previous)) {
      roomPicker.getSelectionModel().select(previous);
    } else if (!draft.getRooms().isEmpty()) {
      roomPicker.getSelectionModel().selectFirst();
    } else {
      loadRoom(null); // fresh case: no rooms yet
    }
  }

  /**
   * Re-reads the current room (its objects, suspects, images, scales). Call when the tab is shown,
   * since content authored on the Objects/Suspects tabs (new objects, home rooms, portraits) may
   * have changed.
   */
  public void refresh() {
    refreshRooms();
    loadRoom(currentRoom());
  }

  /** Every placeable in the room, in canvas order: objects, then suspects, then Dr. Watson. */
  private List<Placeable> placeablesIn(RoomDraft room) {
    List<Placeable> here = new ArrayList<>();
    if (room == null) {
      return here;
    }
    for (ObjectDraft object : room.getObjects()) {
      here.add(new ObjectPlaceable(object, room));
    }
    for (SuspectDraft suspect : draft.getSuspects()) {
      if (suspect.getHomeRoom() == room) {
        here.add(new SuspectPlaceable(suspect));
      }
    }
    here.add(new WatsonPlaceable(room));
    return here;
  }

  private void loadRoom(RoomDraft room) {
    List<Placeable> placeables = placeablesIn(room);
    entityList.getItems().setAll(placeables);
    selectPlaceable(null);
    rebuildPreview(room, placeables);
  }

  private RoomDraft currentRoom() {
    return roomPicker.getSelectionModel().getSelectedItem();
  }

  private void selectPlaceable(Placeable placeable) {
    selected = placeable;
    // Baseline for the next edit's undo entry (the state before any drag/slider/toggle begins).
    pendingTarget = placeable;
    pendingBefore = placeable != null ? capture(placeable) : null;
    if (placeable != null) {
      binding = true;
      sizeSlider.setValue(placeable.scaleX()); // representative; handles resize each axis
      flipXToggle.setSelected(placeable.flipX());
      flipYToggle.setSelected(placeable.flipY());
      binding = false;
      if (entityList.getSelectionModel().getSelectedItem() != placeable) {
        entityList.getSelectionModel().select(placeable);
      }
    }
    sizePanel.setVisible(placeable != null);
    sizePanel.setManaged(placeable != null);
    refreshMarkerStyles();
    refreshPositionLabel();
    // Freshly size the selected marker's sprite + selection box against the CURRENT canvas rect
    // (its outline pref may be stale/zero from an earlier 0-size layout), then place the handles.
    // layoutMarker positions the handles for the selected element; hide them when deselecting.
    if (placeable != null) {
      layoutMarker(placeable);
    } else {
      positionHandles();
    }
  }

  // ---- Markers -----------------------------------------------------------------------------

  private void rebuildPreview(RoomDraft room, List<Placeable> placeables) {
    markerLayer.getChildren().clear();
    spriteBy.clear();
    captionBy.clear();
    showBackground(room);
    if (room == null) {
      return;
    }
    for (Placeable placeable : placeables) {
      placeable.ensurePlaced();
      addMarker(placeable);
    }
    // Handles sit above the markers; re-add after the clear and hide until something's selected.
    for (Handle handle : handles) {
      markerLayer.getChildren().add(handle.node);
    }
    for (RotateHandle grip : rotateHandles) {
      markerLayer.getChildren().add(grip.node);
    }
    positionHandles();
  }

  private void addMarker(Placeable placeable) {
    StackPane sprite = PlacementMarkers.buildSprite(placeable.image());
    Label caption = PlacementMarkers.buildCaption(placeable.displayName());
    if (placeable.isWatson()) {
      sprite.getStyleClass().add("watson");
      caption.getStyleClass().add("watson");
    }
    sprite.setOnMousePressed(e -> selectFromMarker(placeable, e));
    sprite.setOnMouseDragged(e -> dragSprite(placeable, e.getX(), e.getY()));
    sprite.setOnMouseReleased(e -> commitEdit()); // one undo entry per drag
    caption.setOnMousePressed(e -> selectFromMarker(placeable, e));
    caption.setOnMouseDragged(e -> dragCaption(placeable, e.getX(), e.getY()));
    caption.setOnMouseReleased(e -> commitEdit());

    spriteBy.put(placeable, sprite);
    captionBy.put(placeable, caption);
    markerLayer.getChildren().addAll(sprite, caption);
    layoutMarker(placeable);
  }

  private void selectFromMarker(Placeable placeable, javafx.scene.input.MouseEvent e) {
    entityList.getSelectionModel().select(placeable);
    e.consume();
  }

  // ---- Dragging: sprite moves the element; caption authors the label offset ----------------

  private void dragSprite(Placeable placeable, double localX, double localY) {
    StackPane sprite = spriteBy.get(placeable);
    RoomViewLayout.Rect rect = currentRect();
    if (sprite == null || rect.width() <= 0 || rect.height() <= 0) {
      return;
    }
    double centerX = sprite.getLayoutX() + localX;
    double centerY = sprite.getLayoutY() + localY;
    placeable.setPosition(
        (centerX - rect.x()) / rect.width(), (centerY - rect.y()) / rect.height());
    layoutMarker(placeable); // caption stays attached (recomputed from the new sprite anchor)
    if (placeable == selected) {
      refreshPositionLabel();
    }
  }

  private void dragCaption(Placeable placeable, double localX, double localY) {
    Label caption = captionBy.get(placeable);
    RoomViewLayout.Rect rect = currentRect();
    if (caption == null || placeable.posX() == null || rect.height() <= 0) {
      return;
    }
    double anchorX = RoomViewLayout.anchorX(rect, placeable.posX());
    double anchorY = RoomViewLayout.anchorY(rect, placeable.posY());
    double spriteH =
        RoomViewLayout.spriteHeight(rect.height(), placeable.baseFactor(), 1.0)
            * placeable.scaleY();
    if (spriteH <= 0) {
      return;
    }
    double centerX = caption.getLayoutX() + localX;
    double centerY = caption.getLayoutY() + localY;
    placeable.setLabelOffset((centerX - anchorX) / spriteH, (centerY - anchorY) / spriteH);
    PlacementMarkers.layoutCaption(
        caption, anchorX, anchorY, spriteH, placeable.labelDX(), placeable.labelDY());
  }

  // ---- Layout: size + place each element's sprite and caption ------------------------------

  private void layoutMarker(Placeable placeable) {
    StackPane sprite = spriteBy.get(placeable);
    Label caption = captionBy.get(placeable);
    if (sprite == null || placeable.posX() == null) {
      return;
    }
    RoomViewLayout.Rect rect = currentRect();
    double anchorX = RoomViewLayout.anchorX(rect, placeable.posX());
    double anchorY = RoomViewLayout.anchorY(rect, placeable.posY());
    double baseH = RoomViewLayout.spriteHeight(rect.height(), placeable.baseFactor(), 1.0);
    PlacementMarkers.sizeSprite(
        sprite,
        baseH,
        placeable.scaleX(),
        placeable.scaleY(),
        placeable.flipX(),
        placeable.flipY());
    PlacementMarkers.centerNode(sprite, anchorX, anchorY);
    // Rotate the whole sprite (image + selection outline) about its centre = the anchor. The
    // caption
    // is a separate node and stays upright, exactly as RoomView renders it in-game.
    sprite.setRotate(placeable.rotation());
    // Caption offset is a fraction of the rendered (vertical) sprite height, matching RoomView.
    double spriteH = baseH * placeable.scaleY();
    PlacementMarkers.layoutCaption(
        caption, anchorX, anchorY, spriteH, placeable.labelDX(), placeable.labelDY());
    if (placeable == selected) {
      positionHandles();
    }
  }

  private void relayoutAll() {
    for (Placeable placeable : spriteBy.keySet()) {
      layoutMarker(placeable);
    }
    positionHandles();
  }

  private void refreshMarkerStyles() {
    spriteBy.forEach(
        (placeable, sprite) -> {
          boolean sel = placeable == selected;
          PlacementMarkers.setSpriteSelected(sprite, sel); // outline hugs the opaque figure
          setCaptionSelected(captionBy.get(placeable), sel);
        });
  }

  private static void setCaptionSelected(Label caption, boolean selected) {
    if (caption == null) {
      return;
    }
    caption.getStyleClass().remove("selected");
    if (selected) {
      caption.getStyleClass().add("selected");
    }
  }

  private void refreshPositionLabel() {
    if (selected == null || selected.posX() == null) {
      positionLabel.setText("");
      return;
    }
    positionLabel.setText(
        L10n.t(
            "casemaker.objects.position",
            Math.round(selected.posX() * 100),
            Math.round(selected.posY() * 100)));
  }

  /**
   * The rendered-artwork rectangle inside the preview, per the real RoomViewLayout contain math.
   */
  private RoomViewLayout.Rect currentRect() {
    return PlacementMarkers.rect(markerLayer, backgroundImage);
  }

  // ---- Undo / redo -------------------------------------------------------------------------

  /** A full placement state of one entity, so an edit can be reverted or re-applied wholesale. */
  private record PlacementSnapshot(
      Double posX,
      Double posY,
      double scaleX,
      double scaleY,
      boolean flipX,
      boolean flipY,
      double rotation,
      Double labelDX,
      Double labelDY) {}

  /** A reversible placement edit: restore {@code before} to undo, {@code after} to redo. */
  private record PlacementEdit(
      Placeable target, PlacementSnapshot before, PlacementSnapshot after) {}

  private static PlacementSnapshot capture(Placeable p) {
    return new PlacementSnapshot(
        p.posX(),
        p.posY(),
        p.scaleX(),
        p.scaleY(),
        p.flipX(),
        p.flipY(),
        p.rotation(),
        p.labelDX(),
        p.labelDY());
  }

  private static void applySnapshot(Placeable p, PlacementSnapshot s) {
    if (s.posX() != null && s.posY() != null) {
      p.setPosition(s.posX(), s.posY());
    }
    p.setScaleX(s.scaleX());
    p.setScaleY(s.scaleY());
    p.setFlipX(s.flipX());
    p.setFlipY(s.flipY());
    p.setRotation(s.rotation());
    if (s.labelDX() != null && s.labelDY() != null) {
      p.setLabelOffset(s.labelDX(), s.labelDY());
    } else {
      p.clearLabelOffset();
    }
  }

  /** Ends the in-progress edit: if the target actually changed, push a single undo entry. */
  private void commitEdit() {
    if (restoring || pendingTarget == null || pendingBefore == null) {
      return;
    }
    PlacementSnapshot after = capture(pendingTarget);
    if (!after.equals(pendingBefore)) {
      undoStack.push(new PlacementEdit(pendingTarget, pendingBefore, after));
      redoStack.clear();
      pendingBefore = after;
    }
  }

  /** Reverts the last placement edit (Ctrl+Z). */
  public void undo() {
    if (undoStack.isEmpty()) {
      return;
    }
    PlacementEdit edit = undoStack.pop();
    applySnapshot(edit.target(), edit.before());
    redoStack.push(edit);
    showTargetAfterHistory(edit.target());
  }

  /** Re-applies the last undone placement edit (Ctrl+Y / Ctrl+Shift+Z). */
  public void redo() {
    if (redoStack.isEmpty()) {
      return;
    }
    PlacementEdit edit = redoStack.pop();
    applySnapshot(edit.target(), edit.after());
    undoStack.push(edit);
    showTargetAfterHistory(edit.target());
  }

  /** After an undo/redo, navigate to the edited entity's room, rebuild, and reselect it. */
  private void showTargetAfterHistory(Placeable target) {
    restoring = true;
    try {
      RoomDraft room = target.room();
      if (room != null && currentRoom() != room) {
        roomPicker.getSelectionModel().select(room); // fires loadRoom
      } else {
        loadRoom(currentRoom()); // same room: rebuild from the restored model
      }
      for (Placeable p : entityList.getItems()) {
        if (p.sameEntityAs(target)) {
          entityList.getSelectionModel().select(p); // reselect + re-baseline pendingBefore
          break;
        }
      }
    } finally {
      restoring = false;
    }
  }

  // ---- Resize handles ----------------------------------------------------------------------
  // Eight grips on the selected marker's (tight, opaque-bounds) selection box: corners resize both
  // axes, sides resize one. Dragging scales about the sprite anchor (the box scales with it), so a
  // handle's distance from the anchor is proportional to that axis's scale.

  private void createHandles() {
    int[][] units = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};
    for (int[] u : units) {
      handles.add(new Handle(u[0], u[1]));
    }
  }

  private void createRotateHandles() {
    int[][] corners = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
    for (int[] c : corners) {
      rotateHandles.add(new RotateHandle(c[0], c[1]));
    }
  }

  /**
   * Places (and shows) the resize grips + the four rotation grips around the selected marker's
   * opaque box; hides them otherwise. When the sprite is rotated, every grip is rotated about the
   * sprite's centre (the anchor) so the whole selection frame tracks the rotated sprite.
   */
  private void positionHandles() {
    StackPane sprite = selected == null ? null : spriteBy.get(selected);
    if (sprite == null
        || selected.posX() == null
        || !(sprite.getUserData() instanceof PlacementMarkers.MarkerNodes nodes)) {
      handles.forEach(h -> h.node.setVisible(false));
      rotateHandles.forEach(g -> g.node.setVisible(false));
      return;
    }
    RoomViewLayout.Rect rect = currentRect();
    double anchorX = RoomViewLayout.anchorX(rect, selected.posX());
    double anchorY = RoomViewLayout.anchorY(rect, selected.posY());
    Region outline = nodes.outline();
    double halfW;
    double halfH;
    double cx;
    double cy;
    if (outline.getPrefWidth() > 0 && outline.getPrefHeight() > 0) {
      halfW = outline.getPrefWidth() / 2;
      halfH = outline.getPrefHeight() / 2;
      cx = anchorX + outline.getTranslateX(); // opaque-box centre in layer coords
      cy = anchorY + outline.getTranslateY();
    } else {
      // Opaque box unavailable/empty: frame the sprite's whole rendered bounds instead.
      double sw = sprite.getWidth() > 0 ? sprite.getWidth() : sprite.prefWidth(-1);
      double sh = sprite.getHeight() > 0 ? sprite.getHeight() : sprite.prefHeight(-1);
      halfW = sw / 2;
      halfH = sh / 2;
      cx = anchorX;
      cy = anchorY;
    }
    double theta = Math.toRadians(selected.rotation());
    double cos = Math.cos(theta);
    double sin = Math.sin(theta);
    for (Handle h : handles) {
      double[] p = rotateAbout(cx + h.hx * halfW, cy + h.hy * halfH, anchorX, anchorY, cos, sin);
      h.node.setVisible(true);
      // The grips are unmanaged, so the parent never lays them out — size them explicitly here
      // (relocate alone leaves an unmanaged node at 0×0 and thus invisible).
      h.node.resizeRelocate(
          p[0] - HANDLE_SIZE / 2, p[1] - HANDLE_SIZE / 2, HANDLE_SIZE, HANDLE_SIZE);
      h.node.setRotate(selected.rotation()); // align the square with the rotated box
      h.node.toFront();
    }
    for (RotateHandle g : rotateHandles) {
      // Just outside the corner along both axes, then rotated about the sprite centre.
      double bx = cx + g.hx * (halfW + ROTATE_HANDLE_MARGIN);
      double by = cy + g.hy * (halfH + ROTATE_HANDLE_MARGIN);
      double[] p = rotateAbout(bx, by, anchorX, anchorY, cos, sin);
      g.node.setVisible(true);
      g.node.resizeRelocate(
          p[0] - ROTATE_HANDLE_SIZE / 2,
          p[1] - ROTATE_HANDLE_SIZE / 2,
          ROTATE_HANDLE_SIZE,
          ROTATE_HANDLE_SIZE);
      g.node.toFront();
    }
  }

  /**
   * Rotates point {@code (px,py)} about pivot {@code (cx,cy)} by the angle whose cos/sin are given.
   */
  private static double[] rotateAbout(
      double px, double py, double cx, double cy, double cos, double sin) {
    double vx = px - cx;
    double vy = py - cy;
    return new double[] {cx + vx * cos - vy * sin, cy + vx * sin + vy * cos};
  }

  private static double clampScale(double s) {
    return Math.max(MIN_SCALE, Math.min(MAX_SCALE, s));
  }

  /**
   * One resize grip: {@code hx,hy} in {-1,0,1} mark its position on the box and which axes it
   * drives.
   */
  private final class Handle {
    private final Region node = new Region();
    private final int hx;
    private final int hy;
    // Drag start state (captured on press), in layer coordinates relative to the sprite anchor.
    private double startScaleX;
    private double startScaleY;
    private double anchorX;
    private double anchorY;
    private double off0X;
    private double off0Y;

    Handle(int hx, int hy) {
      this.hx = hx;
      this.hy = hy;
      node.getStyleClass().add("casemaker-resize-handle");
      node.setPrefSize(HANDLE_SIZE, HANDLE_SIZE);
      node.setMinSize(HANDLE_SIZE, HANDLE_SIZE);
      node.setMaxSize(HANDLE_SIZE, HANDLE_SIZE);
      node.setManaged(false);
      node.setVisible(false);
      node.setCursor(cursor());
      node.setOnMousePressed(this::press);
      node.setOnMouseDragged(this::drag);
      node.setOnMouseReleased(e -> commitEdit()); // one undo entry per resize
    }

    private javafx.scene.Cursor cursor() {
      if (hx == 0) {
        return javafx.scene.Cursor.V_RESIZE;
      }
      if (hy == 0) {
        return javafx.scene.Cursor.H_RESIZE;
      }
      if (hx == hy) {
        return hx < 0 ? javafx.scene.Cursor.NW_RESIZE : javafx.scene.Cursor.SE_RESIZE;
      }
      return hx < 0 ? javafx.scene.Cursor.SW_RESIZE : javafx.scene.Cursor.NE_RESIZE;
    }

    private void press(javafx.scene.input.MouseEvent e) {
      if (selected == null) {
        return;
      }
      RoomViewLayout.Rect rect = currentRect();
      anchorX = RoomViewLayout.anchorX(rect, selected.posX());
      anchorY = RoomViewLayout.anchorY(rect, selected.posY());
      startScaleX = selected.scaleX();
      startScaleY = selected.scaleY();
      javafx.geometry.Point2D p = markerLayer.sceneToLocal(e.getSceneX(), e.getSceneY());
      off0X = p.getX() - anchorX;
      off0Y = p.getY() - anchorY;
      e.consume();
    }

    private void drag(javafx.scene.input.MouseEvent e) {
      if (selected == null) {
        return;
      }
      javafx.geometry.Point2D p = markerLayer.sceneToLocal(e.getSceneX(), e.getSceneY());
      double[] scales =
          resizedScales(
              hx,
              hy,
              startScaleX,
              startScaleY,
              off0X,
              off0Y,
              p.getX() - anchorX,
              p.getY() - anchorY,
              e.isShiftDown());
      selected.setScaleX(scales[0]);
      selected.setScaleY(scales[1]);
      binding = true;
      sizeSlider.setValue(scales[0]);
      binding = false;
      layoutMarker(selected);
      e.consume();
    }
  }

  /**
   * Pure resize math: from the drag-start scales and the handle's start/current offsets from the
   * sprite anchor, returns the new {@code [scaleX, scaleY]}. A left/right midpoint ({@code hy==0})
   * changes only X; a top/bottom midpoint ({@code hx==0}) only Y; a corner changes both, and Shift
   * on a corner keeps the aspect ratio (uniform). Each handle's distance from the anchor along an
   * axis is proportional to that axis's scale, so the new scale is {@code startScale *
   * newOffset/startOffset}.
   */
  static double[] resizedScales(
      int hx,
      int hy,
      double startScaleX,
      double startScaleY,
      double off0X,
      double off0Y,
      double newX,
      double newY,
      boolean shift) {
    double sx = startScaleX;
    double sy = startScaleY;
    if (hx != 0 && Math.abs(off0X) > 1e-3) {
      sx = clampScale(startScaleX * newX / off0X);
    }
    if (hy != 0 && Math.abs(off0Y) > 1e-3) {
      sy = clampScale(startScaleY * newY / off0Y);
    }
    if (shift && hx != 0 && hy != 0) { // corner + Shift → uniform (keep aspect ratio)
      double uni = Math.max(sx, sy);
      sx = uni;
      sy = uni;
    }
    return new double[] {sx, sy};
  }

  /**
   * One of four rotation grips, just outside a corner of the selection box. Dragging it rotates the
   * sprite about its centre: the sprite's new angle follows the pointer's angle around the centre,
   * offset by the pointer's angle at grab so the sprite doesn't jump. Shift snaps to 15° steps. One
   * undo entry is committed per rotation drag (on release).
   */
  private final class RotateHandle {
    private final Region node = new Region();
    private final int hx;
    private final int hy;
    // Drag start state (captured on press), in layer coordinates.
    private double pivotX;
    private double pivotY;
    private double startRotation;
    private double pressAngle; // degrees, pointer angle around the pivot at press

    RotateHandle(int hx, int hy) {
      this.hx = hx;
      this.hy = hy;
      node.getStyleClass().add("casemaker-rotate-handle");
      node.setPrefSize(ROTATE_HANDLE_SIZE, ROTATE_HANDLE_SIZE);
      node.setMinSize(ROTATE_HANDLE_SIZE, ROTATE_HANDLE_SIZE);
      node.setMaxSize(ROTATE_HANDLE_SIZE, ROTATE_HANDLE_SIZE);
      node.setManaged(false);
      node.setVisible(false);
      node.setCursor(javafx.scene.Cursor.CROSSHAIR);
      node.setOnMousePressed(this::press);
      node.setOnMouseDragged(this::drag);
      node.setOnMouseReleased(e -> commitEdit()); // one undo entry per rotation drag
    }

    private void press(javafx.scene.input.MouseEvent e) {
      if (selected == null) {
        return;
      }
      RoomViewLayout.Rect rect = currentRect();
      pivotX = RoomViewLayout.anchorX(rect, selected.posX());
      pivotY = RoomViewLayout.anchorY(rect, selected.posY());
      startRotation = selected.rotation();
      javafx.geometry.Point2D p = markerLayer.sceneToLocal(e.getSceneX(), e.getSceneY());
      pressAngle = Math.toDegrees(Math.atan2(p.getY() - pivotY, p.getX() - pivotX));
      e.consume();
    }

    private void drag(javafx.scene.input.MouseEvent e) {
      if (selected == null) {
        return;
      }
      javafx.geometry.Point2D p = markerLayer.sceneToLocal(e.getSceneX(), e.getSceneY());
      double current = Math.toDegrees(Math.atan2(p.getY() - pivotY, p.getX() - pivotX));
      double angle = startRotation + (current - pressAngle);
      if (e.isShiftDown()) {
        angle = Math.round(angle / ROTATION_SNAP_DEGREES) * ROTATION_SNAP_DEGREES;
      }
      selected.setRotation(angle);
      layoutMarker(selected); // re-applies setRotate + repositions the handles/grips
      e.consume();
    }
  }

  // ---- Images ------------------------------------------------------------------------------

  private void chooseRoomImage() {
    RoomDraft room = currentRoom();
    if (room == null) {
      return;
    }
    FileChooser chooser = new FileChooser();
    chooser
        .getExtensionFilters()
        .add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"));
    File file = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
    if (file != null) {
      room.setImagePath(file.getAbsolutePath());
      showBackground(room);
    }
  }

  /**
   * Shows the room artwork for {@code path}, reloading ONLY when the path actually changed. A
   * marker drag never reaches here, and a same-room refresh keeps the already-loaded image on
   * screen, so the background stays stable through drags (the reported vanish-on-drag bug).
   */
  private void showBackground(RoomDraft room) {
    String path = room == null ? null : room.getImagePath();
    String name = room == null ? null : room.getName();
    // Cache key includes the room name so two image-less rooms (which resolve to different room
    // presets) still refresh when switched between.
    String key = path + "|" + name;
    if (java.util.Objects.equals(key, backgroundPath)) {
      return;
    }
    backgroundPath = key;
    // Mirror the IN-GAME room-image fallback exactly (authored → room preset → 800×600 placeholder)
    // so the preview aspect-locks to the SAME artwork the game renders. Without this, a room with a
    // missing background gave the preview the frame's aspect while the game used the preset's, and
    // normalized object placement no longer matched (.scratch/gui-placement-parity).
    Image img = tryLoad(path);
    if (img == null && name != null && !name.isBlank()) {
      img = tryLoad(ui.util.PresetArtResolver.roomPreset(name));
    }
    if (img == null) {
      img =
          ui.util.PlaceholderImageGenerator.createRoomPlaceholder(
              name == null || name.isBlank() ? "Room" : name, 800, 600);
    }
    backgroundImage = img;
    roomImageView.setImage(backgroundImage);
    fitPlate(); // the image aspect changed → re-lock the plate to it
  }

  /** Test seam: the room artwork currently on screen (null when none). */
  Image displayedRoomImage() {
    return roomImageView.getImage();
  }

  /**
   * Test seam: rebuild the current room's preview (as a refresh/relayout does); background
   * persists.
   */
  void rebuildCurrentRoomForTest() {
    rebuildPreview(currentRoom(), placeablesIn(currentRoom()));
  }

  /** Test seam: select the entity at {@code index} in the room's list (baselines the next edit). */
  void selectForTest(int index) {
    entityList.getSelectionModel().select(index);
  }

  /** Test seam: inject a background image + relayout (bypasses tryLoad). */
  void setBackgroundForTest(Image image) {
    backgroundPath = "test";
    backgroundImage = image;
    roomImageView.setImage(image);
    relayoutAll();
  }

  /**
   * Test seam: resize the marker layer (as a window/fullscreen resize does) and run a layout pulse.
   * Repositioning happens via {@code markerLayer.layoutChildren()} — NOT an explicit relayout — so
   * this exercises the actual settled-layout path that a fullscreen toggle triggers.
   */
  void setCanvasSizeForTest(double w, double h) {
    markerLayer.resize(w, h);
    markerLayer.layout();
  }

  /** Test seam: the layer-pixel centre of the marker at {@code index} in the room's list. */
  double[] markerCenterForTest(int index) {
    StackPane sprite = spriteBy.get(entityList.getItems().get(index));
    double w = sprite.getWidth() > 0 ? sprite.getWidth() : sprite.prefWidth(-1);
    double h = sprite.getHeight() > 0 ? sprite.getHeight() : sprite.prefHeight(-1);
    return new double[] {sprite.getLayoutX() + w / 2, sprite.getLayoutY() + h / 2};
  }

  /** Test seam: how many resize handles are currently visible AND have a non-zero rendered size. */
  int visibleHandleCountForTest() {
    int n = 0;
    for (Handle h : handles) {
      if (h.node.isVisible() && h.node.getWidth() > 0 && h.node.getHeight() > 0) {
        n++;
      }
    }
    return n;
  }

  /** Test seam: how many rotation grips are currently visible AND have a non-zero rendered size. */
  int visibleRotateHandleCountForTest() {
    int n = 0;
    for (RotateHandle g : rotateHandles) {
      if (g.node.isVisible() && g.node.getWidth() > 0 && g.node.getHeight() > 0) {
        n++;
      }
    }
    return n;
  }

  /** Test seam: the selected placeable's authored rotation (degrees), or 0 when none selected. */
  double selectedRotationForTest() {
    return selected == null ? 0 : selected.rotation();
  }

  /** Test seam: the live {@code setRotate} on the selected marker's sprite node, or 0 when none. */
  double selectedSpriteRotateForTest() {
    StackPane sprite = selected == null ? null : spriteBy.get(selected);
    return sprite == null ? 0 : sprite.getRotate();
  }

  /**
   * Test seam: apply a rotation to the selected marker and commit it, exactly as a rotation-grip
   * drag+release does (set the angle, relayout, then push one undo entry).
   */
  void applyRotationForTest(double degrees) {
    if (selected == null) {
      return;
    }
    selected.setRotation(degrees);
    layoutMarker(selected);
    commitEdit();
  }

  /** Test seam: whether the selected marker's selection box carries the {@code selected} style. */
  boolean selectedOutlineIsSelectedForTest() {
    StackPane sprite = selected == null ? null : spriteBy.get(selected);
    if (sprite == null || !(sprite.getUserData() instanceof PlacementMarkers.MarkerNodes nodes)) {
      return false;
    }
    return nodes.outline().getStyleClass().contains("selected");
  }

  /** Test seam: the selected marker's selection-box (outline) width, or -1 when none. */
  double selectionBoxWidthForTest() {
    StackPane sprite = selected == null ? null : spriteBy.get(selected);
    if (sprite == null || !(sprite.getUserData() instanceof PlacementMarkers.MarkerNodes nodes)) {
      return -1;
    }
    return nodes.outline().getPrefWidth();
  }

  /** Test seam: end the in-progress edit (as a drag-release / slider-end / toggle would). */
  void commitEditForTest() {
    commitEdit();
  }

  /**
   * Resolves an object's marker image the way the game does: the authored image
   * (classpath/file/url), falling back to the deterministic engraving object preset (via {@link
   * ui.util.PresetArtResolver}) when the object has no usable image — so the marker is never blank
   * and matches RoomView.
   */
  private Image resolveObjectImage(ObjectDraft object) {
    Image image = tryLoad(object.getImagePath());
    if (image == null) {
      image = tryLoad(ui.util.PresetArtResolver.objectPreset(object.getId(), object.getName()));
    }
    return image;
  }

  private Image tryLoad(String path) {
    if (path == null || path.isBlank()) {
      return null;
    }
    try {
      File file = new File(path);
      if (file.isFile()) {
        return new Image(file.toURI().toString(), false);
      }
      URL url = getClass().getResource(path.startsWith("/") ? path : "/" + path);
      if (url != null) {
        return new Image(url.toExternalForm(), false);
      }
    } catch (RuntimeException ignored) {
      // Fall through to no image.
    }
    return null;
  }

  // ---- Placeable adapter -------------------------------------------------------------------

  /**
   * A uniform view of anything the canvas can position + scale: an object, a suspect, or Watson.
   * Hides where each entity's fields live (object/suspect draft vs the room's watsonPos + the
   * case's global watson scale/label) so the marker code treats them identically.
   */
  private interface Placeable {
    String displayName();

    Image image();

    /** SUSPECT vs OBJECT base sprite factor (RoomViewLayout), so sizes match in-game. */
    double baseFactor();

    boolean isWatson();

    /** Gives the entity a sensible default position if it has none yet (so a marker can render). */
    void ensurePlaced();

    Double posX();

    Double posY();

    void setPosition(double x, double y);

    double scaleX();

    double scaleY();

    void setScaleX(double scale);

    void setScaleY(double scale);

    boolean flipX();

    boolean flipY();

    void setFlipX(boolean flip);

    void setFlipY(boolean flip);

    /** Clockwise sprite rotation in degrees about the sprite centre (0 = upright). */
    double rotation();

    void setRotation(double degrees);

    Double labelDX();

    Double labelDY();

    void setLabelOffset(double dx, double dy);

    /** Clears the authored label offset (restores the default position). */
    void clearLabelOffset();

    /** The room this entity is shown in (so undo can navigate back to it). */
    RoomDraft room();

    /** Whether {@code other} targets the same underlying entity (identity across rebuilds). */
    boolean sameEntityAs(Placeable other);
  }

  private final class ObjectPlaceable implements Placeable {
    private final ObjectDraft object;
    private final RoomDraft room;

    ObjectPlaceable(ObjectDraft object, RoomDraft room) {
      this.object = object;
      this.room = room;
    }

    @Override
    public RoomDraft room() {
      return room;
    }

    @Override
    public boolean sameEntityAs(Placeable other) {
      return other instanceof ObjectPlaceable op && op.object == object;
    }

    @Override
    public void clearLabelOffset() {
      object.clearLabelOffset();
    }

    @Override
    public String displayName() {
      return object.getName();
    }

    @Override
    public Image image() {
      return resolveObjectImage(object);
    }

    @Override
    public double baseFactor() {
      return RoomViewLayout.OBJECT_BASE_FACTOR;
    }

    @Override
    public boolean isWatson() {
      return false;
    }

    @Override
    public void ensurePlaced() {
      // Display-only default via posX()/posY(); never persisted just for viewing (mirrors Watson).
      // Merely opening the Placement tab must not mutate an unplaced object, or browsing a case
      // would count as an edit.
    }

    @Override
    public Double posX() {
      return object.getPosX() != null ? object.getPosX() : DEFAULT_OBJECT_X;
    }

    @Override
    public Double posY() {
      return object.getPosY() != null ? object.getPosY() : DEFAULT_OBJECT_Y;
    }

    @Override
    public void setPosition(double x, double y) {
      object.setPosition(x, y);
    }

    @Override
    public double scaleX() {
      return object.getImageScaleX();
    }

    @Override
    public double scaleY() {
      return object.getImageScaleY();
    }

    @Override
    public void setScaleX(double scale) {
      object.setImageScaleX(scale);
    }

    @Override
    public void setScaleY(double scale) {
      object.setImageScaleY(scale);
    }

    @Override
    public boolean flipX() {
      return object.isFlipX();
    }

    @Override
    public boolean flipY() {
      return object.isFlipY();
    }

    @Override
    public void setFlipX(boolean flip) {
      object.setFlipX(flip);
    }

    @Override
    public void setFlipY(boolean flip) {
      object.setFlipY(flip);
    }

    @Override
    public double rotation() {
      return object.getRotation();
    }

    @Override
    public void setRotation(double degrees) {
      object.setRotation(degrees);
    }

    @Override
    public Double labelDX() {
      return object.getLabelDX();
    }

    @Override
    public Double labelDY() {
      return object.getLabelDY();
    }

    @Override
    public void setLabelOffset(double dx, double dy) {
      object.setLabelOffset(dx, dy);
    }
  }

  private final class SuspectPlaceable implements Placeable {
    private final SuspectDraft suspect;

    SuspectPlaceable(SuspectDraft suspect) {
      this.suspect = suspect;
    }

    @Override
    public RoomDraft room() {
      return suspect.getHomeRoom();
    }

    @Override
    public boolean sameEntityAs(Placeable other) {
      return other instanceof SuspectPlaceable sp && sp.suspect == suspect;
    }

    @Override
    public void clearLabelOffset() {
      suspect.clearLabelOffset();
    }

    @Override
    public String displayName() {
      return suspect.getName();
    }

    @Override
    public Image image() {
      return tryLoad(suspect.getImagePath());
    }

    @Override
    public double baseFactor() {
      return RoomViewLayout.SUSPECT_BASE_FACTOR;
    }

    @Override
    public boolean isWatson() {
      return false;
    }

    @Override
    public void ensurePlaced() {
      // Display-only default via posX()/posY(); never persisted just for viewing (mirrors Watson).
      // Merely opening the Placement tab must not mutate an unplaced suspect, or browsing a case
      // would count as an edit.
    }

    @Override
    public Double posX() {
      return suspect.getPosX() != null ? suspect.getPosX() : DEFAULT_SUSPECT_X;
    }

    @Override
    public Double posY() {
      return suspect.getPosY() != null ? suspect.getPosY() : DEFAULT_SUSPECT_Y;
    }

    @Override
    public void setPosition(double x, double y) {
      suspect.setPosition(x, y);
    }

    @Override
    public double scaleX() {
      return suspect.getImageScaleX();
    }

    @Override
    public double scaleY() {
      return suspect.getImageScaleY();
    }

    @Override
    public void setScaleX(double scale) {
      suspect.setImageScaleX(scale);
    }

    @Override
    public void setScaleY(double scale) {
      suspect.setImageScaleY(scale);
    }

    @Override
    public boolean flipX() {
      return suspect.isFlipX();
    }

    @Override
    public boolean flipY() {
      return suspect.isFlipY();
    }

    @Override
    public void setFlipX(boolean flip) {
      suspect.setFlipX(flip);
    }

    @Override
    public void setFlipY(boolean flip) {
      suspect.setFlipY(flip);
    }

    @Override
    public double rotation() {
      return suspect.getRotation();
    }

    @Override
    public void setRotation(double degrees) {
      suspect.setRotation(degrees);
    }

    @Override
    public Double labelDX() {
      return suspect.getLabelDX();
    }

    @Override
    public Double labelDY() {
      return suspect.getLabelDY();
    }

    @Override
    public void setLabelOffset(double dx, double dy) {
      suspect.setLabelOffset(dx, dy);
    }
  }

  /**
   * Watson: his position is per-room (on the {@link RoomDraft}), while his size and label offset
   * are global (on the {@link CaseDraft}). His effective position falls back to the default corner
   * when the room hasn't authored one (never persisted until dragged).
   */
  private final class WatsonPlaceable implements Placeable {
    private final RoomDraft room;

    WatsonPlaceable(RoomDraft room) {
      this.room = room;
    }

    @Override
    public RoomDraft room() {
      return room;
    }

    @Override
    public boolean sameEntityAs(Placeable other) {
      return other instanceof WatsonPlaceable; // one Watson per room; room already matched
    }

    @Override
    public void clearLabelOffset() {
      draft.clearWatsonLabelOffset();
    }

    @Override
    public String displayName() {
      return L10n.t("game.watsonSpeaker");
    }

    @Override
    public Image image() {
      return tryLoad(draft.getWatsonImagePath());
    }

    @Override
    public double baseFactor() {
      return RoomViewLayout.SUSPECT_BASE_FACTOR;
    }

    @Override
    public boolean isWatson() {
      return true;
    }

    @Override
    public void ensurePlaced() {
      // Effective position falls back to the default via posX()/posY(); nothing to persist yet.
    }

    @Override
    public Double posX() {
      return room != null && room.getWatsonPosX() != null ? room.getWatsonPosX() : WATSON_DEFAULT_X;
    }

    @Override
    public Double posY() {
      return room != null && room.getWatsonPosY() != null ? room.getWatsonPosY() : WATSON_DEFAULT_Y;
    }

    @Override
    public void setPosition(double x, double y) {
      if (room != null) {
        room.setWatsonPosition(x, y);
      }
    }

    // Watson's size/orientation is now per-room: read this room's override when set, else the case's
    // global value; writes always land on THIS room so each room is independent (perspective).
    @Override
    public double scaleX() {
      return room != null && room.getWatsonImageScaleX() != null
          ? room.getWatsonImageScaleX()
          : draft.getWatsonImageScaleX();
    }

    @Override
    public double scaleY() {
      return room != null && room.getWatsonImageScaleY() != null
          ? room.getWatsonImageScaleY()
          : draft.getWatsonImageScaleY();
    }

    @Override
    public void setScaleX(double scale) {
      if (room != null) {
        room.setWatsonImageScaleX(scale);
      }
    }

    @Override
    public void setScaleY(double scale) {
      if (room != null) {
        room.setWatsonImageScaleY(scale);
      }
    }

    @Override
    public boolean flipX() {
      return room != null && room.getWatsonFlipX() != null
          ? room.getWatsonFlipX()
          : draft.isWatsonFlipX();
    }

    @Override
    public boolean flipY() {
      return room != null && room.getWatsonFlipY() != null
          ? room.getWatsonFlipY()
          : draft.isWatsonFlipY();
    }

    @Override
    public void setFlipX(boolean flip) {
      if (room != null) {
        room.setWatsonFlipX(flip);
      }
    }

    @Override
    public void setFlipY(boolean flip) {
      if (room != null) {
        room.setWatsonFlipY(flip);
      }
    }

    @Override
    public double rotation() {
      return room != null && room.getWatsonRotation() != null
          ? room.getWatsonRotation()
          : draft.getWatsonRotation();
    }

    @Override
    public void setRotation(double degrees) {
      if (room != null) {
        room.setWatsonRotation(degrees);
      }
    }

    @Override
    public Double labelDX() {
      return room != null && room.getWatsonLabelDX() != null
          ? room.getWatsonLabelDX()
          : draft.getWatsonLabelDX();
    }

    @Override
    public Double labelDY() {
      return room != null && room.getWatsonLabelDY() != null
          ? room.getWatsonLabelDY()
          : draft.getWatsonLabelDY();
    }

    @Override
    public void setLabelOffset(double dx, double dy) {
      if (room != null) {
        room.setWatsonLabelOffset(dx, dy);
      }
    }
  }

  // ---- Small helpers -----------------------------------------------------------------------

  private javafx.scene.control.ListCell<Placeable> entityCell() {
    return new javafx.scene.control.ListCell<>() {
      @Override
      protected void updateItem(Placeable placeable, boolean empty) {
        super.updateItem(placeable, empty);
        setText(empty || placeable == null ? null : placeable.displayName());
      }
    };
  }

  private static Region spacer() {
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    return spacer;
  }

  private static Label label(String key) {
    Label label = new Label(L10n.t(key));
    label.getStyleClass().add("sidebar-label");
    return label;
  }

  private static Label section(String key) {
    Label label = new Label(L10n.t(key));
    label.getStyleClass().add("casemaker-section");
    return label;
  }
}

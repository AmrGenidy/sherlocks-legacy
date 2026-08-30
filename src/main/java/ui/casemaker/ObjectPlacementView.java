package ui.casemaker;

import java.io.File;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import ui.casemaker.model.CaseDraft;
import ui.casemaker.model.ObjectDraft;
import ui.casemaker.model.RoomDraft;
import ui.i18n.L10n;

/**
 * The Case Maker's <b>Objects</b> tab: the per-room <em>content</em> editor for authored Objects —
 * add/remove an object and edit its name, examine text, deduce text, and image. Spatial layout
 * (position, sprite size, name-label offset) lives on the unified <b>Placement</b> tab ({@link
 * SuspectPlacementView}), so an object is positioned in exactly one place; this tab no longer draws a
 * drag preview.
 *
 * <p>Edits write straight into the shared {@link CaseDraft} (DEC-1). Asset copying into the case
 * folder is deferred to export (DEC-7); the image picker simply records the chosen path.
 */
public final class ObjectPlacementView extends BorderPane {

  private static final double GAP = 8;
  private static final double PAD = 16;

  private final CaseDraft draft;

  private final ComboBox<RoomDraft> roomPicker = new ComboBox<>();
  private final ListView<ObjectDraft> objectList = new ListView<>();
  private final TextField newObjectField = new TextField();

  // Object editor controls (content only — size/position live on the Placement tab).
  private final TextField nameField = new TextField();
  private final TextArea examineArea = new TextArea();
  private final TextArea deduceArea = new TextArea();
  private final TextField imageField = new TextField();
  private final VBox editorPanel = new VBox(GAP);

  private ObjectDraft selectedObject;
  private boolean bindingEditor; // guards editor field listeners during programmatic population

  public ObjectPlacementView(CaseDraft draft) {
    this.draft = draft;
    setCenter(buildContent());
    setPadding(new Insets(0, PAD, 0, 0));
    refreshRooms();
  }

  // ---- Content: room picker, object list, editor -------------------------------------------

  private Region buildContent() {
    Label roomLabel = label("casemaker.objects.room");
    roomPicker.setConverter(
        new StringConverter<>() {
          @Override
          public String toString(RoomDraft room) {
            return room == null ? "" : roomDisplay(room);
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

    Label heading = section("casemaker.objects.heading");
    objectList.setCellFactory(list -> objectCell());
    objectList.setPrefHeight(160);
    objectList
        .getSelectionModel()
        .selectedItemProperty()
        .addListener((obs, old, object) -> selectObject(object));

    newObjectField.setPromptText(L10n.t("casemaker.objects.newPrompt"));
    HBox.setHgrow(newObjectField, Priority.ALWAYS);
    newObjectField.setOnAction(e -> addObjectFromField());
    Button add = new Button(L10n.t("casemaker.objects.add"));
    add.setOnAction(e -> addObjectFromField());
    Button remove = new Button(L10n.t("casemaker.objects.remove"));
    remove.setOnAction(e -> removeSelectedObject());
    HBox addRow = new HBox(GAP, newObjectField, add);
    HBox removeRow = new HBox(GAP, remove);

    buildEditorPanel();

    VBox panel =
        new VBox(GAP, roomLabel, roomPicker, heading, objectList, addRow, removeRow, editorPanel);
    panel.getStyleClass().add("panel");
    panel.setPadding(new Insets(PAD));

    ScrollPane scroll = new ScrollPane(panel);
    scroll.setFitToWidth(true);
    scroll.getStyleClass().add("casemaker-sidebar-scroll");
    return scroll;
  }

  private void buildEditorPanel() {
    nameField
        .textProperty()
        .addListener(
            (obs, old, val) -> {
              if (!bindingEditor && selectedObject != null) {
                selectedObject.setName(val);
                objectList.refresh();
              }
            });
    examineArea.setWrapText(true);
    examineArea.setPrefRowCount(3);
    examineArea
        .textProperty()
        .addListener(
            (obs, old, val) -> {
              if (!bindingEditor && selectedObject != null) {
                selectedObject.examineText().set(lang(), val);
              }
            });
    deduceArea.setWrapText(true);
    deduceArea.setPrefRowCount(2);
    deduceArea
        .textProperty()
        .addListener(
            (obs, old, val) -> {
              if (!bindingEditor && selectedObject != null) {
                selectedObject.deduceText().set(lang(), val);
              }
            });

    imageField
        .textProperty()
        .addListener(
            (obs, old, val) -> {
              if (!bindingEditor && selectedObject != null) {
                selectedObject.setImagePath(val == null || val.isBlank() ? null : val);
              }
            });
    Button browse = new Button(L10n.t("casemaker.browse"));
    browse.setOnAction(e -> chooseImageInto(imageField));
    HBox imageRow = new HBox(GAP, imageField, browse);
    HBox.setHgrow(imageField, Priority.ALWAYS);

    editorPanel
        .getChildren()
        .setAll(
            label("casemaker.objects.name"),
            nameField,
            label("casemaker.objects.examine"),
            examineArea,
            label("casemaker.objects.deduce"),
            deduceArea,
            label("casemaker.objects.image"),
            imageRow);
    editorPanel.setVisible(false);
    editorPanel.setManaged(false);
  }

  // ---- Room / object lifecycle -------------------------------------------------------------

  /** Re-syncs the room picker with the draft's rooms (call when rooms are added/renamed/removed). */
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

  private void loadRoom(RoomDraft room) {
    objectList.getItems().setAll(room == null ? java.util.List.of() : room.getObjects());
    selectObject(null);
  }

  private RoomDraft currentRoom() {
    return roomPicker.getSelectionModel().getSelectedItem();
  }

  private void addObjectFromField() {
    RoomDraft room = currentRoom();
    String name = newObjectField.getText() == null ? "" : newObjectField.getText().trim();
    if (room == null || name.isEmpty()) {
      return;
    }
    ObjectDraft object = room.addObject(name);
    objectList.getItems().add(object);
    newObjectField.clear();
    objectList.getSelectionModel().select(object);
  }

  private void removeSelectedObject() {
    RoomDraft room = currentRoom();
    if (room == null || selectedObject == null) {
      return;
    }
    ObjectDraft toRemove = selectedObject;
    room.removeObject(toRemove);
    objectList.getItems().remove(toRemove);
    selectObject(null);
  }

  private void selectObject(ObjectDraft object) {
    selectedObject = object;
    if (object != null) {
      bindingEditor = true;
      nameField.setText(object.getName());
      examineArea.setText(object.examineText().get(lang()) == null ? "" : object.examineText().get(lang()));
      deduceArea.setText(object.deduceText().get(lang()) == null ? "" : object.deduceText().get(lang()));
      imageField.setText(object.getImagePath() == null ? "" : object.getImagePath());
      bindingEditor = false;
      if (objectList.getSelectionModel().getSelectedItem() != object) {
        objectList.getSelectionModel().select(object);
      }
    }
    editorPanel.setVisible(object != null);
    editorPanel.setManaged(object != null);
  }

  /** The language the editor currently authors in (the sidebar's "Editing language" selector). */
  private String lang() {
    return draft.getAuthoringLanguage();
  }

  /** A room's localized Display Name for the current language, falling back to its Universal name. */
  private String roomDisplay(RoomDraft room) {
    String display = room.displayNameText().get(lang());
    return display != null && !display.isBlank() ? display : room.getName();
  }

  /**
   * Re-reads the selected object's examine/deduce text for the current authoring language, so the
   * Objects tab switches language in place when the sidebar selector changes. The object's universal
   * name is a command id and stays language-independent.
   */
  public void refreshLanguage() {
    if (selectedObject != null) {
      selectObject(selectedObject);
    }
  }

  // ---- Images ------------------------------------------------------------------------------

  private void chooseImageInto(TextField target) {
    FileChooser chooser = new FileChooser();
    chooser
        .getExtensionFilters()
        .add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"));
    File file = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
    if (file != null) {
      target.setText(file.getAbsolutePath());
    }
  }

  // ---- Small helpers -----------------------------------------------------------------------

  private javafx.scene.control.ListCell<ObjectDraft> objectCell() {
    return new javafx.scene.control.ListCell<>() {
      @Override
      protected void updateItem(ObjectDraft object, boolean empty) {
        super.updateItem(object, empty);
        setText(empty || object == null ? null : object.getName());
      }
    };
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

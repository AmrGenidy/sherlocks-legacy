package ui.casemaker;

import java.io.File;
import java.net.URL;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
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
import ui.casemaker.model.ContradictionDraft;
import ui.casemaker.model.RoomDraft;
import ui.casemaker.model.SuspectDraft;
import ui.casemaker.model.SuspectStateDraft;
import ui.i18n.L10n;

/**
 * The Case Maker's "Suspects" tab (slice 3). Authors a Suspect's identity, home-room placement on a
 * RoomView-style preview, sprite size, stationary flag, and the LIE/TRUTH/PANIC state machine —
 * including Contradiction Rules whose Evidence id is picked from the editor's registry (Object ids
 * ∪ Deduction ids — never free-typed, DEC-2) and whose reward mints a new Deduction.
 *
 * <p>Edits write straight into the shared {@link CaseDraft}. Asset copying is deferred to export
 * (DEC-7); the preview loads the picked path directly.
 */
public final class SuspectEditorView extends BorderPane {

  private static final double GAP = 8;
  private static final double PAD = 16;

  private final CaseDraft draft;

  private final ListView<SuspectDraft> suspectList = new ListView<>();
  private final TextField newSuspectField = new TextField();

  // Identity controls.
  private final TextField nameField = new TextField();
  private final TextField imageField = new TextField();
  private final Slider sizeSlider = new Slider(0.25, 3.0, 1.0);
  private final ComboBox<RoomDraft> homeRoomPicker = new ComboBox<>();
  private final ComboBox<String> initialStatePicker = new ComboBox<>(observableStates());
  private final CheckBox stationaryCheck = new CheckBox(L10n.t("casemaker.suspects.stationary"));
  // A stateless "honest witness" (no state machine) speaks a single statement + clue. These are the
  // top-level fields; when a state machine is present its per-state statements are used instead.
  private final TextArea statementField = new TextArea();
  private final TextArea clueField = new TextArea();
  private final VBox identityPanel = new VBox(GAP);

  // Home-room placement preview.
  private final Region previewBackground = new Region();
  private final Pane markerLayer = new Pane();
  private Label suspectMarker;

  // State machine editor.
  private final VBox statesPanel = new VBox(PAD);

  private SuspectDraft selected;
  private boolean binding; // guards control listeners during programmatic population

  public SuspectEditorView(CaseDraft draft) {
    this.draft = draft;
    setLeft(buildSidePanel());
    setCenter(buildCenter());
    setPadding(new Insets(0, PAD, 0, 0));
    suspectList.getItems().setAll(draft.getSuspects()); // seed when editing a loaded case (slice 7)
    refreshRooms();
    selectSuspect(null);
  }

  private static javafx.collections.ObservableList<String> observableStates() {
    return javafx.collections.FXCollections.observableArrayList(SuspectDraft.STATES);
  }

  // ---- Side panel: suspect list + identity -------------------------------------------------

  private Region buildSidePanel() {
    Label heading = section("casemaker.suspects.heading");
    suspectList.setCellFactory(list -> suspectCell());
    VBox.setVgrow(suspectList, Priority.ALWAYS);
    suspectList
        .getSelectionModel()
        .selectedItemProperty()
        .addListener((obs, old, suspect) -> selectSuspect(suspect));

    newSuspectField.setPromptText(L10n.t("casemaker.suspects.newPrompt"));
    HBox.setHgrow(newSuspectField, Priority.ALWAYS);
    newSuspectField.setOnAction(e -> addSuspectFromField());
    Button add = new Button(L10n.t("casemaker.suspects.add"));
    add.setOnAction(e -> addSuspectFromField());
    Button remove = new Button(L10n.t("casemaker.suspects.remove"));
    remove.setOnAction(e -> removeSelected());

    buildIdentityPanel();

    VBox panel =
        new VBox(
            GAP,
            heading,
            suspectList,
            new HBox(GAP, newSuspectField, add),
            new HBox(GAP, remove),
            identityPanel,
            buildPreview());
    panel.getStyleClass().add("panel");
    panel.setPadding(new Insets(PAD));
    panel.setPrefWidth(380);

    ScrollPane scroll = new ScrollPane(panel);
    scroll.setFitToWidth(true);
    scroll.getStyleClass().add("casemaker-sidebar-scroll");
    return scroll;
  }

  private void buildIdentityPanel() {
    nameField
        .textProperty()
        .addListener(
            (obs, old, val) -> {
              if (!binding && selected != null) {
                selected.setName(val);
                suspectList.refresh();
                refreshMarkerLabel();
              }
            });

    // Top-level statement + clue (the primary language; other languages via the Localization tab).
    statementField.setWrapText(true);
    statementField.setPrefRowCount(3);
    statementField
        .textProperty()
        .addListener(
            (obs, old, val) -> {
              if (!binding && selected != null) {
                selected.statementText().set(lang(), val);
              }
            });
    clueField.setWrapText(true);
    clueField.setPrefRowCount(2);
    clueField
        .textProperty()
        .addListener(
            (obs, old, val) -> {
              if (!binding && selected != null) {
                selected.clueText().set(lang(), val);
              }
            });

    Button browse = new Button(L10n.t("casemaker.browse"));
    browse.setOnAction(e -> chooseImageInto(imageField));
    imageField
        .textProperty()
        .addListener(
            (obs, old, val) -> {
              if (!binding && selected != null) {
                selected.setImagePath(val == null || val.isBlank() ? null : val);
              }
            });
    HBox imageRow = new HBox(GAP, imageField, browse);
    HBox.setHgrow(imageField, Priority.ALWAYS);

    sizeSlider
        .valueProperty()
        .addListener(
            (obs, old, val) -> {
              if (!binding && selected != null) {
                selected.setImageScale(val.doubleValue());
              }
            });

    homeRoomPicker.setConverter(
        new StringConverter<>() {
          @Override
          public String toString(RoomDraft room) {
            if (room == null) {
              return L10n.t("casemaker.suspects.noHomeRoom");
            }
            String display = room.displayNameText().get(lang());
            return display != null && !display.isBlank() ? display : room.getName();
          }

          @Override
          public RoomDraft fromString(String s) {
            return null;
          }
        });
    homeRoomPicker.setMaxWidth(Double.MAX_VALUE);
    homeRoomPicker
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (obs, old, room) -> {
              if (!binding && selected != null) {
                selected.setHomeRoom(room);
                rebuildPreview();
              }
            });

    initialStatePicker.setMaxWidth(Double.MAX_VALUE);
    initialStatePicker
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (obs, old, state) -> {
              if (!binding && selected != null && state != null) {
                selected.setInitialState(state);
              }
            });

    stationaryCheck
        .selectedProperty()
        .addListener(
            (obs, old, val) -> {
              if (!binding && selected != null) {
                selected.setStationary(val);
              }
            });

    identityPanel
        .getChildren()
        .setAll(
            label("casemaker.suspects.name"),
            nameField,
            label("casemaker.suspects.image"),
            imageRow,
            label("casemaker.suspects.size"),
            sizeSlider,
            label("casemaker.suspects.homeRoom"),
            homeRoomPicker,
            label("casemaker.suspects.initialState"),
            initialStatePicker,
            stationaryCheck);
    identityPanel.setVisible(false);
    identityPanel.setManaged(false);
  }

  private Region buildPreview() {
    previewBackground.getStyleClass().add("casemaker-preview");
    markerLayer.getStyleClass().add("casemaker-preview-markers");
    markerLayer.widthProperty().addListener((obs, old, w) -> positionMarker());
    markerLayer.heightProperty().addListener((obs, old, h) -> positionMarker());

    StackPane plate = new StackPane(previewBackground, markerLayer);
    plate.getStyleClass().add("casemaker-graph-frame");
    plate.setMinSize(280, 200);
    plate.setPrefHeight(220);

    Label hint = new Label(L10n.t("casemaker.suspects.placementHint"));
    hint.getStyleClass().add("casemaker-hint");
    hint.setWrapText(true);

    VBox box = new VBox(GAP, plate, hint);
    return box;
  }

  // ---- Centre: state machine editor --------------------------------------------------------

  private Region buildCenter() {
    Label heading = section("casemaker.suspects.states");
    VBox container = new VBox(PAD, heading, statesPanel);
    container.setPadding(new Insets(0, 0, 0, PAD));
    ScrollPane scroll = new ScrollPane(container);
    scroll.setFitToWidth(true);
    scroll.getStyleClass().add("casemaker-sidebar-scroll");
    return scroll;
  }

  private void rebuildStates() {
    statesPanel.getChildren().clear();
    if (selected == null) {
      return;
    }
    // A suspect with no state machine is an honest witness (Shape B): it speaks the top-level
    // statement above. Do NOT auto-create LIE/TRUTH/PANIC by rendering their cards — buildStateCard
    // calls state(name), which would create empty states and turn the witness into a (broken) liar
    // on export. Offer an explicit opt-in instead. A suspect that already has states (Shape A) keeps
    // its full card editor exactly as before.
    if (selected.getStates().isEmpty()) {
      statesPanel.getChildren().add(buildNoStateMachine());
      return;
    }
    for (String stateName : SuspectDraft.STATES) {
      statesPanel.getChildren().add(buildStateCard(stateName));
    }
  }

  /**
   * The honest-witness (Shape B) editor: the suspect has no state machine, so this is where the
   * author reads and edits its single spoken statement + clue — the same fields the game reads
   * back. An opt-in button below promotes the witness to a full contradictable state machine.
   */
  private Region buildNoStateMachine() {
    Label hint = new Label(L10n.t("casemaker.suspects.noStateMachine"));
    hint.getStyleClass().add("casemaker-hint");
    hint.setWrapText(true);

    Button add = new Button(L10n.t("casemaker.suspects.addStateMachine"));
    add.setOnAction(
        e -> {
          if (selected == null) {
            return;
          }
          // Create the standard states and carry the witness's plain statement into LIE (every
          // language) so nothing is lost when turning them into a contradictable suspect.
          for (Map.Entry<String, String> byLang : selected.statementText().asMap().entrySet()) {
            selected.state("LIE").statementText().set(byLang.getKey(), byLang.getValue());
          }
          selected.state("TRUTH");
          selected.state("PANIC");
          selected.setInitialState("LIE");
          rebuildStates();
        });

    // statementField/clueField are shared, single instances (bound once in buildIdentityPanel and
    // populated in selectSuspect). Re-parenting them here is how a stateless suspect's dialogue
    // becomes visible; for a Shape A suspect this panel is never built, so they simply stay hidden.
    VBox card =
        new VBox(
            GAP,
            label("casemaker.suspects.statement"),
            statementField,
            label("casemaker.suspects.clue"),
            clueField);
    card.getStyleClass().add("panel");
    card.setPadding(new Insets(PAD));

    VBox box = new VBox(GAP, hint, card, add);
    box.setPadding(new Insets(0, 0, 0, PAD));
    return box;
  }

  private Region buildStateCard(String stateName) {
    // Peek, don't create: rendering a card for a state the author hasn't written yet (e.g. a
    // suspect authored with only LIE + TRUTH) must not materialise an empty PANIC state, or merely
    // viewing the case would count as an edit. The state is created lazily on the first real change.
    SuspectStateDraft existing = selected.getState(stateName);
    SuspectDraft owner = selected;

    Label title = new Label(L10n.t("casemaker.suspects.state." + stateName));
    title.getStyleClass().add("casemaker-section");

    TextArea statement =
        new TextArea(
            existing == null || existing.statementText().get(lang()) == null
                ? ""
                : existing.statementText().get(lang()));
    statement.setWrapText(true);
    statement.setPrefRowCount(2);
    statement
        .textProperty()
        .addListener((obs, old, val) -> owner.state(stateName).statementText().set(lang(), val));

    VBox contradictionList = new VBox(GAP);
    if (existing != null) {
      for (ContradictionDraft rule : existing.getContradictions()) {
        contradictionList
            .getChildren()
            .add(buildContradictionRow(existing, rule, contradictionList));
      }
    }
    Button addRule = new Button(L10n.t("casemaker.suspects.addContradiction"));
    addRule.setOnAction(
        e -> {
          SuspectStateDraft state = owner.state(stateName); // create-on-demand
          ContradictionDraft rule = state.addContradiction();
          contradictionList
              .getChildren()
              .add(buildContradictionRow(state, rule, contradictionList));
        });

    VBox card =
        new VBox(
            GAP,
            title,
            label("casemaker.suspects.statement"),
            statement,
            label("casemaker.suspects.contradictions"),
            contradictionList,
            addRule);
    card.getStyleClass().add("panel");
    card.setPadding(new Insets(PAD));
    return card;
  }

  private Region buildContradictionRow(
      SuspectStateDraft state, ContradictionDraft rule, VBox owner) {
    // Evidence: a registry-backed dropdown, repopulated each time it opens so freshly minted
    // deductions and new objects appear (DEC-2 — never free-typed).
    ComboBox<String> evidence = new ComboBox<>();
    evidence.setMaxWidth(Double.MAX_VALUE);
    evidence.setOnShowing(e -> evidence.getItems().setAll(draft.evidenceChoices()));
    evidence.getItems().setAll(draft.evidenceChoices());
    evidence.getSelectionModel().select(rule.getEvidenceId());
    evidence
        .getSelectionModel()
        .selectedItemProperty()
        .addListener((obs, old, val) -> rule.setEvidenceId(val));
    HBox.setHgrow(evidence, Priority.ALWAYS);

    ComboBox<String> nextState = new ComboBox<>(observableStates());
    nextState.getSelectionModel().select(rule.getNextState());
    nextState
        .getSelectionModel()
        .selectedItemProperty()
        .addListener((obs, old, val) -> rule.setNextState(val));

    TextField reward = new TextField(rule.getRewardDeductionId());
    reward.setPromptText(L10n.t("casemaker.suspects.reward"));
    reward
        .textProperty()
        .addListener(
            (obs, old, val) ->
                rule.setRewardDeductionId(val == null || val.isBlank() ? null : val));
    HBox.setHgrow(reward, Priority.ALWAYS);

    TextField message = new TextField(rule.successMessageText().get(lang()));
    message.setPromptText(L10n.t("casemaker.suspects.successMessage"));
    message
        .textProperty()
        .addListener(
            (obs, old, val) ->
                rule.successMessageText().set(lang(), val == null || val.isBlank() ? null : val));

    Button remove = new Button("✕");

    HBox top =
        new HBox(
            GAP,
            labeled("casemaker.suspects.evidence", evidence),
            labeled("casemaker.suspects.nextState", nextState),
            remove);
    top.setAlignment(Pos.BOTTOM_LEFT);
    VBox row =
        new VBox(
            GAP,
            top,
            labeled("casemaker.suspects.reward", reward),
            labeled("casemaker.suspects.successMessage", message));
    row.getStyleClass().add("casemaker-contradiction");
    row.setPadding(new Insets(GAP));

    remove.setOnAction(
        e -> {
          state.removeContradiction(rule);
          owner.getChildren().remove(row);
        });
    return row;
  }

  // ---- Suspect lifecycle -------------------------------------------------------------------

  /** Re-syncs the home-room dropdown with the draft's rooms (call when rooms change). */
  public void refreshRooms() {
    RoomDraft previous = homeRoomPicker.getSelectionModel().getSelectedItem();
    homeRoomPicker.getItems().setAll(draft.getRooms());
    // Null guard: getRooms() is immutable (contains(null) throws), and a fresh case has no rooms.
    if (previous != null && draft.getRooms().contains(previous)) {
      homeRoomPicker.getSelectionModel().select(previous);
    }
  }

  private void addSuspectFromField() {
    String name = newSuspectField.getText() == null ? "" : newSuspectField.getText().trim();
    if (name.isEmpty()) {
      return;
    }
    SuspectDraft suspect = draft.addSuspect(name);
    suspectList.getItems().add(suspect);
    newSuspectField.clear();
    suspectList.getSelectionModel().select(suspect);
  }

  private void removeSelected() {
    if (selected == null) {
      return;
    }
    SuspectDraft toRemove = selected;
    draft.removeSuspect(toRemove);
    suspectList.getItems().remove(toRemove);
    selectSuspect(null);
  }

  /** The language the editor currently authors in (the sidebar's "Editing language" selector). */
  private String lang() {
    return draft.getAuthoringLanguage();
  }

  /**
   * Re-reads the selected suspect's text into the fields for the current authoring language. Called
   * when the sidebar "Editing language" changes so the statement/clue/state text switch language in
   * place without losing the selection.
   */
  public void refreshLanguage() {
    if (selected != null) {
      selectSuspect(selected);
    }
  }

  private void selectSuspect(SuspectDraft suspect) {
    selected = suspect;
    if (suspect != null) {
      binding = true;
      nameField.setText(suspect.getName());
      imageField.setText(suspect.getImagePath() == null ? "" : suspect.getImagePath());
      sizeSlider.setValue(suspect.getImageScale());
      homeRoomPicker.getSelectionModel().select(suspect.getHomeRoom());
      statementField.setText(
          suspect.statementText().get(lang()) == null ? "" : suspect.statementText().get(lang()));
      clueField.setText(suspect.clueText().get(lang()) == null ? "" : suspect.clueText().get(lang()));
      initialStatePicker.getSelectionModel().select(suspect.getInitialState());
      stationaryCheck.setSelected(suspect.isStationary());
      binding = false;
      if (suspectList.getSelectionModel().getSelectedItem() != suspect) {
        suspectList.getSelectionModel().select(suspect);
      }
    }
    identityPanel.setVisible(suspect != null);
    identityPanel.setManaged(suspect != null);
    rebuildStates();
    rebuildPreview();
  }

  // ---- Placement preview -------------------------------------------------------------------

  private void rebuildPreview() {
    markerLayer.getChildren().clear();
    suspectMarker = null;
    RoomDraft home = selected == null ? null : selected.getHomeRoom();
    setBackgroundImage(home == null ? null : home.getImagePath());
    if (selected == null || home == null) {
      return;
    }
    // Do NOT write a default position here: merely viewing an as-yet-unplaced suspect must not
    // mutate the model (that would make browsing a case look "modified" on close). The marker is
    // shown at a sensible default spot for display only; the position is persisted on first drag.
    suspectMarker = new Label(selected.getName());
    suspectMarker.getStyleClass().addAll("casemaker-object-marker", "selected");
    suspectMarker.setOnMouseDragged(this::dragMarker);
    markerLayer.getChildren().add(suspectMarker);
    positionMarker();
  }

  private void dragMarker(javafx.scene.input.MouseEvent e) {
    if (selected == null || suspectMarker == null) {
      return;
    }
    double w = markerLayer.getWidth();
    double h = markerLayer.getHeight();
    if (w <= 0 || h <= 0) {
      return;
    }
    double centerX = suspectMarker.getLayoutX() + e.getX();
    double centerY = suspectMarker.getLayoutY() + e.getY();
    selected.setPosition(centerX / w, centerY / h);
    positionMarker();
  }

  private void positionMarker() {
    if (suspectMarker == null || selected == null) {
      return;
    }
    double w = markerLayer.getWidth();
    double h = markerLayer.getHeight();
    if (w <= 0 || h <= 0) {
      return;
    }
    // Fall back to a display-only default for an unplaced suspect (see rebuildPreview) — read-only,
    // never written back to the model.
    double px = selected.getPosX() == null ? 0.5 : selected.getPosX();
    double py = selected.getPosY() == null ? 0.6 : selected.getPosY();
    double mw =
        suspectMarker.getWidth() > 0 ? suspectMarker.getWidth() : suspectMarker.prefWidth(-1);
    double mh =
        suspectMarker.getHeight() > 0 ? suspectMarker.getHeight() : suspectMarker.prefHeight(-1);
    suspectMarker.setLayoutX(px * w - mw / 2);
    suspectMarker.setLayoutY(py * h - mh / 2);
  }

  private void refreshMarkerLabel() {
    if (suspectMarker != null && selected != null) {
      suspectMarker.setText(selected.getName());
    }
  }

  private void setBackgroundImage(String path) {
    Image image = tryLoad(path);
    if (image == null) {
      previewBackground.setBackground(null);
      return;
    }
    previewBackground.setBackground(
        new Background(
            new BackgroundImage(
                image,
                BackgroundRepeat.NO_REPEAT,
                BackgroundRepeat.NO_REPEAT,
                BackgroundPosition.CENTER,
                new BackgroundSize(0, 0, false, false, true, false))));
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
      // placeholder
    }
    return null;
  }

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

  // ---- Helpers -----------------------------------------------------------------------------

  private ListCell<SuspectDraft> suspectCell() {
    return new ListCell<>() {
      @Override
      protected void updateItem(SuspectDraft suspect, boolean empty) {
        super.updateItem(suspect, empty);
        setText(empty || suspect == null ? null : suspect.getName());
      }
    };
  }

  private static Region labeled(String key, Region control) {
    Label label = new Label(L10n.t(key));
    label.getStyleClass().add("sidebar-label");
    VBox box = new VBox(2, label, control);
    HBox.setHgrow(box, Priority.ALWAYS);
    return box;
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

package ui.casemaker;

import JsonDTO.CaseFile;
import extractors.CaseLoader;
import extractors.CaseValidator;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import ui.MainController;
import ui.casemaker.model.CaseDraft;
import ui.casemaker.model.CaseDraftLoader;
import ui.casemaker.model.CaseMakerSerializer;
import ui.casemaker.model.RoomDraft;
import ui.i18n.CaseTitles;
import ui.i18n.L10n;
import ui.i18n.LocaleStyling;
import ui.util.AppIcon;
import ui.util.PresetArtResolver;
import ui.util.Theme;

public final class CaseMakerWindow extends Stage {
   private static final double GAP = (double)8.0F;
   private static final double PAD = (double)16.0F;
   private static final double NODE_CASCADE_X = (double)160.0F;
   private static final double NODE_CASCADE_Y = (double)110.0F;
   private final CaseDraft draft;
   private final Map<RoomDraft, StackPane> nodeByRoom;
   private final List<Node> edgeNodes;
   private final Pane graphCanvas;
   private final ObjectPlacementView objectPlacementView;
   private final SuspectEditorView suspectEditorView;
   private final SuspectPlacementView suspectPlacementView;
   private TabPane centerTabs;
   private Tab placementTab;
   private final CaseLogicView caseLogicView;
   private final LocalizationView localizationView;
   private final ListView<RoomDraft> roomList;
   private final Label connectivityBanner;
   private final ToggleButton linkModeToggle;
   private final TextField newRoomField;
   private static final String NO_PRESET = "";
   private final ComboBox<String> roomBackgroundPicker;
   private boolean syncingRoomBackground;
   // The sidebar "Editing language" selector: which language the content editors show/edit. Separate
   // from the interface language.
   private ComboBox<String> editingLanguageBox;
   private final MainController shell;
   private final ListView<String> validationList;
   private String lastSavedSnapshot;
   private RoomDraft selectedRoom;
   private RoomDraft linkSource;
   private double dragAnchorX;
   private double dragAnchorY;
   private double dragNodeX;
   private double dragNodeY;

   public CaseMakerWindow(MainController shell) {
      this(shell, new CaseDraft());
   }

   public CaseMakerWindow(MainController shell, CaseDraft draft) {
      this.nodeByRoom = new IdentityHashMap();
      this.edgeNodes = new ArrayList();
      this.graphCanvas = new Pane();
      this.roomList = new ListView();
      this.connectivityBanner = new Label();
      this.linkModeToggle = new ToggleButton(L10n.t("casemaker.graph.linkMode"));
      this.newRoomField = new TextField();
      this.roomBackgroundPicker = new ComboBox();
      this.validationList = new ListView();
      this.shell = shell;
      this.draft = draft;
      this.objectPlacementView = new ObjectPlacementView(draft);
      this.suspectEditorView = new SuspectEditorView(draft);
      this.suspectPlacementView = new SuspectPlacementView(draft);
      this.caseLogicView = new CaseLogicView(draft);
      this.localizationView = new LocalizationView(draft);
      // Keep the sidebar "Editing language" selector in step when the language is changed from
      // inside the Localization tab (guarded against loops by applyEditingLanguage).
      this.localizationView.setOnLanguageChange(this::applyEditingLanguage);
      this.setTitle(L10n.t("casemaker.title"));
      this.setMinWidth((double)900.0F);
      this.setMinHeight((double)600.0F);
      AppIcon.applyTo(this);
      BorderPane root = new BorderPane();
      root.getStyleClass().add("casemaker-root");
      root.setTop(this.buildHeader());
      root.setLeft(this.buildSidebar());
      root.setCenter(this.buildCenter());
      root.setBottom(this.buildFooter());
      LocaleStyling.apply(root);
      Scene scene = new Scene(root, (double)1100.0F, (double)720.0F);
      Theme.install(scene);
      scene.getStylesheets().add(this.css("/css/casemaker.css"));
      this.setScene(scene);
      this.installPlacementUndoShortcuts(scene);
      this.seedRoomGraphFromDraft();
      this.refreshConnectivity();
      this.lastSavedSnapshot = CaseMakerSerializer.toJson(draft);
      // Validate a LOADED case up front so silent problems (e.g. a suspect with no dialogue) are
      // visible the moment it opens — but not a fresh, empty new case, which would only show the
      // expected "no rooms yet" noise.
      if (draft.getSuspects() != null && !draft.getSuspects().isEmpty()) {
         javafx.application.Platform.runLater(this::runValidation);
      }
      this.setOnCloseRequest((event) -> {
         if (!this.confirmCloseAllowed()) {
            event.consume();
         }

      });
   }

   private void seedRoomGraphFromDraft() {
      for(RoomDraft room : this.draft.getRooms()) {
         this.roomList.getItems().add(room);
         this.createNode(room);
      }

      this.redrawEdges();
      if (this.draft.getStartingRoom() != null) {
         this.roomList.getSelectionModel().select(this.draft.getStartingRoom());
      }

      this.refreshNodeStyles();
   }

   private String css(String path) {
      return this.getClass().getResource(path).toExternalForm();
   }

   private void installPlacementUndoShortcuts(Scene scene) {
      KeyCombination undo = new KeyCodeCombination(KeyCode.Z, new KeyCombination.Modifier[]{KeyCombination.CONTROL_DOWN});
      KeyCombination redoY = new KeyCodeCombination(KeyCode.Y, new KeyCombination.Modifier[]{KeyCombination.CONTROL_DOWN});
      KeyCombination redoZ = new KeyCodeCombination(KeyCode.Z, new KeyCombination.Modifier[]{KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN});
      scene.getAccelerators().put(undo, (Runnable)() -> {
         SuspectPlacementView var10001 = this.suspectPlacementView;
         Objects.requireNonNull(var10001);
         this.onPlacementTab(var10001::undo);
      });
      scene.getAccelerators().put(redoY, (Runnable)() -> {
         SuspectPlacementView var10001 = this.suspectPlacementView;
         Objects.requireNonNull(var10001);
         this.onPlacementTab(var10001::redo);
      });
      scene.getAccelerators().put(redoZ, (Runnable)() -> {
         SuspectPlacementView var10001 = this.suspectPlacementView;
         Objects.requireNonNull(var10001);
         this.onPlacementTab(var10001::redo);
      });
   }

   private void onPlacementTab(Runnable action) {
      if (this.centerTabs != null && this.centerTabs.getSelectionModel().getSelectedItem() == this.placementTab) {
         action.run();
      }

   }

   private Node buildHeader() {
      Label title = new Label(L10n.t("casemaker.heading"));
      title.getStyleClass().add("window-title");
      Region rule = new Region();
      rule.getStyleClass().add("title-rule");
      rule.setMaxWidth(Double.NEGATIVE_INFINITY);
      this.connectivityBanner.getStyleClass().add("casemaker-banner");
      this.connectivityBanner.setWrapText(true);
      this.connectivityBanner.setMaxWidth(Double.MAX_VALUE);
      VBox header = new VBox((double)8.0F, new Node[]{title, rule, this.connectivityBanner});
      header.setPadding(new Insets((double)16.0F, (double)16.0F, (double)8.0F, (double)16.0F));
      return header;
   }

   private Node buildSidebar() {
      VBox languagePanel = this.buildEditingLanguagePanel();
      VBox details = this.buildDetailsForm();
      VBox rooms = this.buildRoomsPanel();
      VBox sidebar = new VBox((double)16.0F, new Node[]{languagePanel, details, rooms});
      sidebar.setPadding(new Insets((double)0.0F, (double)16.0F, (double)16.0F, (double)16.0F));
      sidebar.setPrefWidth((double)340.0F);
      VBox.setVgrow(rooms, Priority.ALWAYS);
      ScrollPane scroll = new ScrollPane(sidebar);
      scroll.setFitToWidth(true);
      scroll.getStyleClass().add("casemaker-sidebar-scroll");
      return scroll;
   }

   /**
    * The "Editing language" selector: which language the content editors (Suspects tab statements,
    * the Localization tab, …) display and edit. This is an editor-session preference, entirely
    * separate from the interface language, and is never serialized into the case.
    */
   private VBox buildEditingLanguagePanel() {
      this.editingLanguageBox = new ComboBox<>();
      this.editingLanguageBox.setMaxWidth(Double.MAX_VALUE);
      this.editingLanguageBox.getItems().setAll(this.draft.getLanguages());
      this.editingLanguageBox.setValue(this.draft.getAuthoringLanguage());
      // Repopulate from the draft each time it opens so languages added/removed on the Localization
      // tab appear here without a rebuild.
      this.editingLanguageBox.setOnShowing((e) -> {
         String current = this.editingLanguageBox.getValue();
         this.editingLanguageBox.getItems().setAll(this.draft.getLanguages());
         if (current != null && this.draft.getLanguages().contains(current)) {
            this.editingLanguageBox.setValue(current);
         }
      });
      this.editingLanguageBox
          .getSelectionModel()
          .selectedItemProperty()
          .addListener((o, a, b) -> {
             if (b != null) {
                this.applyEditingLanguage(b);
             }
          });
      VBox panel =
          new VBox(
              (double)8.0F,
              new Node[]{this.fieldLabel("casemaker.editingLanguage"), this.editingLanguageBox});
      panel.getStyleClass().add("panel");
      panel.setPadding(new Insets((double)16.0F));
      return panel;
   }

   /**
    * Applies a new editing language across the editors: records it on the draft, keeps both the
    * sidebar box and the Localization tab in step, and re-reads the Suspects editor in the new
    * language. The equality guard makes this idempotent, so the two selectors can call it freely
    * without looping.
    */
   private void applyEditingLanguage(String lang) {
      if (lang == null || lang.equals(this.draft.getAuthoringLanguage())) {
         return;
      }
      this.draft.setAuthoringLanguage(lang);
      if (this.editingLanguageBox != null && !lang.equals(this.editingLanguageBox.getValue())) {
         this.editingLanguageBox.setValue(lang);
      }
      this.suspectEditorView.refreshLanguage();
      this.objectPlacementView.refreshLanguage();
      this.caseLogicView.refreshLanguage();
      this.refreshRoomLabels();
      // The home-room / object-room pickers label rooms too — repopulate so they re-render in the
      // new language.
      this.objectPlacementView.refreshRooms();
      this.suspectEditorView.refreshRooms();
      this.suspectPlacementView.refreshRooms();
      this.localizationView.selectLanguage(lang);
   }

   private VBox buildDetailsForm() {
      Label heading = sectionLabel("casemaker.details.heading");
      TextField universalTitle = new TextField(this.draft.getUniversalTitle());
      universalTitle.textProperty().addListener((obs, old, val) -> this.draft.setUniversalTitle(blankToNull(val)));
      TextField author = new TextField(this.draft.getAuthor());
      author.textProperty().addListener((obs, old, val) -> this.draft.setAuthor(blankToNull(val)));
      // Author-defined character names (single string for all languages). Blank keeps the engine's
      // Sherlock Holmes / Dr. Watson defaults, so existing cases are unchanged.
      TextField detectiveName = new TextField(this.draft.getDetectiveName());
      detectiveName.setPromptText("Sherlock Holmes");
      detectiveName.textProperty().addListener((obs, old, val) -> this.draft.setDetectiveName(blankToNull(val)));
      TextField helperName = new TextField(this.draft.getHelperName());
      helperName.setPromptText("Dr. Watson");
      helperName.textProperty().addListener((obs, old, val) -> this.draft.setHelperName(blankToNull(val)));
      int initialTokens = this.draft.getStartingInsightTokens() == null ? 0 : this.draft.getStartingInsightTokens();
      Spinner<Integer> tokens = new Spinner(0, 99, initialTokens);
      tokens.setEditable(true);
      tokens.valueProperty().addListener((obs, old, val) -> this.draft.setStartingInsightTokens(val));
      this.draft.setStartingInsightTokens((Integer)tokens.getValue());
      TextField watsonImage = new TextField(this.draft.getWatsonImagePath());
      watsonImage.textProperty().addListener((obs, old, val) -> this.draft.setWatsonImagePath(blankToNull(val)));
      Button browse = new Button(L10n.t("casemaker.browse"));
      browse.setOnAction((e) -> this.chooseImageInto(watsonImage));
      HBox watsonRow = new HBox((double)8.0F, new Node[]{watsonImage, browse});
      HBox.setHgrow(watsonImage, Priority.ALWAYS);
      VBox form = new VBox((double)8.0F, new Node[]{heading, fieldLabel("casemaker.field.universalTitle"), universalTitle, fieldLabel("casemaker.field.author"), author, fieldLabel("casemaker.field.detectiveName"), detectiveName, fieldLabel("casemaker.field.helperName"), helperName, fieldLabel("casemaker.field.startingTokens"), tokens, fieldLabel("casemaker.field.watsonImage"), watsonRow});
      form.getStyleClass().add("panel");
      form.setPadding(new Insets((double)16.0F));
      return form;
   }

   private VBox buildRoomsPanel() {
      Label heading = sectionLabel("casemaker.rooms.heading");
      this.roomList.setCellFactory((list) -> new RoomCell());
      VBox.setVgrow(this.roomList, Priority.ALWAYS);
      this.roomList.getSelectionModel().selectedItemProperty().addListener((obs, old, room) -> this.selectRoom(room, false));
      this.newRoomField.setPromptText(L10n.t("casemaker.rooms.newPrompt"));
      Button add = new Button(L10n.t("casemaker.rooms.add"));
      add.getStyleClass().add("primary-button");
      add.setOnAction((e) -> this.addRoomFromField());
      this.newRoomField.setOnAction((e) -> this.addRoomFromField());
      HBox.setHgrow(this.newRoomField, Priority.ALWAYS);
      HBox addRow = new HBox((double)8.0F, new Node[]{this.newRoomField, add});
      Button rename = new Button(L10n.t("casemaker.rooms.rename"));
      rename.setOnAction((e) -> this.renameSelectedRoom());
      Button delete = new Button(L10n.t("casemaker.rooms.delete"));
      delete.setOnAction((e) -> this.deleteSelectedRoom());
      Button setStarting = new Button(L10n.t("casemaker.rooms.setStarting"));
      setStarting.setOnAction((e) -> this.setSelectedAsStarting());
      VBox actions = new VBox((double)8.0F, new Node[]{rename, setStarting, delete});
      Label backgroundLabel = fieldLabel("casemaker.rooms.background");
      this.roomBackgroundPicker.getItems().add("");
      this.roomBackgroundPicker.getItems().addAll(PresetArtResolver.ROOM_PRESET_IDS);
      this.roomBackgroundPicker.setConverter(new StringConverter<String>() {
         public String toString(String presetId) {
            return presetId != null && !presetId.isBlank() ? L10n.t("casemaker.preset." + presetId) : L10n.t("casemaker.rooms.background.none");
         }

         public String fromString(String s) {
            return s;
         }
      });
      this.roomBackgroundPicker.setMaxWidth(Double.MAX_VALUE);
      this.roomBackgroundPicker.setDisable(true);
      this.roomBackgroundPicker.valueProperty().addListener((obs, old, presetId) -> this.onRoomBackgroundPicked(presetId));
      VBox background = new VBox((double)8.0F, new Node[]{backgroundLabel, this.roomBackgroundPicker});
      VBox panel = new VBox((double)8.0F, new Node[]{heading, this.roomList, addRow, actions, background});
      panel.getStyleClass().add("panel");
      panel.setPadding(new Insets((double)16.0F));
      return panel;
   }

   private Node buildCenter() {
      Tab mapTab = new Tab(L10n.t("casemaker.tab.map"), this.buildGraphArea());
      mapTab.setClosable(false);
      Tab objectsTab = new Tab(L10n.t("casemaker.tab.objects"), this.objectPlacementView);
      objectsTab.setClosable(false);
      Tab suspectsTab = new Tab(L10n.t("casemaker.tab.suspects"), this.suspectEditorView);
      suspectsTab.setClosable(false);
      this.placementTab = new Tab(L10n.t("casemaker.tab.placement"), this.suspectPlacementView);
      this.placementTab.setClosable(false);
      Tab logicTab = new Tab(L10n.t("casemaker.tab.logic"), this.caseLogicView);
      logicTab.setClosable(false);
      Tab localizationTab = new Tab(L10n.t("casemaker.tab.localization"), this.localizationView);
      localizationTab.setClosable(false);
      TabPane tabs = new TabPane(new Tab[]{mapTab, objectsTab, suspectsTab, this.placementTab, logicTab, localizationTab});
      this.centerTabs = tabs;
      tabs.setPadding(new Insets((double)0.0F, (double)0.0F, (double)0.0F, (double)16.0F));
      tabs.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
         if (selected == localizationTab) {
            this.localizationView.refresh();
         } else if (selected == this.placementTab) {
            this.suspectPlacementView.refresh();
         }

      });
      return tabs;
   }

   private Node buildGraphArea() {
      Label heading = sectionLabel("casemaker.graph.heading");
      this.linkModeToggle.getStyleClass().add("casemaker-link-toggle");
      this.linkModeToggle.setOnAction((e) -> this.clearLinkSource());
      HBox toolbar = new HBox((double)8.0F, new Node[]{heading, spacer(), this.linkModeToggle});
      toolbar.setAlignment(Pos.CENTER_LEFT);
      this.graphCanvas.getStyleClass().add("casemaker-canvas");
      this.graphCanvas.setPrefSize((double)2000.0F, (double)1400.0F);
      this.graphCanvas.setOnMousePressed((e) -> this.clearLinkSource());
      ScrollPane scroll = new ScrollPane(this.graphCanvas);
      scroll.setPannable(false);
      VBox.setVgrow(scroll, Priority.ALWAYS);
      StackPane plate = new StackPane(new Node[]{scroll});
      plate.getStyleClass().add("casemaker-graph-frame");
      VBox area = new VBox((double)8.0F, new Node[]{toolbar, plate});
      area.setPadding(new Insets((double)0.0F, (double)16.0F, (double)0.0F, (double)0.0F));
      area.setFillWidth(true);
      VBox.setVgrow(plate, Priority.ALWAYS);
      return area;
   }

   private Node buildFooter() {
      this.validationList.setPrefHeight((double)96.0F);
      this.validationList.setPlaceholder(new Label(L10n.t("casemaker.validate.none")));
      this.validationList.setCellFactory((list) -> this.validationCell());
      // Make the validation "terminal" copyable: select multiple lines, Ctrl+C to copy them (or all
      // when nothing is selected), and a right-click menu with Copy / Copy all.
      this.validationList
          .getSelectionModel()
          .setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
      this.validationList.setOnKeyPressed(
          (e) -> {
             if (e.isControlDown() && e.getCode() == KeyCode.C) {
                this.copyValidationSelection();
                e.consume();
             }
          });
      javafx.scene.control.MenuItem copyItem =
          new javafx.scene.control.MenuItem(L10n.t("casemaker.validate.copy"));
      copyItem.setOnAction((e) -> this.copyValidationSelection());
      javafx.scene.control.MenuItem copyAllItem =
          new javafx.scene.control.MenuItem(L10n.t("casemaker.validate.copyAll"));
      copyAllItem.setOnAction((e) -> this.copyValidationAll());
      this.validationList.setContextMenu(
          new javafx.scene.control.ContextMenu(
              new javafx.scene.control.MenuItem[]{copyItem, copyAllItem}));
      Button open = new Button(L10n.t("casemaker.open"));
      open.setOnAction((e) -> this.openCase());
      Button validate = new Button(L10n.t("casemaker.validate"));
      validate.setOnAction((e) -> this.runValidation());
      Button copyAll = new Button(L10n.t("casemaker.validate.copyAll"));
      copyAll.setOnAction((e) -> this.copyValidationAll());
      Button testPlay = new Button(L10n.t("casemaker.testPlay"));
      testPlay.setOnAction((e) -> this.testPlay());
      Button save = new Button(L10n.t("casemaker.save"));
      save.getStyleClass().add("primary-button");
      save.setOnAction((e) -> this.saveInPlace());
      Button export = new Button(L10n.t("casemaker.export"));
      export.setOnAction((e) -> this.export());
      Button close = new Button(L10n.t("casemaker.close"));
      close.setOnAction((e) -> this.requestClose());
      Region spacer = new Region();
      HBox.setHgrow(spacer, Priority.ALWAYS);
      HBox buttons = new HBox((double)8.0F, new Node[]{open, validate, copyAll, spacer, testPlay, save, export, close});
      buttons.setAlignment(Pos.CENTER_LEFT);
      VBox footer = new VBox((double)8.0F, new Node[]{this.validationList, buttons});
      footer.setPadding(new Insets((double)8.0F, (double)16.0F, (double)16.0F, (double)16.0F));
      return footer;
   }

   /** Copies the selected validation lines to the clipboard, or all lines when none are selected. */
   private void copyValidationSelection() {
      java.util.List<String> selected = this.validationList.getSelectionModel().getSelectedItems();
      copyLinesToClipboard(
          selected == null || selected.isEmpty() ? this.validationList.getItems() : selected);
   }

   /** Copies every validation line to the clipboard. */
   private void copyValidationAll() {
      copyLinesToClipboard(this.validationList.getItems());
   }

   private static void copyLinesToClipboard(java.util.List<String> lines) {
      if (lines == null || lines.isEmpty()) {
         return;
      }
      javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
      content.putString(String.join(System.lineSeparator(), lines));
      javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
   }

   private ListCell<String> validationCell() {
      return new ListCell<String>() {
         protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            this.getStyleClass().removeAll(new String[]{"validation-error", "validation-warning"});
            if (!empty && item != null) {
               this.setText(item);
               this.getStyleClass().add(item.startsWith("ERROR") ? "validation-error" : "validation-warning");
            } else {
               this.setText((String)null);
            }

         }
      };
   }

   private boolean runValidation() {
      CaseValidator.Report report = CaseExporter.validate(this.draft);
      this.validationList.getItems().setAll(new String[0]);

      for(CaseValidator.Issue issue : report.issues()) {
         // Skip image-path warnings for files that actually exist on disk. In the editor an image
         // path is resolved to an absolute picked file (or kept case-relative), and the shared
         // resolver refuses absolute paths for security — so it "does not resolve" even though the
         // file is present and will be copied on export. Suppressing these keeps the terminal to
         // real problems. Genuinely-missing images are still reported.
         if (imageResolvesOnDisk(issue)) {
            continue;
         }
         ObservableList var10000 = this.validationList.getItems();
         String var10001 = String.valueOf(issue.severity());
         var10000.add(var10001 + " @ " + issue.location() + ": " + issue.message());
      }

      return !report.hasErrors();
   }

   /**
    * True when an image-path WARNING refers to a file that actually exists — either as an absolute
    * picked file, or relative to the case's source folder. Used to hide false "does not resolve"
    * warnings the editor produces before export (the shared resolver refuses absolute paths).
    */
   private boolean imageResolvesOnDisk(CaseValidator.Issue issue) {
      if (issue.severity() != CaseValidator.Severity.WARNING) {
         return false;
      }
      String msg = issue.message();
      // Only the image/soundtrack path warning carries this exact suffix (warnIfUnresolved); this
      // avoids ever matching the "does not resolve to any object or deduction" logic messages.
      if (msg == null || !msg.contains("(case dir or classpath)")) {
         return false;
      }
      int a = msg.indexOf(39); // first single-quote
      int b = a >= 0 ? msg.indexOf(39, a + 1) : -1;
      if (a < 0 || b <= a) {
         return false;
      }
      String path = msg.substring(a + 1, b);
      if (path.isBlank()) {
         return false;
      }
      try {
         if (new java.io.File(path).isFile()) {
            return true; // absolute (or CWD-relative) picked file that exists
         }
         java.nio.file.Path dir = this.draft.getSourceDir();
         return dir != null && java.nio.file.Files.isRegularFile(dir.resolve(path));
      } catch (RuntimeException e) {
         return false;
      }
   }

   /**
    * Saves the case back to the file it was opened from (in place), so placement/other tweaks
    * persist without exporting a fresh copy and swapping it by hand. A brand-new case (no source
    * file yet) falls through to Export, which prompts for a location. Validation is refreshed in the
    * terminal but does not block a save.
    */
   private boolean saveInPlace() {
      this.runValidation(); // refresh the terminal; saving is not gated on it
      try {
         CaseExporter.Result result = CaseExporter.saveInPlace(this.draft);
         this.lastSavedSnapshot = CaseMakerSerializer.toJson(this.draft);
         this.alert(
             AlertType.INFORMATION,
             L10n.t("casemaker.save.done", new Object[]{result.caseJson().toString()}));
         return true;
      } catch (IllegalStateException noSource) {
         // Brand-new case with no file yet — fall back to the Export ("save as") flow.
         return this.export();
      } catch (Exception ex) {
         this.alert(
             AlertType.ERROR,
             L10n.t("casemaker.save.failed", new Object[]{String.valueOf(ex.getMessage())}));
         return false;
      }
   }

   private boolean export() {
      if (!this.runValidation()) {
         this.alert(AlertType.ERROR, L10n.t("casemaker.export.blocked"));
         return false;
      } else {
         DirectoryChooser chooser = new DirectoryChooser();
         File defaultDir = new File("cases");
         if (defaultDir.isDirectory()) {
            chooser.setInitialDirectory(defaultDir);
         }

         chooser.setTitle(L10n.t("casemaker.export"));
         File dir = chooser.showDialog(this);
         if (dir == null) {
            return false;
         } else {
            try {
               CaseExporter.Result result = CaseExporter.export(this.draft, dir.toPath());
               this.lastSavedSnapshot = CaseMakerSerializer.toJson(this.draft);
               this.alert(AlertType.INFORMATION, L10n.t("casemaker.export.done", new Object[]{result.caseDir().toString()}));
               return true;
            } catch (Exception ex) {
               this.alert(AlertType.ERROR, L10n.t("casemaker.export.failed", new Object[]{String.valueOf(ex.getMessage())}));
               return false;
            }
         }
      }
   }

   boolean isDirty() {
      return !CaseMakerSerializer.toJson(this.draft).equals(this.lastSavedSnapshot);
   }

   private void requestClose() {
      if (this.confirmCloseAllowed()) {
         this.close();
      }

   }

   private boolean confirmCloseAllowed() {
      if (!this.isDirty()) {
         return true;
      } else {
         Alert dialog = new Alert(AlertType.CONFIRMATION);
         dialog.setTitle(L10n.t("casemaker.close.unsavedTitle"));
         dialog.setHeaderText((String)null);
         dialog.setContentText(L10n.t("casemaker.close.unsavedMessage"));
         ButtonType exportButton = new ButtonType(L10n.t("casemaker.close.export"), ButtonData.OK_DONE);
         ButtonType discardButton = new ButtonType(L10n.t("casemaker.close.discard"), ButtonData.OTHER);
         ButtonType cancelButton = new ButtonType(L10n.t("casemaker.close.cancel"), ButtonData.CANCEL_CLOSE);
         dialog.getButtonTypes().setAll(new ButtonType[]{exportButton, discardButton, cancelButton});
         themeDialog(dialog.getDialogPane());
         Optional<ButtonType> choice = dialog.showAndWait();
         if (!choice.isEmpty() && choice.get() != cancelButton) {
            return choice.get() == discardButton ? true : this.export();
         } else {
            return false;
         }
      }
   }

   private void testPlay() {
      if (!this.runValidation()) {
         this.alert(AlertType.ERROR, L10n.t("casemaker.export.blocked"));
      } else {
         try {
            Path tmp = Files.createTempDirectory("casemaker-testplay");
            CaseExporter.export(this.draft, tmp);
            List<CaseFile> cases = CaseLoader.loadCases(tmp.toString());
            CaseFile match = (CaseFile)cases.stream().filter((c) -> this.draft.getUniversalTitle() != null && this.draft.getUniversalTitle().equals(c.getUniversalTitle())).findFirst().orElse(cases.isEmpty() ? null : (CaseFile)cases.get(cases.size() - 1));
            if (match == null) {
               this.alert(AlertType.ERROR, L10n.t("casemaker.testPlay.failed"));
               return;
            }

            this.shell.startSinglePlayerSession();
            this.shell.launchSinglePlayerCase(match, "en");
            this.toBack();
         } catch (Exception var4) {
            this.alert(AlertType.ERROR, L10n.t("casemaker.testPlay.failed"));
         }

      }
   }

   private void openCase() {
      List<CaseFile> cases = CaseLoader.loadCases("cases");
      if (cases.isEmpty()) {
         this.alert(AlertType.INFORMATION, L10n.t("casemaker.open.none"));
      } else {
         Map<String, CaseFile> byTitle = new LinkedHashMap();

         for(CaseFile c : cases) {
            byTitle.put(CaseTitles.displayTitle(c), c);
         }

         ChoiceDialog<String> dialog = new ChoiceDialog((String)byTitle.keySet().iterator().next(), byTitle.keySet());
         dialog.setTitle(L10n.t("casemaker.open"));
         dialog.setHeaderText((String)null);
         dialog.setContentText(L10n.t("casemaker.open.pick"));
         themeDialog(dialog.getDialogPane());
         dialog.showAndWait().ifPresent((title) -> {
            CaseFile selected = (CaseFile)byTitle.get(title);
            if (selected != null) {
               if (this.confirmCloseAllowed()) {
                  CaseDraft loaded = CaseDraftLoader.load(selected);
                  this.shell.replaceCaseMaker(loaded);
               }
            }
         });
      }
   }

   private void alert(Alert.AlertType type, String message) {
      Alert alert = new Alert(type);
      alert.setHeaderText((String)null);
      alert.setContentText(message);
      themeDialog(alert.getDialogPane());
      alert.showAndWait();
   }

   static void themeDialog(DialogPane pane) {
      Theme.install(pane);
      LocaleStyling.apply(pane);
      pane.getStyleClass().add("casemaker-dialog");
      pane.setGraphic((Node)null);
   }

   private void addRoomFromField() {
      String name = this.newRoomField.getText() == null ? "" : this.newRoomField.getText().trim();
      if (!name.isEmpty()) {
         RoomDraft room = this.draft.addRoom(name);
         this.createNode(room);
         this.roomList.getItems().add(room);
         this.newRoomField.clear();
         this.roomList.getSelectionModel().select(room);
         this.refreshRoomList();
         this.refreshConnectivity();
         this.objectPlacementView.refreshRooms();
         this.suspectEditorView.refreshRooms();
         this.suspectPlacementView.refreshRooms();
      }
   }

   private void renameSelectedRoom() {
      if (this.selectedRoom != null) {
         TextInputDialog dialog = new TextInputDialog(this.selectedRoom.getName());
         dialog.setTitle(L10n.t("casemaker.rooms.renameTitle"));
         dialog.setHeaderText((String)null);
         dialog.setContentText(L10n.t("casemaker.rooms.renamePrompt"));
         themeDialog(dialog.getDialogPane());
         Optional<String> result = dialog.showAndWait();
         result.map(String::trim).filter((name) -> !name.isEmpty()).ifPresent((name) -> {
            this.draft.renameRoom(this.selectedRoom, name);
            StackPane node = (StackPane)this.nodeByRoom.get(this.selectedRoom);
            if (node != null) {
               Object patt0$temp = node.getChildren().get(0);
               if (patt0$temp instanceof Label) {
                  Label label = (Label)patt0$temp;
                  label.setText(this.roomLabel(this.selectedRoom));
               }
            }

            this.refreshRoomList();
            this.redrawEdges();
            this.objectPlacementView.refreshRooms();
            this.suspectEditorView.refreshRooms();
            this.suspectPlacementView.refreshRooms();
         });
      }
   }

   private void deleteSelectedRoom() {
      if (this.selectedRoom != null) {
         RoomDraft toRemove = this.selectedRoom;
         this.draft.removeRoom(toRemove);
         StackPane node = (StackPane)this.nodeByRoom.remove(toRemove);
         if (node != null) {
            this.graphCanvas.getChildren().remove(node);
         }

         this.roomList.getItems().remove(toRemove);
         this.selectedRoom = null;
         this.clearLinkSource();
         this.refreshRoomList();
         this.redrawEdges();
         this.refreshConnectivity();
         this.objectPlacementView.refreshRooms();
         this.suspectEditorView.refreshRooms();
         this.suspectPlacementView.refreshRooms();
      }
   }

   private void setSelectedAsStarting() {
      if (this.selectedRoom != null) {
         this.draft.setStartingRoom(this.selectedRoom);
         this.refreshRoomList();
         this.refreshNodeStyles();
         this.refreshConnectivity();
      }
   }

   /**
    * The label shown for a room in the map and the room list: its localized Display Name for the
    * current editing language when the author has set one, otherwise the Universal (command) name.
    * Display-only — a room's identity and neighbour wiring always stay keyed on the Universal name.
    */
   private String roomLabel(RoomDraft room) {
      if (room == null) {
         return "";
      }
      String display = room.displayNameText().get(this.draft.getAuthoringLanguage());
      return display != null && !display.isBlank() ? display : room.getName();
   }

   /** Re-labels the map nodes and room list for the current editing language (no structural change). */
   private void refreshRoomLabels() {
      for (Map.Entry<RoomDraft, StackPane> entry : this.nodeByRoom.entrySet()) {
         StackPane node = entry.getValue();
         if (node != null && !node.getChildren().isEmpty()
             && node.getChildren().get(0) instanceof Label label) {
            label.setText(this.roomLabel(entry.getKey()));
         }
      }
      this.roomList.refresh();
   }

   private void createNode(RoomDraft room) {
      Label label = new Label(this.roomLabel(room));
      StackPane node = new StackPane(new Node[]{label});
      node.getStyleClass().add("casemaker-room-node");
      int index = this.nodeByRoom.size();
      node.setLayoutX((double)40.0F + (double)(index % 5) * (double)160.0F);
      node.setLayoutY((double)40.0F + (double)(index / 5) * (double)110.0F);
      node.setOnMousePressed((e) -> {
         this.selectRoom(room, true);
         this.dragAnchorX = e.getSceneX();
         this.dragAnchorY = e.getSceneY();
         this.dragNodeX = node.getLayoutX();
         this.dragNodeY = node.getLayoutY();
         e.consume();
      });
      node.setOnMouseDragged((e) -> {
         if (!this.linkModeToggle.isSelected()) {
            node.setLayoutX(Math.max((double)0.0F, this.dragNodeX + (e.getSceneX() - this.dragAnchorX)));
            node.setLayoutY(Math.max((double)0.0F, this.dragNodeY + (e.getSceneY() - this.dragAnchorY)));
            this.redrawEdges();
            e.consume();
         }
      });
      node.setOnMouseClicked((e) -> {
         if (this.linkModeToggle.isSelected()) {
            this.handleLinkClick(room);
         }

         e.consume();
      });
      this.nodeByRoom.put(room, node);
      this.graphCanvas.getChildren().add(node);
      this.refreshNodeStyles();
   }

   private void handleLinkClick(RoomDraft room) {
      if (this.linkSource == null) {
         this.linkSource = room;
         this.refreshNodeStyles();
      } else if (this.linkSource == room) {
         this.clearLinkSource();
      } else {
         if (this.isLinked(this.linkSource, room)) {
            this.draft.unlinkRooms(this.linkSource, room);
         } else {
            this.draft.linkRooms(this.linkSource, this.inferDirection(this.linkSource, room), room);
         }

         this.clearLinkSource();
         this.redrawEdges();
         this.refreshConnectivity();
      }
   }

   private boolean isLinked(RoomDraft a, RoomDraft b) {
      return a.getNeighbors().containsValue(b) || b.getNeighbors().containsValue(a);
   }

   private void clearLinkSource() {
      this.linkSource = null;
      this.refreshNodeStyles();
   }

   private String inferDirection(RoomDraft from, RoomDraft to) {
      StackPane a = (StackPane)this.nodeByRoom.get(from);
      StackPane b = (StackPane)this.nodeByRoom.get(to);
      double dx = centerX(b) - centerX(a);
      double dy = centerY(b) - centerY(a);
      double angle = Math.toDegrees(Math.atan2(-dy, dx));
      if (!(angle < (double)-157.5F) && !(angle >= (double)157.5F)) {
         if (angle < (double)-112.5F) {
            return "southwest";
         } else if (angle < (double)-67.5F) {
            return "south";
         } else if (angle < (double)-22.5F) {
            return "southeast";
         } else if (angle < (double)22.5F) {
            return "east";
         } else if (angle < (double)67.5F) {
            return "northeast";
         } else {
            return angle < (double)112.5F ? "north" : "northwest";
         }
      } else {
         return "west";
      }
   }

   private void redrawEdges() {
      this.graphCanvas.getChildren().removeAll(this.edgeNodes);
      this.edgeNodes.clear();
      Set<RoomDraft> done = Collections.newSetFromMap(new IdentityHashMap());

      for(RoomDraft room : this.draft.getRooms()) {
         StackPane fromNode = (StackPane)this.nodeByRoom.get(room);
         if (fromNode != null) {
            for(Map.Entry<String, RoomDraft> link : room.getNeighbors().entrySet()) {
               RoomDraft target = (RoomDraft)link.getValue();
               StackPane toNode = (StackPane)this.nodeByRoom.get(target);
               if (toNode != null && !done.contains(target)) {
                  Line line = new Line(centerX(fromNode), centerY(fromNode), centerX(toNode), centerY(toNode));
                  line.getStyleClass().add("casemaker-edge");
                  Text dir = new Text((centerX(fromNode) + centerX(toNode)) / (double)2.0F, (centerY(fromNode) + centerY(toNode)) / (double)2.0F, capitalize((String)link.getKey()));
                  dir.getStyleClass().add("casemaker-edge-label");
                  this.edgeNodes.add(line);
                  this.edgeNodes.add(dir);
               }
            }

            done.add(room);
         }
      }

      this.graphCanvas.getChildren().addAll(0, this.edgeNodes);
   }

   private void refreshNodeStyles() {
      for(Map.Entry<RoomDraft, StackPane> entry : this.nodeByRoom.entrySet()) {
         StackPane node = (StackPane)entry.getValue();
         node.getStyleClass().removeAll(new String[]{"selected", "starting", "link-source"});
         if (entry.getKey() == this.draft.getStartingRoom()) {
            node.getStyleClass().add("starting");
         }

         if (entry.getKey() == this.selectedRoom) {
            node.getStyleClass().add("selected");
         }

         if (entry.getKey() == this.linkSource) {
            node.getStyleClass().add("link-source");
         }
      }

   }

   private void selectRoom(RoomDraft room, boolean syncList) {
      this.selectedRoom = room;
      if (syncList && room != null) {
         this.roomList.getSelectionModel().select(room);
      }

      this.syncRoomBackgroundPicker(room);
      this.refreshNodeStyles();
   }

   private void syncRoomBackgroundPicker(RoomDraft room) {
      this.syncingRoomBackground = true;

      try {
         this.roomBackgroundPicker.setDisable(room == null);
         String selectedId = "";
         if (room != null && room.getImagePath() != null) {
            for(String presetId : PresetArtResolver.ROOM_PRESET_IDS) {
               if (PresetArtResolver.roomPresetPath(presetId).equals(room.getImagePath())) {
                  selectedId = presetId;
                  break;
               }
            }
         }

         this.roomBackgroundPicker.setValue(selectedId);
      } finally {
         this.syncingRoomBackground = false;
      }

   }

   private void onRoomBackgroundPicked(String presetId) {
      if (!this.syncingRoomBackground && this.selectedRoom != null) {
         if (presetId != null && !presetId.isBlank()) {
            this.selectedRoom.setImagePath(PresetArtResolver.roomPresetPath(presetId));
         } else {
            this.selectedRoom.setImagePath((String)null);
         }

      }
   }

   private void refreshRoomList() {
      this.roomList.refresh();
   }

   private void refreshConnectivity() {
      this.connectivityBanner.getStyleClass().removeAll(new String[]{"ok", "warn"});
      if (this.draft.getRooms().isEmpty()) {
         this.connectivityBanner.setText(L10n.t("casemaker.connectivity.noRooms"));
      } else if (this.draft.getStartingRoom() == null) {
         this.connectivityBanner.setText(L10n.t("casemaker.connectivity.noStart"));
         this.connectivityBanner.getStyleClass().add("warn");
      } else {
         List<RoomDraft> unreachable = this.draft.unreachableRooms();
         if (unreachable.isEmpty()) {
            this.connectivityBanner.setText(L10n.t("casemaker.connectivity.ok"));
            this.connectivityBanner.getStyleClass().add("ok");
         } else {
            String names = (String)unreachable.stream().map(RoomDraft::getName).collect(Collectors.joining(", "));
            this.connectivityBanner.setText(L10n.t("casemaker.connectivity.unreachable", new Object[]{names}));
            this.connectivityBanner.getStyleClass().add("warn");
         }

      }
   }

   private void chooseImageInto(TextField target) {
      FileChooser chooser = new FileChooser();
      chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", new String[]{"*.png", "*.jpg", "*.jpeg", "*.gif"}));
      File file = chooser.showOpenDialog(this);
      if (file != null) {
         target.setText(file.getAbsolutePath());
      }

   }

   private static double centerX(Region node) {
      double width = node.getWidth() > (double)0.0F ? node.getWidth() : node.prefWidth((double)-1.0F);
      return node.getLayoutX() + width / (double)2.0F;
   }

   private static double centerY(Region node) {
      double height = node.getHeight() > (double)0.0F ? node.getHeight() : node.prefHeight((double)-1.0F);
      return node.getLayoutY() + height / (double)2.0F;
   }

   private static String capitalize(String s) {
      return s != null && !s.isEmpty() ? Character.toUpperCase(s.charAt(0)) + s.substring(1) : s;
   }

   private static String blankToNull(String s) {
      return s != null && !s.isBlank() ? s : null;
   }

   private static Region spacer() {
      Region spacer = new Region();
      HBox.setHgrow(spacer, Priority.ALWAYS);
      return spacer;
   }

   private static Label sectionLabel(String key) {
      Label label = new Label(L10n.t(key));
      label.getStyleClass().add("casemaker-section");
      return label;
   }

   private static Label fieldLabel(String key) {
      Label label = new Label(L10n.t(key));
      label.getStyleClass().add("sidebar-label");
      return label;
   }

   private final class RoomCell extends ListCell<RoomDraft> {
      protected void updateItem(RoomDraft room, boolean empty) {
         super.updateItem(room, empty);
         if (!empty && room != null) {
            String suffix = room == CaseMakerWindow.this.draft.getStartingRoom() ? L10n.t("casemaker.rooms.startingSuffix") : "";
            String var10001 = CaseMakerWindow.this.roomLabel(room);
            this.setText(var10001 + suffix);
         } else {
            this.setText((String)null);
         }

      }
   }
}

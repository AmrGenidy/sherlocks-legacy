package ui.pinboard;

import common.dto.JournalEntryDTO;
import common.dto.JournalEntryType;
import common.dto.pinboard.*;
import java.util.*;
import java.util.stream.Collectors;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;
import ui.i18n.L10n;
import ui.i18n.LocaleStyling;

public class PinboardController {

  private final Stage stage;
  private final Pane canvas;
  // The procedural cork texture behind the notes (GUI G5a / DEC-g5a-1): a tiled, mouse-transparent
  // backing layer kept at canvas child index 0, below the threads and cards. Built from
  // Palette.CORK
  // so it follows the theme (the board is rebuilt in-theme on a theme switch). Presentation only.
  private Region corkPlate;
  private static final double BOARD_SIZE = 2000; // matches the canvas pref size below
  // Fixed seed: the texture is stable across repaints / theme switches (no shimmer); arbitrary
  // value.
  private static final long CORK_SEED = 18950101L;
  private final List<PinboardItemModel> items = new ArrayList<>();
  private final List<PinboardLinkModel> links = new ArrayList<>();
  private final Map<String, Node> itemNodeMap = new HashMap<>();
  private final Map<PinboardLinkModel, Line> linkNodeMap = new HashMap<>();

  // New Side Panel Components
  private VBox evidencePanel;
  private VBox cluesBox;
  private VBox statementsBox;
  private VBox deductionsBox;
  private VBox suspectsBox;

  // State
  private boolean isLinkMode = false;
  private boolean isDeleteLinkMode = false;
  private String linkStartId = null;
  // The live oxblood thread that follows the cursor from the first picked item until the second is
  // chosen, or the pick is cancelled (Escape / click on empty board). Issue 03 / DEC-3.
  private Line pendingThread = null;
  private PinboardItemModel draggedItem = null;
  private PinboardItemModel selectedItem = null;
  // Multi-selection for Contradict feature
  private Set<PinboardItemModel> selectedItems = new HashSet<>();

  private double dragDeltaX, dragDeltaY;

  // New-note cascade (.scratch/ingame-fixes-3 issue 01): each added note is offset from the last by
  // CASCADE_OFFSET px and wraps back near the top-left every CASCADE_WRAP notes, so additions are
  // always visible and never stack directly on top of one another.
  private static final double CASCADE_OFFSET = 24;
  private static final int CASCADE_WRAP = 8;
  private int cascadeStep = 0;

  private Set<String> loadedSuspects = new HashSet<>();

  // UI Components
  private ScrollPane canvasScrollPane;
  private ComboBox<String> linkColorSelector;
  private BorderPane root;
  private ToolBar toolBar;

  // Read-only mode (.scratch/gui-review-enter-case): when reviewing a solved case the board is
  // shown
  // exactly as saved but cannot be changed — the toolbar is replaced by a "read only" hint and
  // every
  // mutating interaction (drag, resize, text edit, link add/remove, drag-drop add) is gated off.
  // Must
  // be set BEFORE the board is populated, since item/link nodes read it at creation.
  private boolean readOnly = false;

  // "Sync journal" only means anything in a shared (multiplayer) session — it is hidden entirely in
  // single player, where the board and the journal are already one and the same.
  private Button syncBtn;

  // Undo/redo (.scratch/gui-pinboard-undo): a memento stack of full board snapshots. A snapshot of
  // the state BEFORE each mutating action is pushed (drag/resize/text-edit push one entry per
  // gesture: captured on press / focus-gain, committed on release / focus-loss only if something
  // changed). Restoring replays the diff between now and the snapshot through the same
  // apply-locally + sendUpdate path a live edit takes, so multiplayer peers follow every undo.
  // Node-properties keys publishing a note body's edit-session controls (begin / commit), so
  // deselection and the new-note auto-edit route through the same bookkeeping as double-click.
  private static final String EDIT_BEGIN_PROP = "pinboard-edit-begin";
  private static final String EDIT_COMMIT_PROP = "pinboard-edit-commit";

  private static final int MAX_HISTORY = 50;
  private final ArrayDeque<PinboardStateDTO> undoStack = new ArrayDeque<>();
  private final ArrayDeque<PinboardStateDTO> redoStack = new ArrayDeque<>();
  // Snapshot captured when a drag / resize gesture starts; pushed on release only if the gesture
  // actually changed something, so a whole drag is ONE undo entry (and a plain click is none).
  private PinboardStateDTO pendingGestureSnapshot;
  private boolean gestureChanged;

  // Callbacks
  private Runnable onSyncRequest;
  private java.util.function.Consumer<PinboardUpdateDTO> onUpdateCallback;
  private java.util.function.Consumer<String> commandHandler;
  // Fired whenever the player creates a link (drags a thread) — lets the pinboard tutorial advance
  // on the link action (.scratch/gui-pinboard-tutorial).
  private Runnable onLinkCreated;

  public PinboardController() {
    this.stage = new Stage();
    ui.util.AppIcon.applyTo(this.stage);
    this.canvas = new Pane();
    initializeUI();
  }

  /**
   * Tags the board root with the active {@code read-scale-NNN} class so the note title/body
   * (.pinboard-item-*) follow the Settings "Reading text size" slider
   * (.scratch/gui-typography-readability Phase 2). Called by the owner after construction; the
   * board is rebuilt on a scale change, so creation-time application is sufficient.
   */
  public void applyReadingTextScale(double scale) {
    ui.util.ContentScaleStyling.apply(root, ui.util.ContentScale.READING_PREFIX, scale);
  }

  private void initializeUI() {
    root = new BorderPane();
    root.getStylesheets().add(getClass().getResource("/css/pinboard.css").toExternalForm());
    root.getStyleClass().add("pinboard-root");
    LocaleStyling.apply(root);

    // --- Toolbar ---
    toolBar = new ToolBar();
    toolBar.getStyleClass().add("pinboard-toolbar");

    Button addNoteBtn = new Button(L10n.t("pinboard.addNote"));
    addNoteBtn.setOnAction(e -> createNoteAtCenter());

    ToggleButton linkModeBtn = new ToggleButton(L10n.t("pinboard.linkMode"));

    ToggleButton deleteLinkModeBtn = new ToggleButton(L10n.t("pinboard.deleteLink"));
    deleteLinkModeBtn
        .selectedProperty()
        .addListener(
            (obs, oldVal, newVal) -> {
              isDeleteLinkMode = newVal;
              if (isDeleteLinkMode) {
                linkModeBtn.setSelected(false);
              }
            });

    linkModeBtn
        .selectedProperty()
        .addListener(
            (obs, oldVal, newVal) -> {
              isLinkMode = newVal;
              if (isLinkMode) {
                deleteLinkModeBtn.setSelected(false);
                if (!canvas.getStyleClass().contains("pinboard-canvas--linking")) {
                  canvas.getStyleClass().add("pinboard-canvas--linking"); // crosshair affordance
                }
              } else {
                canvas.getStyleClass().remove("pinboard-canvas--linking");
              }
              cancelPendingLink(); // abandon any half-made link when toggling (issue 03 / DEC-3)
            });

    // Items are the wire-protocol color codes; the converter renders the localized name.
    linkColorSelector = new ComboBox<>();
    linkColorSelector.getItems().addAll("GREEN", "YELLOW", "RED");
    linkColorSelector.setValue("RED");
    linkColorSelector.setPromptText(L10n.t("pinboard.linkColor"));
    linkColorSelector.setConverter(
        new javafx.util.StringConverter<>() {
          @Override
          public String toString(String code) {
            if (code == null) {
              return null;
            }
            switch (code) {
              case "GREEN":
                return L10n.t("pinboard.colorGreen");
              case "YELLOW":
                return L10n.t("pinboard.colorYellow");
              default:
                return L10n.t("pinboard.colorRed");
            }
          }

          @Override
          public String fromString(String string) {
            return string;
          }
        });

    Button deleteBtn = new Button(L10n.t("pinboard.delete"));
    deleteBtn.setOnAction(e -> deleteSelectedItem());

    Button clearBtn = new Button(L10n.t("pinboard.clearBoard"));
    clearBtn.setOnAction(
        e -> {
          // Snapshot HERE, not inside clearCanvas — clearCanvas is also the applyState/reset
          // plumbing, where a snapshot would pollute the undo history.
          if (!items.isEmpty() || !links.isEmpty()) {
            pushUndoSnapshot();
          }
          clearCanvas(); // CHANGED: Calls clearCanvas instead of clearBoard
        });

    syncBtn = new Button(L10n.t("pinboard.syncJournal"));
    syncBtn.setOnAction(e -> syncJournal());

    Button contradictBtn = new Button(L10n.t("pinboard.contradict"));
    contradictBtn.setOnAction(e -> handleContradictButton());

    Button combineBtn = new Button(L10n.t("pinboard.combine"));
    combineBtn.setOnAction(e -> handleCombineButton());

    // Engraved, themed toolbar controls with clear hover/press/active states (issue 02 / DEC-2).
    for (Button b :
        java.util.List.of(addNoteBtn, deleteBtn, clearBtn, syncBtn, contradictBtn, combineBtn)) {
      b.getStyleClass().add("pinboard-tool-button");
    }
    linkModeBtn.getStyleClass().add("pinboard-tool-toggle");
    deleteLinkModeBtn.getStyleClass().add("pinboard-tool-toggle");
    linkColorSelector.getStyleClass().add("pinboard-tool-combo");

    toolBar
        .getItems()
        .addAll(
            addNoteBtn,
            linkModeBtn,
            deleteLinkModeBtn,
            linkColorSelector,
            new Separator(),
            deleteBtn,
            clearBtn,
            contradictBtn,
            combineBtn,
            new Separator(),
            syncBtn);
    root.setTop(toolBar);

    // --- Center: Canvas ---
    canvas.getStyleClass().add("pinboard-canvas");
    canvas.setPrefSize(BOARD_SIZE, BOARD_SIZE); // Large canvas

    // The cork texture (GUI G5a / DEC-g5a-1): a tiled procedural fleck layer over the flat -sl-cork
    // fill, behind everything else. As a child of `canvas` it shares the zoom transform for free,
    // and
    // it's mouse-transparent so drag / link / drop events pass straight through to the canvas.
    corkPlate = buildCorkPlate();
    canvas.getChildren().add(corkPlate);
    // The board is spatial, not textual: pin its coordinate space left-to-right so
    // drag math and synced item positions stay identical in both languages.
    canvas.setNodeOrientation(javafx.geometry.NodeOrientation.LEFT_TO_RIGHT);

    // Zoom support
    Scale scale = new Scale(1, 1);
    canvas.getTransforms().add(scale);

    canvasScrollPane = new ScrollPane(canvas);
    canvasScrollPane.getStyleClass().add("pinboard-board-frame"); // Ink frame around the cork board
    canvasScrollPane.setPannable(true);
    canvasScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    canvasScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

    // Mouse wheel zoom
    canvasScrollPane.addEventFilter(
        ScrollEvent.SCROLL,
        e -> {
          if (e.isControlDown()) {
            double delta = e.getDeltaY();
            double scaleFactor = (delta > 0) ? 1.1 : 0.9;
            double newScaleX = scale.getX() * scaleFactor;
            double newScaleY = scale.getY() * scaleFactor;

            // Clamp zoom
            if (newScaleX >= 0.5 && newScaleX <= 3.0) {
              scale.setX(newScaleX);
              scale.setY(newScaleY);
            }
            e.consume();
          }
        });

    // Link mode (issue 03 / DEC-3): the pending thread follows the cursor, and a click that reaches
    // the empty board (item clicks are consumed) cancels a half-made link.
    canvas.addEventHandler(
        MouseEvent.MOUSE_MOVED,
        e -> {
          if (isLinkMode && pendingThread != null) {
            pendingThread.setEndX(e.getX());
            pendingThread.setEndY(e.getY());
          }
        });
    canvas.addEventHandler(
        MouseEvent.MOUSE_CLICKED,
        e -> {
          if (isLinkMode && linkStartId != null) {
            cancelPendingLink();
          }
          // Card clicks are consumed, so this is a click on the bare cork: drop the selection (a
          // highlighted note stops being highlighted) and take the focus so an in-progress note edit
          // commits (its TextArea's focus-loss listener does the commit).
          clearSelection();
          canvas.requestFocus();
        });

    // Canvas Drag-Drop Logic (Target). Disabled in read-only review: no new items from the panel.
    canvas.setOnDragOver(
        e -> {
          if (!readOnly && e.getDragboard().hasContent(DataFormat.PLAIN_TEXT)) {
            e.acceptTransferModes(TransferMode.COPY);
          }
          e.consume();
        });

    canvas.setOnDragDropped(
        e -> {
          if (readOnly) {
            e.setDropCompleted(false);
            e.consume();
            return;
          }
          Dragboard db = e.getDragboard();
          boolean success = false;
          if (db.hasContent(DataFormat.PLAIN_TEXT)) {
            String content = db.getString(); // Format: ID|TITLE|TYPE|TEXT
            String[] parts = content.split("\\|", 4);
            if (parts.length >= 4) {
              String id = parts[0];
              String title = parts[1];
              String typeStr = parts[2];
              String text = parts[3];

              // Convert drop coordinates to local canvas coordinates
              Point2D localPoint = canvas.sceneToLocal(e.getSceneX(), e.getSceneY());

              createNodeFromDrop(id, title, typeStr, text, localPoint.getX(), localPoint.getY());
              success = true;
            }
          }
          e.setDropCompleted(success);
          e.consume();
        });

    root.setCenter(canvasScrollPane);

    // --- Right Panel: Journal Evidence (tidy themed list — issue 01 / DEC-1) ---
    evidencePanel = new VBox(8);
    evidencePanel.setPadding(new Insets(8));
    evidencePanel.getStyleClass().add("pinboard-evidence-panel");

    cluesBox = new VBox(5);
    statementsBox = new VBox(5);
    deductionsBox = new VBox(5);
    suspectsBox = new VBox(5);

    // Section headers replace the oversized Accordion/TitledPane headers that clipped and forced a
    // horizontal scrollbar; they wrap and stay ~16–18px via .section-header (pinboard.css).
    VBox evidenceList =
        new VBox(
            12,
            evidenceSection(L10n.t("pinboard.clues"), cluesBox),
            evidenceSection(L10n.t("pinboard.suspectStatements"), statementsBox),
            evidenceSection(L10n.t("pinboard.deductions"), deductionsBox),
            evidenceSection(L10n.t("pinboard.suspects"), suspectsBox));
    evidenceList.getStyleClass().add("pinboard-evidence-list");

    ScrollPane rightScroll = new ScrollPane(evidenceList);
    rightScroll.setFitToWidth(true); // fit the panel width — wrap, never scroll horizontally
    rightScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    rightScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    rightScroll.setPrefWidth(260);
    rightScroll.getStyleClass().add("pinboard-sidebar");

    Label evidenceTitle = new Label(L10n.t("pinboard.journalEvidence"));
    evidenceTitle.getStyleClass().add("pinboard-evidence-title");
    evidenceTitle.setWrapText(true);
    evidenceTitle.setMaxWidth(Double.MAX_VALUE);

    evidencePanel.getChildren().addAll(evidenceTitle, rightScroll);
    VBox.setVgrow(rightScroll, Priority.ALWAYS);
    root.setRight(evidencePanel);

    Scene scene = new Scene(root, 1200, 900);
    // Escape steps back out of whatever is active: first it cancels a half-made link, then it
    // leaves
    // Link / Delete-link mode entirely, so one key always returns the board to plain selection.
    // Delete removes the selected item, exactly like the Delete button (but never while typing in a
    // note, where Delete must keep its normal text meaning).
    scene.addEventFilter(
        KeyEvent.KEY_PRESSED,
        e -> {
          if (e.getCode() == KeyCode.ESCAPE) {
            boolean handled = false;
            if (linkStartId != null) {
              cancelPendingLink();
              handled = true;
            }
            if (linkModeBtn.isSelected()) {
              linkModeBtn.setSelected(false);
              handled = true;
            }
            if (deleteLinkModeBtn.isSelected()) {
              deleteLinkModeBtn.setSelected(false);
              handled = true;
            }
            if (handled) {
              e.consume();
            }
          } else if (e.getCode() == KeyCode.DELETE
              && !readOnly
              && !(e.getTarget() instanceof javafx.scene.control.TextInputControl)) {
            deleteSelectedItem();
            e.consume();
          } else if (isUndoCombo(e) || isRedoCombo(e)) {
            // Ctrl+Z / Ctrl+Shift+Z (or Ctrl+Y) — never while reviewing read-only, and never while
            // typing in a note, where the TextArea keeps its own text-level undo.
            if (!readOnly && !(e.getTarget() instanceof javafx.scene.control.TextInputControl)) {
              if (isUndoCombo(e)) {
                undo();
              } else {
                redo();
              }
              e.consume();
            }
          }
        });
    // The pinboard is its own Stage: it needs the base theme (which defines -sl-*) + the dark
    // override; pinboard.css only references those looked-up colours (DESIGN.md §8).
    ui.util.Theme.install(scene);
    stage.setScene(scene);
    stage.setTitle(L10n.t("toolbar.pinboard"));
    // DESIGN.md §4 (.scratch/responsive-resizing issue 03): sensible min size, 8px scale —
    // toolbar + pannable canvas + evidence sidebar stay usable.
    stage.setMinWidth(640);
    stage.setMinHeight(480);
  }

  // --- Data Population (Push Model) ---

  public void setJournalEntries(List<JournalEntryDTO> entries) {
    // Clear side panels (except suspects)
    cluesBox.getChildren().clear();
    statementsBox.getChildren().clear();
    deductionsBox.getChildren().clear();

    if (entries == null) return;

    for (JournalEntryDTO entry : entries) {
      addJournalEntryToPanel(entry);
    }
  }

  public void addJournalEntry(JournalEntryDTO entry) {
    addJournalEntryToPanel(entry);
  }

  private void addJournalEntryToPanel(JournalEntryDTO entry) {
    // Create draggable label
    Label label = new Label(entry.getTitle());
    label.setTooltip(new Tooltip(entry.getText()));
    label.setMaxWidth(Double.MAX_VALUE);
    label.setWrapText(true); // long titles wrap instead of widening the panel (issue 01 / DEC-1)
    label.setPadding(new Insets(5));
    label.getStyleClass().add("journal-entry-chip");

    // Drag Source Logic
    label.setOnDragDetected(
        e -> {
          Dragboard db = label.startDragAndDrop(TransferMode.COPY);
          ClipboardContent content = new ClipboardContent();
          // Format: ID|TITLE|TYPE|TEXT
          String payload =
              entry.getId()
                  + "|"
                  + entry.getTitle()
                  + "|"
                  + entry.getType().name()
                  + "|"
                  + entry.getText();
          content.putString(payload);
          db.setContent(content);
          e.consume();
        });

    // Add to appropriate box
    if (entry.getType() == JournalEntryType.CLUE) {
      cluesBox.getChildren().add(label);
    } else if (entry.getType() == JournalEntryType.SUSPECT_STATEMENT) {
      statementsBox.getChildren().add(label);
    } else if (entry.getType() == JournalEntryType.DEDUCTION) {
      deductionsBox.getChildren().add(label);
    }
  }

  public void setSuspects(List<String> suspectNames) {
    suspectsBox.getChildren().clear();
    loadedSuspects.clear();
    if (suspectNames == null) return;
    for (String name : suspectNames) {
      addSuspectName(name);
    }
  }

  public void addSuspectName(String suspectName) {
    if (suspectName == null || loadedSuspects.contains(suspectName)) return;

    loadedSuspects.add(suspectName);

    Label label = new Label(suspectName);
    label.setMaxWidth(Double.MAX_VALUE);
    label.setWrapText(true); // long names wrap instead of widening the panel (issue 01 / DEC-1)
    label.setPadding(new Insets(5));
    label.getStyleClass().add("suspect-chip");

    label.setOnDragDetected(
        e -> {
          Dragboard db = label.startDragAndDrop(TransferMode.COPY);
          ClipboardContent content = new ClipboardContent();
          // Format: ID|TITLE|TYPE|TEXT
          String id = "suspect:" + suspectName.toLowerCase().replace(" ", "_");
          String payload =
              id + "|" + suspectName + "|SUSPECT|" + L10n.t("pinboard.suspectContent", suspectName);
          content.putString(payload);
          db.setContent(content);
          e.consume();
        });

    suspectsBox.getChildren().add(label);
  }

  /** A wrapping ~16–18px section header over its list of chips (issue 01 / DEC-1). */
  private VBox evidenceSection(String title, VBox body) {
    Label header = new Label(title);
    header.getStyleClass().add("section-header");
    header.setWrapText(true);
    header.setMaxWidth(Double.MAX_VALUE);
    VBox section = new VBox(4, header, body);
    section.getStyleClass().add("pinboard-evidence-section");
    return section;
  }

  private void createNodeFromDrop(
      String refId, String title, String typeStr, String text, double x, double y) {
    pushUndoSnapshot();
    PinboardItemModel item = new PinboardItemModel();
    item.setTitle(title);
    item.setContent(text);
    item.setRelatedJournalEntryId(refId);
    item.setX(x);
    item.setY(y);
    item.setWidth(200);
    item.setHeight(150);

    // Type styling
    try {
      PinboardItemModel.ItemType type = PinboardItemModel.ItemType.valueOf(typeStr);
      item.setType(type);

      switch (typeStr) {
        case "CLUE":
          item.setColor("#add8e6");
          break; // Light Blue
        case "SUSPECT_STATEMENT":
          item.setColor("#ffcccb");
          break; // Light Red
        case "DEDUCTION":
          item.setColor("#90ee90");
          break; // Light Green
        case "SUSPECT":
          item.setColor("#ffb347");
          break; // Orange (Pastel Orange)
        default:
          item.setColor("#e0e0e0");
          break;
      }
    } catch (Exception e) {
      item.setType(PinboardItemModel.ItemType.EVIDENCE);
      item.setColor("#e0e0e0");
    }

    addItemToBoard(item);

    PinboardUpdateDTO update = new PinboardUpdateDTO();
    update.setType(PinboardUpdateDTO.UpdateType.ADD_ITEM);
    update.setItem(toDTO(item));
    sendUpdate(update);
  }

  /**
   * Shows the "Sync journal" tool only in a shared (multiplayer) session. In single player the board
   * and the journal are already the same thing, so the button is hidden AND unmanaged — it takes no
   * space in the toolbar and cannot be reached.
   */
  public void setMultiplayer(boolean multiplayer) {
    if (syncBtn != null) {
      syncBtn.setVisible(multiplayer);
      syncBtn.setManaged(multiplayer);
    }
  }

  public void show() {
    stage.show();
    stage.toFront();
  }

  public boolean isShowing() {
    return stage.isShowing();
  }

  /**
   * The Pinboard's own top-level window. The board is its own {@link Stage}, so a reveal added to
   * the main game window renders <em>behind</em> it; the in-game screen uses this to float a reveal
   * popup as an owned child stage that stacks above the board (.scratch/gui-pinboard-reveal-float).
   */
  public Stage getStage() {
    return stage;
  }

  /** Closes the Pinboard window (e.g. when the shell rebuilds windows on language switch). */
  public void close() {
    stage.close();
  }

  /**
   * Enters/leaves strictly read-only review mode (.scratch/gui-review-enter-case): the editing
   * toolbar is replaced by a "read only" hint and every mutating interaction is gated off (drag,
   * resize, text edit, link add/remove, drag-drop add); the board still renders, pans and scrolls
   * so the player sees it exactly as saved. Call this BEFORE populating the board ({@link
   * #applyState}), since item/link nodes read the flag when they are created.
   */
  public void setReadOnly(boolean readOnly) {
    this.readOnly = readOnly;
    if (root != null) {
      root.setTop(readOnly ? readOnlyHint() : toolBar);
    }
  }

  /** A small themed "Reviewing — read only" strip shown in place of the toolbar while reviewing. */
  private Region readOnlyHint() {
    Label hint = new Label(L10n.t("review.badge"));
    hint.getStyleClass().add("pinboard-readonly-hint");
    ToolBar bar = new ToolBar(hint);
    bar.getStyleClass().add("pinboard-toolbar");
    return bar;
  }

  public void reset() {
    resetContent();
  }

  // CHANGED: Renamed from clearBoard to resetContent for clarity
  public void resetContent() {
    clearCanvas();

    // Also clear side panels
    cluesBox.getChildren().clear();
    statementsBox.getChildren().clear();
    deductionsBox.getChildren().clear();
    suspectsBox.getChildren().clear();
    loadedSuspects.clear();
  }

  // NEW: Clears only canvas nodes and links
  public void clearCanvas() {
    items.clear();
    links.clear();
    canvas.getChildren().clear();
    itemNodeMap.clear();
    linkNodeMap.clear();
    // Preserve the cork backing layer through a clear (GUI G5a): re-seat it at index 0 so the next
    // items/threads still draw on cork, not on a bare canvas.
    if (corkPlate != null) {
      canvas.getChildren().add(0, corkPlate);
    }
  }

  /**
   * Insertion index just above the cork backing layer (GUI G5a) — threads go here so they sit on
   * the cork but under the cards. Returns 0 if the plate isn't present (defensive).
   */
  private int corkLayerTop() {
    return (corkPlate != null && canvas.getChildren().contains(corkPlate)) ? 1 : 0;
  }

  // --- Note Logic ---

  private void createNoteAtCenter() {
    pushUndoSnapshot();
    // Base near the current viewport top-left so the note is visible wherever the board is panned,
    // plus a cascading offset so consecutive notes never land on top of each other (issue 01).
    double baseX = Math.abs(canvasScrollPane.getViewportBounds().getMinX()) + 60;
    double baseY = Math.abs(canvasScrollPane.getViewportBounds().getMinY()) + 60;
    double cascade = NoteCascade.offsetFor(cascadeStep, CASCADE_OFFSET, CASCADE_WRAP);
    cascadeStep++;

    PinboardItemModel note = new PinboardItemModel();
    note.setType(PinboardItemModel.ItemType.NOTE);
    note.setTitle(L10n.t("pinboard.newNoteTitle"));
    note.setContent(L10n.t("pinboard.newNoteContent"));
    note.setX(baseX + cascade);
    note.setY(baseY + cascade);
    note.setColor("#fdfd96");

    addItemToBoard(note);

    // Make the just-added note obvious: bring it to the front, select it alone, and start it in
    // edit
    // mode so the player can immediately type into the note they created (issue 01).
    Node node = itemNodeMap.get(note.getId());
    if (node != null) {
      node.toFront();
      clearSelection();
      selectItem(note, node);
      javafx.application.Platform.runLater(
          () -> {
            Node content = node.lookup(".pinboard-item-content");
            if (content instanceof TextArea area
                && area.getProperties().get(EDIT_BEGIN_PROP) instanceof Runnable begin) {
              // The body is inert until an edit begins — a brand-new note begins one immediately,
              // through the same session bookkeeping as a double-click.
              begin.run();
              area.selectAll();
            }
          });
    }

    PinboardUpdateDTO update = new PinboardUpdateDTO();
    update.setType(PinboardUpdateDTO.UpdateType.ADD_ITEM);
    update.setItem(toDTO(note));
    sendUpdate(update);
  }

  private void addItemToBoard(PinboardItemModel item) {
    items.add(item);
    Node node = createItemNode(item);
    itemNodeMap.put(item.getId(), node);
    canvas.getChildren().add(node);
    node.setLayoutX(item.getX());
    node.setLayoutY(item.getY());
  }

  /**
   * The cork backing layer (GUI G5a / DEC-g5a-1): a board-sized {@link Region} tiled with the
   * procedural {@link CorkTexture} in the active {@link ui.util.Palette#CORK} tone.
   * Mouse-transparent so it never blocks board interaction; the tile repeats at natural size so the
   * flecks stay fine at 1.0× zoom (and scale with the board's zoom transform like everything else
   * on the canvas).
   */
  private Region buildCorkPlate() {
    Region plate = new Region();
    plate.setPrefSize(BOARD_SIZE, BOARD_SIZE);
    plate.setMouseTransparent(true);
    javafx.scene.image.WritableImage tile = CorkTexture.makeTile(ui.util.Palette.CORK, CORK_SEED);
    plate.setBackground(
        new Background(
            new BackgroundImage(
                tile,
                BackgroundRepeat.REPEAT,
                BackgroundRepeat.REPEAT,
                BackgroundPosition.DEFAULT,
                BackgroundSize.DEFAULT)));
    return plate;
  }

  /**
   * A small drawn brass pin (ligne claire via {@code .pinboard-pin}); never intercepts the mouse.
   */
  private Region brassPin() {
    Region pin = new Region();
    pin.getStyleClass().add("pinboard-pin");
    pin.setMinSize(12, 12);
    pin.setPrefSize(12, 12);
    pin.setMaxSize(12, 12);
    pin.setMouseTransparent(true);
    return pin;
  }

  private Node createItemNode(PinboardItemModel item) {
    VBox box = new VBox(2);
    box.setPrefSize(item.getWidth(), item.getHeight());
    box.setMinSize(100, 80); // Minimum size
    box.getStyleClass().add("pinboard-item");
    // Type modifier drives the paper tint via pinboard.css (.pinboard-item--<type>).
    String typeMod =
        item.getType() != null ? item.getType().name().toLowerCase().replace("_", "-") : "evidence";
    box.getStyleClass().add("pinboard-item--" + typeMod);

    Label titleLabel = new Label(item.getTitle());
    titleLabel.getStyleClass().add("pinboard-item-title");
    titleLabel.setMaxWidth(Double.MAX_VALUE);

    TextArea contentArea = new TextArea(item.getContent());
    contentArea.getStyleClass().add("pinboard-item-content");
    contentArea.setWrapText(true);
    // The body is INERT by default (.scratch/gui-pinboard-note-clicks): mouse-transparent so a
    // click anywhere on the card — including over the text — selects it, Ctrl+clicks into a
    // multi-selection, or picks it in Link mode, and not focus-traversable so Tab never lands in a
    // non-editing note. Double-clicking the card starts an edit (never in read-only review);
    // losing focus, Escape, or deselection commits the text and returns the body to inert.
    contentArea.setEditable(false);
    contentArea.setMouseTransparent(true);
    contentArea.setFocusTraversable(false);
    VBox.setVgrow(contentArea, Priority.ALWAYS);

    // Resize Handle — hidden + inert in read-only review (no resizing).
    Label resizeHandle = new Label("◢");
    resizeHandle.getStyleClass().add("pinboard-resize-handle");
    resizeHandle.setAlignment(Pos.BOTTOM_RIGHT);
    resizeHandle.setVisible(!readOnly);
    resizeHandle.setManaged(!readOnly);

    HBox bottomBar = new HBox(resizeHandle);
    bottomBar.setAlignment(Pos.BOTTOM_RIGHT);
    bottomBar.setPadding(new Insets(0, 2, 0, 0));

    if (!readOnly) {
      resizeHandle.setOnMousePressed(
          e -> {
            // A whole resize is ONE undo entry: snapshot now, push on release if it changed.
            pendingGestureSnapshot = getState();
            gestureChanged = false;
            e.consume(); // Prevent drag of parent
          });
      resizeHandle.setOnMouseDragged(
          e -> {
            Point2D mouseLocal = canvas.sceneToLocal(e.getSceneX(), e.getSceneY());
            double newW = Math.max(100, mouseLocal.getX() - item.getX());
            double newH = Math.max(80, mouseLocal.getY() - item.getY());

            box.setPrefSize(newW, newH);
            item.setWidth(newW);
            item.setHeight(newH);
            gestureChanged = true;
            updateLinks(item);
            e.consume();
          });
      resizeHandle.setOnMouseReleased(
          e -> {
            if (gestureChanged) {
              pushUndoSnapshot(pendingGestureSnapshot);
              // Commit the resize like a move: one RESIZE_ITEM per gesture (previously a local
              // resize was never synced at all, unlike drag's MOVE_ITEM).
              PinboardUpdateDTO update = new PinboardUpdateDTO();
              update.setType(PinboardUpdateDTO.UpdateType.RESIZE_ITEM);
              update.setTargetId(item.getId());
              update.setItem(toDTO(item));
              sendUpdate(update);
            }
            pendingGestureSnapshot = null;
            e.consume();
          });
    }

    contentArea.textProperty().addListener((obs, o, n) -> item.setContent(n));

    // One edit session = ONE undo entry and ONE commit: beginEdit snapshots the pre-edit state and
    // wakes the body up; commitEdit (idempotent) pushes the undo entry if the text changed, sends
    // the UPDATE_CONTENT broadcast, and puts the body back to inert. Commit triggers: focus loss,
    // Escape, deselection (stopEditing).
    final PinboardStateDTO[] editSnapshot = new PinboardStateDTO[1];
    final String[] contentAtEditStart = new String[1];
    final boolean[] editing = {false};
    Runnable commitEdit =
        () -> {
          if (!editing[0]) {
            return;
          }
          editing[0] = false;
          // A no-op edit (opened, nothing typed) neither burns an undo entry nor hits the wire.
          if (!Objects.equals(contentAtEditStart[0], item.getContent())) {
            if (editSnapshot[0] != null) {
              pushUndoSnapshot(editSnapshot[0]);
            }
            PinboardUpdateDTO update = new PinboardUpdateDTO();
            update.setType(PinboardUpdateDTO.UpdateType.UPDATE_CONTENT);
            update.setTargetId(item.getId());
            update.setValue(item.getContent());
            sendUpdate(update);
          }
          editSnapshot[0] = null;
          contentArea.setEditable(false);
          contentArea.setMouseTransparent(true);
        };
    Runnable beginEdit =
        () -> {
          if (readOnly || editing[0]) {
            return;
          }
          editing[0] = true;
          editSnapshot[0] = getState();
          contentAtEditStart[0] = item.getContent();
          contentArea.setMouseTransparent(false);
          contentArea.setEditable(true);
          contentArea.requestFocus();
          contentArea.positionCaret(
              contentArea.getText() != null ? contentArea.getText().length() : 0);
        };
    // Published on the node so stopEditing (deselection) and createNoteAtCenter (a brand-new note
    // starts editing at once) route through the SAME session bookkeeping.
    contentArea.getProperties().put(EDIT_BEGIN_PROP, beginEdit);
    contentArea.getProperties().put(EDIT_COMMIT_PROP, commitEdit);

    contentArea
        .focusedProperty()
        .addListener(
            (obs, oldVal, newVal) -> {
              if (!newVal) {
                commitEdit.run();
              }
            });

    // Escape while editing = commit and leave the edit.
    contentArea.addEventHandler(
        KeyEvent.KEY_PRESSED,
        e -> {
          if (e.getCode() == KeyCode.ESCAPE) {
            commitEdit.run();
            canvas.requestFocus();
            e.consume();
          }
        });

    StackPane titleStack = new StackPane(titleLabel);
    StackPane.setAlignment(titleLabel, Pos.CENTER_LEFT);
    StackPane.setMargin(titleLabel, new Insets(0, 5, 0, 5));

    // A brass pin tacking the card to the cork (issue 04 / DEC-7): drawn ligne-claire via CSS,
    // mouse-transparent so it never intercepts drag / link / select.
    Region brassPin = brassPin();
    titleStack.getChildren().add(brassPin);
    StackPane.setAlignment(brassPin, Pos.TOP_CENTER);
    StackPane.setMargin(brassPin, new Insets(-4, 0, 0, 0));

    // Link logic & Selection
    box.setOnMouseClicked(
        e -> {
          if (isLinkMode) {
            handleLinkClick(item);
          } else {
            // Delete-link mode now deletes by clicking the thread itself (issue 03 / DEC-4), so an
            // item click here is just selection.
            boolean isMultiSelect = e.isControlDown() || e.isShiftDown();
            handleSelection(item, box, isMultiSelect);
            // Double-click starts editing the body (the inert TextArea lets the click through, so
            // this works anywhere on the card, including over the text). Never in review.
            if (!isMultiSelect && e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
              beginEdit.run();
            }
          }
          e.consume();
        });

    box.getChildren().addAll(titleStack, contentArea, bottomBar);

    // Dragging logic on the canvas
    makeDraggable(box, item);

    return box;
  }

  private void handleSelection(PinboardItemModel item, Node node, boolean isMultiSelect) {
    if (!isMultiSelect) {
      // Deselect all
      clearSelection();
      selectItem(item, node);
    } else {
      // Toggle selection
      if (selectedItems.contains(item)) {
        deselectItem(item);
      } else {
        selectItem(item, node);
      }
    }
  }

  private void selectItem(PinboardItemModel item, Node node) {
    selectedItems.add(item);
    selectedItem = item; // Keep track of last selected for single-item operations
    if (!node.getStyleClass().contains("pinboard-item--selected")) {
      node.getStyleClass().add("pinboard-item--selected");
    }
  }

  private void deselectItem(PinboardItemModel item) {
    selectedItems.remove(item);
    if (selectedItem == item) selectedItem = null;

    Node node = itemNodeMap.get(item.getId());
    if (node != null) {
      node.getStyleClass().remove("pinboard-item--selected");
      stopEditing(node);
    }
  }

  /**
   * Ends an in-progress body edit on a card (deselection commits, like focus-loss and Escape): the
   * card's commit runnable pushes the undo entry, broadcasts UPDATE_CONTENT and re-inerts the body;
   * it is idempotent, so the focus-loss it may also trigger cannot double-commit.
   */
  private void stopEditing(Node node) {
    Node content = node.lookup(".pinboard-item-content");
    if (content instanceof TextArea area
        && area.getProperties().get(EDIT_COMMIT_PROP) instanceof Runnable commit) {
      commit.run();
      if (area.isFocused()) {
        canvas.requestFocus();
      }
    }
  }

  private void clearSelection() {
    for (PinboardItemModel item : new ArrayList<>(selectedItems)) {
      deselectItem(item);
    }
    selectedItems.clear();
    selectedItem = null;
  }

  private void deleteSelectedItem() {
    if (selectedItems.isEmpty()) {
      return;
    }
    pushUndoSnapshot();
    // Delete all selected items
    for (PinboardItemModel item : new ArrayList<>(selectedItems)) {
      removeItem(item);
    }
    selectedItems.clear();
    selectedItem = null;
  }

  private void handleContradictButton() {
    if (selectedItems.size() != 2) {
      showAlert(L10n.t("pinboard.selectionErrorTitle"), L10n.t("pinboard.contradictSelectTwo"));
      return;
    }

    String command = contradictCommandFor(selectedItems);
    if (command == null) {
      showAlert(L10n.t("pinboard.invalidSelectionTitle"), L10n.t("pinboard.contradictNeedTypes"));
      return;
    }

    if (commandHandler != null) {
      commandHandler.accept(command);
    }
  }

  /**
   * Builds the canonical {@code contradict <evidence> with <suspect>} command
   * (.scratch/gui-contradict-syntax) for a two-card selection, or {@code null} if it is not a valid
   * suspect-side + evidence pair. The "suspect side" may be a SUSPECT card OR a suspect's STATEMENT
   * card — the statement (and the link the player just drew to the evidence) is the natural action,
   * so the board resolves a statement card to its owning suspect and runs the same contradiction
   * (.scratch/gui-pinboard-contradict-statement). Pure — no UI/FX, so it is unit-testable.
   */
  static String contradictCommandFor(java.util.Collection<PinboardItemModel> selected) {
    if (selected == null || selected.size() != 2) {
      return null;
    }
    PinboardItemModel suspectSide = null;
    PinboardItemModel evidence = null;
    for (PinboardItemModel item : selected) {
      if (isSuspectSide(item)) {
        suspectSide = item;
      } else {
        evidence = item;
      }
    }
    if (suspectSide == null || evidence == null) {
      return null;
    }
    String suspectArg = suspectArgFor(suspectSide);
    String evidenceId = evidence.getRelatedJournalEntryId();
    if (isBlank(suspectArg) || isBlank(evidenceId)) {
      return null;
    }
    return "contradict " + evidenceId + " with " + suspectArg;
  }

  /** A card that stands in for a suspect: a SUSPECT card, or a suspect's STATEMENT card. */
  private static boolean isSuspectSide(PinboardItemModel item) {
    return item.getType() == PinboardItemModel.ItemType.SUSPECT || isStatementCard(item);
  }

  /**
   * Statement journal ids are {@code stmt:<suspectId>:<state>} (QuestionCommand/ContradictCommand).
   */
  private static boolean isStatementCard(PinboardItemModel item) {
    String id = item.getRelatedJournalEntryId();
    return id != null && id.startsWith("stmt:");
  }

  /**
   * True when a card can take part in a Combine: a journal-backed note (clue / statement /
   * deduction). A SUSPECT card is not a note — its reference is not a journal entry — and a
   * free-typed note has no journal entry at all, so neither can be combined.
   */
  private static boolean isCombinable(PinboardItemModel item) {
    return item != null
        && item.getType() != PinboardItemModel.ItemType.SUSPECT
        && item.getRelatedJournalEntryId() != null;
  }

  /**
   * The {@code <suspect>} argument: a SUSPECT card's title (a suspect name), or the suspect id
   * pulled from a STATEMENT card's id ({@code stmt:<suspectId>:<state>}). {@code ContradictCommand}
   * resolves the suspect by name OR id, so either works.
   */
  private static String suspectArgFor(PinboardItemModel item) {
    if (isStatementCard(item)) {
      String[] parts = item.getRelatedJournalEntryId().split(":");
      return parts.length >= 2 ? parts[1] : null;
    }
    return item.getTitle();
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  private void handleCombineButton() {
    if (selectedItems.size() != 2) {
      showAlert(L10n.t("pinboard.selectionErrorTitle"), L10n.t("pinboard.combineSelectTwo"));
      return;
    }

    Iterator<PinboardItemModel> it = selectedItems.iterator();
    PinboardItemModel item1 = it.next();
    PinboardItemModel item2 = it.next();

    // Only real journal notes can be combined. A suspect card is not a note — its reference is not
    // a
    // journal entry, so sending it would make the engine answer with the misleading "you must
    // discover both notes". Reject it here with a message that says what actually went wrong.
    if (!isCombinable(item1) || !isCombinable(item2)) {
      showAlert(L10n.t("pinboard.selectionErrorTitle"), L10n.t("pinboard.combineNeedNotes"));
      return;
    }

    String id1 = item1.getRelatedJournalEntryId();
    String id2 = item2.getRelatedJournalEntryId();

    if (id1 == null || id2 == null) {
      showAlert(L10n.t("pinboard.selectionErrorTitle"), L10n.t("pinboard.combineNeedJournal"));
      return;
    }

    if (commandHandler != null) {
      String command =
          "combine " + id1 + " + " + id2; // Using "+" for readability, but command parsing should
      // handle spaces
      // Actually, my command parser uses split(" ").
      // I should check BaseCommand parser but generally standard is space separated.
      // "combine id1 id2"
      command = "combine " + id1 + " " + id2;
      commandHandler.accept(command);
    }
  }

  private void showAlert(String title, String content) {
    Alert alert = new Alert(Alert.AlertType.WARNING);
    alert.setTitle(title);
    alert.setHeaderText(null);
    alert.setContentText(content);
    // A dialog is its own Scene: install the theme (which also tags the reading text-size bucket)
    // so it matches the board instead of the modena default.
    ui.util.Theme.install(alert.getDialogPane());
    LocaleStyling.apply(alert.getDialogPane());
    alert.showAndWait();
  }

  private void makeDraggable(Node node, PinboardItemModel item) {
    node.setOnMousePressed(
        e -> {
          if (!isLinkMode && !readOnly) {
            dragDeltaX = node.getLayoutX() - e.getSceneX();
            dragDeltaY = node.getLayoutY() - e.getSceneY();
            // A whole drag is ONE undo entry: snapshot now, push on release if it moved.
            pendingGestureSnapshot = getState();
            gestureChanged = false;
            node.toFront();
            e.consume();
          }
        });

    node.setOnMouseDragged(
        e -> {
          if (!isLinkMode && !readOnly) {
            double newX = e.getSceneX() + dragDeltaX;
            double newY = e.getSceneY() + dragDeltaY;
            node.setLayoutX(newX);
            node.setLayoutY(newY);
            item.setX(newX);
            item.setY(newY);
            gestureChanged = true;
            updateLinks(item);
            e.consume();
          }
        });

    node.setOnMouseReleased(
        e -> {
          if (!isLinkMode && !readOnly) {
            if (gestureChanged) {
              pushUndoSnapshot(pendingGestureSnapshot);
            }
            pendingGestureSnapshot = null;
            PinboardUpdateDTO update = new PinboardUpdateDTO();
            update.setType(PinboardUpdateDTO.UpdateType.MOVE_ITEM);
            update.setTargetId(item.getId());
            update.setNewX(item.getX());
            update.setNewY(item.getY());
            sendUpdate(update);
          }
        });
  }

  private void handleLinkClick(PinboardItemModel item) {
    Node node = itemNodeMap.get(item.getId());
    if (linkStartId == null) {
      // First pick: highlight the source and start a live thread that follows the cursor.
      linkStartId = item.getId();
      if (node != null && !node.getStyleClass().contains("pinboard-item--link-source")) {
        node.getStyleClass().add("pinboard-item--link-source");
      }
      if (node instanceof Region region) {
        startPendingThread(region);
      }
    } else {
      // Second pick: commit the link (unless it is the same item), then clear the pending state.
      if (!linkStartId.equals(item.getId())) {
        createLink(linkStartId, item.getId());
      }
      cancelPendingLink();
    }
  }

  /** Abandons a half-made link: clears the source highlight and the cursor-following thread. */
  private void cancelPendingLink() {
    if (linkStartId != null) {
      Node node = itemNodeMap.get(linkStartId);
      if (node != null) {
        node.getStyleClass().remove("pinboard-item--link-source");
        if (selectedItem != null && selectedItem.getId().equals(linkStartId)) {
          selectItem(selectedItem, node);
        }
      }
      linkStartId = null;
    }
    clearPendingThread();
  }

  /**
   * Period-correct yarn colours from the theme-aware DESIGN.md §2 palette (flip with the theme; the
   * board rebuilds on a theme switch). DESIGN.md §8. Shared by committed links and the in-progress
   * preview thread so the preview matches the final colour from the very first note.
   */
  private static Color strokeColorFor(String color) {
    switch (color != null ? color.toUpperCase() : "RED") {
      case "GREEN":
        return ui.util.Palette.MOSS;
      case "YELLOW":
        return ui.util.Palette.OCHRE; // brass
      case "RED":
      default:
        return ui.util.Palette.OXBLOOD;
    }
  }

  /**
   * Starts the live thread from {@code source}'s centre; its end follows the cursor. Styled with
   * the currently-selected link colour so the preview matches the final link from the first pick.
   */
  private void startPendingThread(Region source) {
    clearPendingThread();
    pendingThread = new Line();
    // Style the preview thread inline with the currently-selected colour (was a fixed-oxblood CSS
    // class): an author stylesheet's -fx-stroke would override setStroke(), so we match the
    // committed-link path and set stroke + width directly. Keeps the preview colour identical to
    // the
    // final link from the first pick.
    String selectedColor =
        linkColorSelector.getValue() != null ? linkColorSelector.getValue() : "RED";
    pendingThread.setStroke(strokeColorFor(selectedColor));
    pendingThread.setStrokeWidth(2.5);
    pendingThread.setMouseTransparent(true); // never block item / canvas clicks
    pendingThread
        .startXProperty()
        .bind(source.layoutXProperty().add(source.widthProperty().divide(2)));
    pendingThread
        .startYProperty()
        .bind(source.layoutYProperty().add(source.heightProperty().divide(2)));
    pendingThread.setEndX(source.getLayoutX() + source.getWidth() / 2);
    pendingThread.setEndY(source.getLayoutY() + source.getHeight() / 2);
    canvas.getChildren().add(pendingThread);
  }

  private void clearPendingThread() {
    if (pendingThread != null) {
      canvas.getChildren().remove(pendingThread);
      pendingThread = null;
    }
  }

  private void createLink(String startId, String endId) {
    for (PinboardLinkModel link : links) {
      if ((link.getStartItemId().equals(startId) && link.getEndItemId().equals(endId))
          || (link.getStartItemId().equals(endId) && link.getEndItemId().equals(startId))) {
        return; // Link exists
      }
    }
    pushUndoSnapshot();
    String color =
        linkColorSelector.getValue() != null ? linkColorSelector.getValue().toUpperCase() : "RED";
    PinboardLinkModel link = new PinboardLinkModel(startId, endId, color);
    links.add(link);
    drawLink(link);

    PinboardUpdateDTO update = new PinboardUpdateDTO();
    update.setType(PinboardUpdateDTO.UpdateType.ADD_LINK);
    update.setLink(toDTO(link));
    sendUpdate(update);

    if (onLinkCreated != null) {
      onLinkCreated.run();
    }
  }

  private void drawLink(PinboardLinkModel link) {
    Node startNode = itemNodeMap.get(link.getStartItemId());
    Node endNode = itemNodeMap.get(link.getEndItemId());

    if (startNode instanceof Region && endNode instanceof Region) {
      Region startRegion = (Region) startNode;
      Region endRegion = (Region) endNode;

      Line line = new Line();
      line.setStroke(strokeColorFor(link.getColor()));
      line.setStrokeWidth(
          3); // a touch thicker — a believable thread, and easier to click to delete
      line.startXProperty()
          .bind(startRegion.layoutXProperty().add(startRegion.widthProperty().divide(2)));
      line.startYProperty()
          .bind(startRegion.layoutYProperty().add(startRegion.heightProperty().divide(2)));
      line.endXProperty()
          .bind(endRegion.layoutXProperty().add(endRegion.widthProperty().divide(2)));
      line.endYProperty()
          .bind(endRegion.layoutYProperty().add(endRegion.heightProperty().divide(2)));

      // Delete-link mode (issue 03 / DEC-4): hovering a thread highlights it (thicker + hand
      // cursor); a click removes it. Right-click stays a delete shortcut in any mode.
      line.setOnMouseEntered(
          e -> {
            if (isDeleteLinkMode) {
              line.setStrokeWidth(6);
              line.setCursor(javafx.scene.Cursor.HAND);
            }
          });
      line.setOnMouseExited(e -> line.setStrokeWidth(3));
      line.setOnMouseClicked(
          e -> {
            if (readOnly) {
              return; // read-only review: threads cannot be deleted
            }
            if (e.getButton() == MouseButton.SECONDARY
                || (isDeleteLinkMode && e.getButton() == MouseButton.PRIMARY)) {
              // Snapshot at the user action, not inside removeLink — removeLink is also the
              // remove-item cascade and the incoming-update plumbing.
              pushUndoSnapshot();
              removeLink(link);
            }
          });

      linkNodeMap.put(link, line);
      // Threads sit behind the cards but ABOVE the cork plate (which holds index 0). GUI G5a.
      canvas.getChildren().add(corkLayerTop(), line);
    }
  }

  private void updateLinks(PinboardItemModel item) {}

  private void removeItem(PinboardItemModel item) {
    items.remove(item);
    Node node = itemNodeMap.remove(item.getId());
    canvas.getChildren().remove(node);
    List<PinboardLinkModel> toRemove =
        links.stream()
            .filter(
                l ->
                    l.getStartItemId().equals(item.getId())
                        || l.getEndItemId().equals(item.getId()))
            .collect(Collectors.toList());
    toRemove.forEach(this::removeLink);

    PinboardUpdateDTO update = new PinboardUpdateDTO();
    update.setType(PinboardUpdateDTO.UpdateType.REMOVE_ITEM);
    update.setTargetId(item.getId());
    sendUpdate(update);
  }

  private void removeLink(PinboardLinkModel link) {
    links.remove(link);
    Line line = linkNodeMap.remove(link);
    canvas.getChildren().remove(line);

    PinboardUpdateDTO update = new PinboardUpdateDTO();
    update.setType(PinboardUpdateDTO.UpdateType.REMOVE_LINK);
    update.setLink(toDTO(link));
    sendUpdate(update);
  }

  private void syncJournal() {
    if (onSyncRequest != null) {
      onSyncRequest.run();
    }
  }

  public void setOnSyncRequest(Runnable onSyncRequest) {
    this.onSyncRequest = onSyncRequest;
  }

  public void setOnUpdateCallback(java.util.function.Consumer<PinboardUpdateDTO> callback) {
    this.onUpdateCallback = callback;
  }

  public void setCommandHandler(java.util.function.Consumer<String> handler) {
    this.commandHandler = handler;
  }

  /** Notified when the player draws a link, so the pinboard tutorial can advance on that action. */
  public void setOnLinkCreated(Runnable onLinkCreated) {
    this.onLinkCreated = onLinkCreated;
  }

  private void sendUpdate(PinboardUpdateDTO update) {
    if (onUpdateCallback != null) {
      onUpdateCallback.accept(update);
    }
  }

  // --- Undo / redo (.scratch/gui-pinboard-undo) ---

  private static boolean isUndoCombo(KeyEvent e) {
    return e.isShortcutDown() && !e.isShiftDown() && e.getCode() == KeyCode.Z;
  }

  private static boolean isRedoCombo(KeyEvent e) {
    return e.isShortcutDown()
        && ((e.isShiftDown() && e.getCode() == KeyCode.Z) || e.getCode() == KeyCode.Y);
  }

  /** Records the CURRENT state as the point an undo returns to. Call BEFORE mutating the board. */
  private void pushUndoSnapshot() {
    pushUndoSnapshot(getState());
  }

  /**
   * Records a snapshot captured earlier (gesture start / edit focus-gain) as an undo point. A new
   * edit forks history, so the redo stack is discarded.
   */
  private void pushUndoSnapshot(PinboardStateDTO snapshot) {
    if (readOnly || snapshot == null) {
      return;
    }
    push(undoStack, snapshot);
    redoStack.clear();
  }

  private static void push(ArrayDeque<PinboardStateDTO> stack, PinboardStateDTO snapshot) {
    stack.push(snapshot);
    while (stack.size() > MAX_HISTORY) {
      stack.removeLast();
    }
  }

  private void undo() {
    if (undoStack.isEmpty()) {
      return;
    }
    PinboardStateDTO target = undoStack.pop();
    push(redoStack, getState());
    restoreState(target);
  }

  private void redo() {
    if (redoStack.isEmpty()) {
      return;
    }
    PinboardStateDTO target = redoStack.pop();
    push(undoStack, getState());
    restoreState(target);
  }

  /**
   * Restores a snapshot by replaying the diff from the current state as ordinary granular updates:
   * each one mutates the local board exactly like an incoming peer update, then goes out through
   * {@link #sendUpdate} — the SAME path a live edit takes — so in multiplayer the peer and the
   * server's board state follow the undo instead of silently diverging.
   */
  private void restoreState(PinboardStateDTO target) {
    cancelPendingLink();
    clearSelection();
    for (PinboardUpdateDTO update : PinboardUndoDiff.diff(getState(), target)) {
      applyUpdateNow(update);
      sendUpdate(update);
    }
  }

  public PinboardStateDTO getState() {
    PinboardStateDTO state = new PinboardStateDTO();
    List<PinboardItemDTO> itemDTOs = items.stream().map(this::toDTO).collect(Collectors.toList());
    List<PinboardLinkDTO> linkDTOs = links.stream().map(this::toDTO).collect(Collectors.toList());
    state.setItems(itemDTOs);
    state.setLinks(linkDTOs);
    // Template data omitted as requested (replaced by Evidence Panel)
    return state;
  }

  public void applyState(PinboardStateDTO state) {
    if (state == null) return;
    // A full state replaces the world (server sync / session restore): stale mementos would let
    // Ctrl+Z "undo" straight across it, so history starts fresh.
    undoStack.clear();
    redoStack.clear();
    pendingGestureSnapshot = null;
    java.util.function.Consumer<PinboardUpdateDTO> savedCallback = this.onUpdateCallback;
    this.onUpdateCallback = null;
    try {
      resetContent(); // CHANGED: Replaced clearBoard() with resetContent() for full state apply
      // Note: This does NOT restore the side panel items, as they come from the
      // Journal source.
      // MainController must call setJournalEntries separately.
      if (state.getItems() != null) {
        for (PinboardItemDTO dto : state.getItems()) {
          addItemToBoard(fromDTO(dto));
        }
      }
      if (state.getLinks() != null) {
        for (PinboardLinkDTO dto : state.getLinks()) {
          PinboardLinkModel link = fromDTO(dto);
          links.add(link);
          drawLink(link);
        }
      }
    } finally {
      this.onUpdateCallback = savedCallback;
    }
  }

  public void applyUpdate(PinboardUpdateDTO update) {
    javafx.application.Platform.runLater(() -> applyUpdateNow(update));
  }

  /**
   * Applies one granular update to the local board, callback-suppressed (it describes a change that
   * already happened elsewhere — a peer's edit, or an undo/redo restore that broadcasts each update
   * itself). Must run on the FX thread.
   */
  private void applyUpdateNow(PinboardUpdateDTO update) {
    java.util.function.Consumer<PinboardUpdateDTO> savedCallback = this.onUpdateCallback;
    this.onUpdateCallback = null;
    try {
      switch (update.getType()) {
        case ADD_ITEM:
          addItemToBoard(fromDTO(update.getItem()));
          break;
        case MOVE_ITEM:
          updateItemPosition(update.getTargetId(), update.getNewX(), update.getNewY());
          break;
        case RESIZE_ITEM:
          PinboardItemModel itemToResize = findItemById(update.getTargetId());
          if (itemToResize != null && update.getItem() != null) {
            itemToResize.setWidth(update.getItem().getWidth());
            itemToResize.setHeight(update.getItem().getHeight());
            Node node = itemNodeMap.get(itemToResize.getId());
            if (node instanceof Region) {
              ((Region) node).setPrefSize(itemToResize.getWidth(), itemToResize.getHeight());
            }
            updateLinks(itemToResize);
          }
          break;
        case UPDATE_CONTENT:
          PinboardItemModel itemToUpdate = findItemById(update.getTargetId());
          if (itemToUpdate != null) {
            itemToUpdate.setContent(update.getValue());
            Node node = itemNodeMap.get(itemToUpdate.getId());
            if (node instanceof VBox) {
              for (Node child : ((VBox) node).getChildren()) {
                if (child instanceof TextArea) {
                  ((TextArea) child).setText(update.getValue());
                  break;
                }
              }
            }
          }
          break;
        case REMOVE_ITEM:
          PinboardItemModel itemToRemove = findItemById(update.getTargetId());
          if (itemToRemove != null) removeItem(itemToRemove);
          break;
        case ADD_LINK:
          PinboardLinkModel link = fromDTO(update.getLink());
          links.add(link);
          drawLink(link);
          break;
        case REMOVE_LINK:
          if (update.getLink() != null) {
            PinboardLinkModel target = null;
            for (PinboardLinkModel l : links) {
              if (l.getStartItemId().equals(update.getLink().getStartItemId())
                  && l.getEndItemId().equals(update.getLink().getEndItemId())) {
                target = l;
                break;
              }
            }
            if (target != null) removeLink(target);
          }
          break;
        case CLEAR_BOARD:
          resetContent(); // CHANGED: Ensure updates clear everything if meant to be a full
          // reset
          break;
      }
    } finally {
      this.onUpdateCallback = savedCallback;
    }
  }

  private void updateItemPosition(String id, double x, double y) {
    PinboardItemModel item = findItemById(id);
    if (item != null) {
      item.setX(x);
      item.setY(y);
      Node node = itemNodeMap.get(id);
      if (node != null) {
        node.setLayoutX(x);
        node.setLayoutY(y);
        updateLinks(item);
      }
    }
  }

  private PinboardItemModel findItemById(String id) {
    return items.stream().filter(i -> i.getId().equals(id)).findFirst().orElse(null);
  }

  private PinboardItemDTO toDTO(PinboardItemModel model) {
    PinboardItemDTO dto = new PinboardItemDTO();
    dto.setId(model.getId());
    dto.setType(model.getType().name());
    dto.setTitle(model.getTitle());
    dto.setContent(model.getContent());
    dto.setRelatedJournalEntryId(model.getRelatedJournalEntryId());
    dto.setX(model.getX());
    dto.setY(model.getY());
    dto.setWidth(model.getWidth());
    dto.setHeight(model.getHeight());
    dto.setColor(model.getColor());
    return dto;
  }

  private PinboardItemModel fromDTO(PinboardItemDTO dto) {
    PinboardItemModel model = new PinboardItemModel();
    if (dto.getId() != null) model.setId(dto.getId());
    model.setType(PinboardItemModel.ItemType.valueOf(dto.getType()));
    model.setTitle(dto.getTitle());
    model.setContent(dto.getContent());
    model.setRelatedJournalEntryId(dto.getRelatedJournalEntryId());
    model.setX(dto.getX());
    model.setY(dto.getY());
    model.setWidth(dto.getWidth());
    model.setHeight(dto.getHeight());
    model.setColor(dto.getColor());
    return model;
  }

  private PinboardLinkDTO toDTO(PinboardLinkModel model) {
    return new PinboardLinkDTO(model.getStartItemId(), model.getEndItemId(), model.getColor());
  }

  private PinboardLinkModel fromDTO(PinboardLinkDTO dto) {
    return new PinboardLinkModel(dto.getStartItemId(), dto.getEndItemId(), dto.getColor());
  }
}

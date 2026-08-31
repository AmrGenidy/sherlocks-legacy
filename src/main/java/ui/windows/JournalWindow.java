package ui.windows;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import ui.MainController;
import ui.i18n.L10n;
import ui.i18n.LocaleStyling;

/**
 * Journal window for the Detective Game. Allows players to search journal entries and add new
 * notes.
 */
public class JournalWindow {

  private Stage stage;
  private MainController mainController;
  private ListView<String> entriesListView;
  private TextField searchField;
  private TextArea noteTextArea;
  private List<String> allEntries;

  public JournalWindow(MainController controller) {
    this.mainController = controller;
    this.allEntries = new ArrayList<>();
    initializeWindow();
  }

  private void initializeWindow() {
    stage = new Stage();
    ui.util.AppIcon.applyTo(stage);
    stage.setTitle(L10n.t("toolbar.journal"));
    // DESIGN.md §4 (.scratch/responsive-resizing issue 03): sensible min size, 8px scale.
    stage.setMinWidth(400);
    stage.setMinHeight(480);

    BorderPane root = new BorderPane();
    root.setPadding(new Insets(10));
    root.getStyleClass().add("panel");
    LocaleStyling.apply(root);
    // Reading text size follows the "Reading text size" slider
    // (.scratch/gui-typography-readability):
    // the .typewriter entries list resizes with the read-scale bucket; rebuilt on a scale change.
    ui.util.ContentScaleStyling.apply(
        root, ui.util.ContentScale.READING_PREFIX, mainController.getReadingTextScale());

    VBox topBox = new VBox(5);
    Label searchLabel = new Label(L10n.t("journal.searchLabel"));
    searchLabel.getStyleClass().add("panel-title");

    searchField = new TextField();
    searchField.setPromptText(L10n.t("journal.searchPrompt"));
    searchField.getStyleClass().add("themed-input");
    searchField.setOnAction(e -> performSearch());

    topBox.getChildren().addAll(searchLabel, searchField);
    root.setTop(topBox);

    VBox centerBox = new VBox(5);
    centerBox.setPadding(new Insets(10, 0, 10, 0));

    Label entriesLabel = new Label(L10n.t("journal.entriesLabel"));
    entriesLabel.getStyleClass().add("panel-title");

    entriesListView = new ListView<>();
    entriesListView.getStyleClass().add("typewriter");
    entriesListView.setPrefHeight(300);

    centerBox.getChildren().addAll(entriesLabel, entriesListView);
    VBox.setVgrow(entriesListView, javafx.scene.layout.Priority.ALWAYS);
    root.setCenter(centerBox);

    VBox bottomBox = new VBox(5);
    Label noteLabel = new Label(L10n.t("journal.addNoteLabel"));
    noteLabel.getStyleClass().add("panel-title");

    noteTextArea = new TextArea();
    noteTextArea.setPromptText(L10n.t("journal.notePrompt"));
    noteTextArea.setPrefHeight(80);
    noteTextArea.setWrapText(true);
    noteTextArea.getStyleClass().add("themed-input");
    noteTextArea.addEventFilter(
        KeyEvent.KEY_PRESSED,
        e -> {
          if (e.getCode() == KeyCode.ENTER && !e.isShiftDown()) {
            e.consume(); // Consume the event to prevent a newline
            addNote();
          }
        });

    Button addNoteButton = new Button(L10n.t("journal.addNote"));
    addNoteButton.setOnAction(e -> addNote());

    HBox buttonBox = new HBox();
    buttonBox.setAlignment(Pos.CENTER_RIGHT);
    buttonBox.getChildren().add(addNoteButton);

    bottomBox.getChildren().addAll(noteLabel, noteTextArea, buttonBox);
    root.setBottom(bottomBox);

    Scene scene = new Scene(root, 600, 500);

    ui.util.Theme.install(scene);

    stage.setScene(scene);
  }

  public boolean isShowing() {
    return stage != null && stage.isShowing();
  }

  public void close() {
    if (stage != null) {
      stage.close();
    }
  }

  private void performSearch() {
    String keyword = searchField.getText().trim().toLowerCase();
    if (keyword.isEmpty()) {
      updateEntriesList(allEntries);
      return;
    }

    List<String> filteredEntries =
        allEntries.stream()
            .filter(entry -> entry.toLowerCase().contains(keyword))
            .collect(Collectors.toList());

    updateEntriesList(filteredEntries);
  }

  private void addNote() {
    String note = noteTextArea.getText().trim();
    if (note.isEmpty()) {
      return;
    }

    if (mainController != null) {
      // Route through the SAME command the terminal uses, so the GUI "Add note" is equivalent to
      // typing `journal add …` — it reaches the engine AND the tutorial step-matcher
      // (.scratch/gui-journal-tutorial-addnote).
      mainController.sendCommand("journal add " + note);
    }

    noteTextArea.clear();
  }

  private void updateEntriesList(List<String> entries) {
    entriesListView.getItems().clear();
    entriesListView.getItems().addAll(entries);
  }

  public void addEntry(String entry) {
    allEntries.add(entry);
    updateEntriesList(allEntries);
  }

  public void setEntries(List<common.dto.JournalEntryDTO> entries) {
    allEntries.clear();
    for (common.dto.JournalEntryDTO entry : entries) {
      allEntries.add(entry.toString());
    }
    updateEntriesList(allEntries);
  }

  public void show() {
    if (stage != null) {
      stage.show();
      stage.toFront();
    }
  }

  public void hide() {
    if (stage != null) {
      stage.hide();
    }
  }
}

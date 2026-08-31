package ui.windows;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import singleplayer.util.CaseFileUtil;
import ui.MainController;
import ui.i18n.L10n;
import ui.i18n.LocaleStyling;

/**
 * "File a new case" (MENU_DESIGN #8): the add-custom-case modal dressed as filing a case into the
 * archive. A dossier panel with the current archive (the case list), a path well + a <b>Browse…</b>
 * {@link FileChooser}, engraved {@code .menu-plate} actions, and an in-window status line (no
 * {@code Alert}). Inherits the active light/dark theme so it matches the app.
 */
public class AddCaseWindow extends Stage {

  private final MainController mainController;
  private ListView<String> caseListView;
  private Label status;

  public AddCaseWindow(MainController mainController) {
    this.mainController = mainController;

    initModality(Modality.APPLICATION_MODAL);
    setTitle(L10n.t("addCase.title"));
    ui.util.AppIcon.applyTo(this);
    // DESIGN.md §4 (.scratch/responsive-resizing issue 03): sensible min size, 8px scale.
    setMinWidth(420);
    setMinHeight(440);

    VBox layout = new VBox(16);
    layout.setPadding(new Insets(24));
    layout.getStyleClass().add("dossier-panel");
    LocaleStyling.apply(layout);

    Label titleLabel = new Label(L10n.t("addCase.manageHeading"));
    titleLabel.getStyleClass().add("dossier-title");
    Label intro = new Label(L10n.t("addCase.intro"));
    intro.getStyleClass().add("dossier-intro");
    intro.setWrapText(true);

    caseListView = new ListView<>();
    caseListView.getStyleClass().add("dossier-list");
    VBox.setVgrow(caseListView, Priority.ALWAYS);
    refreshCaseList();

    Label pathLabel = new Label(L10n.t("addCase.pathLabel"));
    pathLabel.getStyleClass().add("settings-row-label");

    TextField pathField = new TextField();
    pathField.getStyleClass().add("mp-code-well");
    pathField.setPromptText(L10n.t("addCase.pathPrompt"));
    HBox.setHgrow(pathField, Priority.ALWAYS);

    Button browse = plate(L10n.t("addCase.browse"), false);
    browse.setOnAction(event -> browseForCase(pathField));
    HBox pathRow = new HBox(10, pathField, browse);
    pathRow.setAlignment(Pos.CENTER_LEFT);

    status = new Label();
    status.getStyleClass().add("dossier-status");
    status.setWrapText(true);
    status.setManaged(false);
    status.setVisible(false);

    Button addButton = plate(L10n.t("addCase.add"), true);
    addButton.setOnAction(event -> fileCase(pathField));
    Button closeButton = plate(L10n.t("common.close"), false);
    closeButton.setOnAction(event -> close());
    HBox actions = new HBox(10, addButton, closeButton);
    actions.setAlignment(Pos.CENTER_RIGHT);

    layout
        .getChildren()
        .addAll(titleLabel, intro, caseListView, pathLabel, pathRow, status, actions);

    Scene scene = new Scene(layout, 540, 620);
    // Base + active theme (MENU_DESIGN #6); dark mode is an override layer (DESIGN.md §8).
    ui.util.Theme.install(scene);
    setScene(scene);
  }

  private Button plate(String text, boolean primary) {
    Button button = new Button(text);
    button.getStyleClass().add("menu-plate");
    if (primary) {
      button.getStyleClass().add("menu-plate--primary");
    }
    return button;
  }

  private void browseForCase(TextField pathField) {
    FileChooser chooser = new FileChooser();
    chooser.setTitle(L10n.t("addCase.browse"));
    chooser
        .getExtensionFilters()
        .add(new FileChooser.ExtensionFilter(L10n.t("addCase.jsonFilter"), "*.json"));
    File chosen = chooser.showOpenDialog(this);
    if (chosen != null) {
      pathField.setText(chosen.getAbsolutePath());
    }
  }

  private void fileCase(TextField pathField) {
    String path = pathField.getText();
    if (path == null || path.trim().isEmpty()) {
      setStatus(L10n.t("addCase.emptyPath"), false);
      return;
    }
    String result = CaseFileUtil.addCaseFile(path);
    boolean ok = result.startsWith("Success");
    setStatus(result, ok);
    if (ok) {
      refreshCaseList();
      pathField.clear();
      // Reload the case-selection gallery behind this modal so the imported case appears at once.
      mainController.refreshCaseSelectionAfterImport();
    }
  }

  private void setStatus(String message, boolean ok) {
    status.setText(message);
    status.getStyleClass().removeAll("dossier-status--ok", "dossier-status--error");
    status.getStyleClass().add(ok ? "dossier-status--ok" : "dossier-status--error");
    status.setManaged(true);
    status.setVisible(true);
  }

  private void refreshCaseList() {
    List<String> caseFiles =
        CaseFileUtil.getAvailableCaseFiles().stream()
            .map(File::getName)
            .collect(Collectors.toList());
    caseListView.getItems().setAll(caseFiles);
  }
}

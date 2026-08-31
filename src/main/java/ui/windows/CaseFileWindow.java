package ui.windows;

import JsonDTO.LocalizedCaseFile;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import ui.MainController;
import ui.i18n.L10n;
import ui.i18n.LocaleStyling;

public class CaseFileWindow {
  private final Stage stage;
  private final MainController mainController;

  public CaseFileWindow(MainController mainController) {
    this.mainController = mainController;
    this.stage = new Stage();
    ui.util.AppIcon.applyTo(this.stage);
    this.stage.initModality(Modality.APPLICATION_MODAL);
    this.stage.setTitle(L10n.t("toolbar.caseFile"));
    // DESIGN.md §4 (.scratch/responsive-resizing issue 03): min size on the 8px scale; the
    // tabbed profile layout needs the larger floor to keep portrait + details grid readable.
    this.stage.setMinWidth(648);
    this.stage.setMinHeight(552);

    BorderPane root = new BorderPane();
    root.getStyleClass().add("panel");
    LocaleStyling.apply(root);
    // Reading prose (.case-file-body TextAreas) follows the "Reading text size" slider
    // (.scratch/gui-typography-readability); rebuilt on a scale change.
    ui.util.ContentScaleStyling.apply(
        root, ui.util.ContentScale.READING_PREFIX, mainController.getReadingTextScale());

    LocalizedCaseFile.LocalizedCaseFileBlock block = mainController.getActiveCaseFileBlock();

    if (block == null) {
      Label noDataLabel = new Label(L10n.t("caseFile.noData"));
      noDataLabel.getStyleClass().add("panel-title");
      noDataLabel.setAlignment(Pos.CENTER);
      root.setCenter(noDataLabel);
    } else {
      TabPane tabPane = new TabPane();
      tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
      // TabPane styling can go to detective-theme.css if we need specifics

      // Overview Tab
      Tab overviewTab = createOverviewTab(block);
      tabPane.getTabs().add(overviewTab);

      // Suspect Tabs
      if (block.getSuspectProfiles() != null) {
        for (Map.Entry<String, LocalizedCaseFile.LocalizedSuspectProfileData> entry :
            block.getSuspectProfiles().entrySet()) {
          Tab suspectTab = createSuspectTab(entry.getKey(), entry.getValue());
          tabPane.getTabs().add(suspectTab);
        }
      }
      root.setCenter(tabPane);
    }

    // Apply theme and global shortcuts
    Scene scene = new Scene(root, 700, 600);
    ui.util.Theme.install(scene);

    scene.setOnKeyPressed(
        event -> {
          if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
            close();
          }
        });

    stage.setScene(scene);
  }

  private Tab createOverviewTab(LocalizedCaseFile.LocalizedCaseFileBlock block) {
    Tab tab = new Tab(L10n.t("caseFile.overviewTab"));
    VBox content = new VBox(15);
    content.setPadding(new Insets(20));
    content.getStyleClass().add("panel");

    Label titleLabel = new Label(L10n.t("caseFile.overviewTitle"));
    titleLabel.getStyleClass().add("window-title");

    // The case's lead detective (the player) and assistant. Author-defined per case; falls back to
    // the Sherlock Holmes framing when a case does not rename them. A single name for all languages.
    String detective = mainController.getActiveDetectiveName();
    if (detective == null || detective.isBlank()) {
      detective = "Sherlock Holmes";
    }
    String assistant = mainController.getActiveHelperName();
    if (assistant == null || assistant.isBlank()) {
      assistant = L10n.t("game.watsonSpeaker");
    }
    GridPane castGrid = new GridPane();
    castGrid.setHgap(12);
    castGrid.setVgap(4);
    addGridRow(castGrid, 0, L10n.t("caseFile.detective"), detective);
    addGridRow(castGrid, 1, L10n.t("caseFile.assistant"), assistant);

    TextArea overviewArea =
        new TextArea(
            block.getOverview() != null ? block.getOverview() : L10n.t("caseFile.noOverview"));
    overviewArea.setWrapText(true);
    overviewArea.setEditable(false);
    overviewArea.getStyleClass().addAll("themed-input", "case-file-body");
    overviewArea.setPrefRowCount(5);

    content.getChildren().addAll(titleLabel, castGrid, overviewArea);

    if (block.getVictim() != null) {
      Separator sep = new Separator();

      Label victimTitle = new Label(L10n.t("caseFile.victimTitle"));
      victimTitle.getStyleClass().add("panel-title");

      GridPane grid = new GridPane();
      grid.setHgap(10);
      grid.setVgap(10);

      addGridRow(grid, 0, L10n.t("caseFile.name"), block.getVictim().getName());
      addGridRow(grid, 1, L10n.t("caseFile.relation"), block.getVictim().getRelationToCase());

      TextArea notesArea =
          new TextArea(
              block.getVictim().getNotes() != null
                  ? block.getVictim().getNotes()
                  : L10n.t("caseFile.noNotes"));
      notesArea.setWrapText(true);
      notesArea.setEditable(false);
      notesArea.getStyleClass().addAll("themed-input", "case-file-body");
      notesArea.setPrefRowCount(4);

      content
          .getChildren()
          .addAll(sep, victimTitle, grid, new Label(L10n.t("caseFile.notes")), notesArea);
    }

    tab.setContent(content);
    return tab;
  }

  private Tab createSuspectTab(
      String suspectId, LocalizedCaseFile.LocalizedSuspectProfileData profile) {
    // Tabs show the suspect's DISPLAY name, never the raw id (.scratch/casefile-tabs issue 02).
    String displayName = mainController.getSuspectDisplayName(suspectId);
    Tab tab = new Tab(displayName);

    SplitPane split = new SplitPane();
    split.setOrientation(javafx.geometry.Orientation.VERTICAL);
    split.setDividerPositions(0.45);

    // Top half
    HBox topHalf = new HBox(20);
    topHalf.setPadding(new Insets(15));
    topHalf.getStyleClass().add("panel");

    ImageView portrait = new ImageView();
    portrait.setFitWidth(150);
    portrait.setFitHeight(150);
    portrait.setPreserveRatio(true);
    portrait.setSmooth(true); // filtered scaling, not nearest-neighbour
    if (profile.getImagePath() != null) {
      // Ask for art decoded near the drawn size (2× the 150px box) rather than reducing a ~1264px
      // source by 8× at draw time, which is what made these portraits look pixelated.
      Image img = mainController.getImageManager().getSuspectImage(profile.getImagePath(), 300);
      if (img != null) {
        portrait.setImage(img);
      }
    }

    GridPane detailsGrid = new GridPane();
    detailsGrid.setHgap(10);
    detailsGrid.setVgap(10);

    String unknown = L10n.t("caseFile.unknown");
    addGridRow(detailsGrid, 0, L10n.t("caseFile.name"), displayName);
    addGridRow(
        detailsGrid,
        1,
        L10n.t("caseFile.profession"),
        profile.getProfession() != null ? profile.getProfession() : unknown);
    addGridRow(
        detailsGrid,
        2,
        L10n.t("caseFile.age"),
        profile.getAge() != null ? String.valueOf(profile.getAge()) : unknown);
    addGridRow(
        detailsGrid,
        3,
        L10n.t("caseFile.height"),
        profile.getHeightCm() != null
            ? L10n.t("caseFile.heightCm", profile.getHeightCm())
            : unknown);
    addGridRow(
        detailsGrid,
        4,
        L10n.t("caseFile.weight"),
        profile.getWeightKg() != null
            ? L10n.t("caseFile.weightKg", profile.getWeightKg())
            : unknown);

    topHalf.getChildren().addAll(portrait, detailsGrid);

    // Bottom half
    VBox bottomHalf = new VBox(10);
    bottomHalf.setPadding(new Insets(15));
    bottomHalf.getStyleClass().add("panel");

    Label relLabel = new Label(L10n.t("caseFile.relationshipToVictim"));
    relLabel.getStyleClass().add("sidebar-label");

    TextArea relArea =
        new TextArea(
            profile.getRelationshipToVictim() != null
                ? profile.getRelationshipToVictim()
                : unknown);
    relArea.setWrapText(true);
    relArea.setEditable(false);
    relArea.getStyleClass().addAll("themed-input", "case-file-body");
    relArea.setPrefRowCount(3);

    Label bioLabel = new Label(L10n.t("caseFile.biography"));
    bioLabel.getStyleClass().add("sidebar-label");

    TextArea bioArea = new TextArea(profile.getBio() != null ? profile.getBio() : unknown);
    bioArea.setWrapText(true);
    bioArea.setEditable(false);
    bioArea.getStyleClass().addAll("themed-input", "case-file-body");
    VBox.setVgrow(bioArea, Priority.ALWAYS);

    bottomHalf.getChildren().addAll(relLabel, relArea, bioLabel, bioArea);

    split.getItems().addAll(topHalf, bottomHalf);
    tab.setContent(split);
    return tab;
  }

  private void addGridRow(GridPane grid, int row, String label, String value) {
    Label lbl = new Label(label);
    lbl.getStyleClass().add("sidebar-label");
    Label val = new Label(value != null && !value.isEmpty() ? value : L10n.t("caseFile.unknown"));
    val.getStyleClass().add("sidebar-value");
    grid.addRow(row, lbl, val);
  }

  public void show() {
    stage.show();
  }

  public boolean isShowing() {
    return stage.isShowing();
  }

  public void close() {
    stage.close();
  }
}

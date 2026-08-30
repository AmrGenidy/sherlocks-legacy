package ui.windows;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import ui.i18n.L10n;
import ui.i18n.LocaleStyling;

/**
 * The in-game command reference (GUI G4): a scannable card, not a wall of text. {@link
 * HelpReference} sections render into a single two-column grid — the command literal in the
 * typewriter face on the left, a wrapping Spectral description on the right — on a vellum {@code
 * .panel} that follows the theme. Vertical scroll only; descriptions wrap, so there is never a
 * horizontal scrollbar.
 *
 * <p>One grid for the whole card (section headers span both columns) so the command column sizes to
 * the single widest command across <i>all</i> groups and every row aligns. The command never wraps
 * mid-token: its column is content-sized ({@code hgrow=NEVER}, command label {@code
 * wrapText=false}); only the description column grows and wraps.
 */
public class HelpWindow extends Stage {

  public HelpWindow() {
    setTitle(L10n.t("toolbar.help"));
    ui.util.AppIcon.applyTo(this);
    // A comfortable default that shows most of the card; resizable, with a floor that fits the
    // widest command ("move [north|south|east|west|up|down]") beside a readable description.
    setMinWidth(480);
    setMinHeight(440);

    BorderPane root = new BorderPane();
    root.getStyleClass().add("panel");
    LocaleStyling.apply(root);

    // Shared sub-window title treatment (petrol + ochre rule), like the other sub-windows.
    Label title = new Label(L10n.t("help.title"));
    title.getStyleClass().add("window-title");
    title.setMaxWidth(Double.MAX_VALUE);
    BorderPane.setMargin(title, new Insets(0, 0, 12, 0));
    root.setTop(title);

    GridPane grid = buildGrid();

    // Vertical scroll only: fit to width so the grid reflows; the description wraps → no
    // h-scrollbar.
    ScrollPane scroll = new ScrollPane(grid);
    scroll.getStyleClass().add("help-scroll");
    scroll.setFitToWidth(true);
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    root.setCenter(scroll);

    Scene scene = new Scene(root, 700, 560);
    ui.util.Theme.install(scene);
    scene.setOnKeyPressed(
        event -> {
          if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
            close();
          }
        });
    setScene(scene);
  }

  /** One grid for the whole card: headers span both columns; command column is content-sized. */
  private static GridPane buildGrid() {
    GridPane grid = new GridPane();
    grid.getStyleClass().add("help-table");
    grid.setHgap(16);
    grid.setVgap(8);

    // Column 0 sizes to the widest command (no wrap, no grow); column 1 takes the rest and wraps.
    ColumnConstraints cmdCol = new ColumnConstraints();
    cmdCol.setHgrow(Priority.NEVER);
    cmdCol.setHalignment(HPos.LEFT);
    ColumnConstraints descCol = new ColumnConstraints();
    descCol.setHgrow(Priority.ALWAYS);
    descCol.setFillWidth(true);
    descCol.setHalignment(HPos.LEFT);
    grid.getColumnConstraints().addAll(cmdCol, descCol);

    int row = 0;
    boolean first = true;
    for (HelpReference.Section section : HelpReference.sections()) {
      Label header = new Label(L10n.t(section.headerKey()));
      header.getStyleClass().add("help-section-header");
      header.setWrapText(true);
      header.setMaxWidth(Double.MAX_VALUE);
      GridPane.setColumnSpan(header, 2);
      // Even spacing between groups; the first header hugs the top.
      GridPane.setMargin(header, new Insets(first ? 0 : 12, 0, 2, 0));
      grid.add(header, 0, row++);
      first = false;

      for (HelpReference.Entry entry : section.entries()) {
        Label command = new Label(entry.command());
        command.getStyleClass().add("help-command");
        command.setWrapText(false); // never break a command mid-token
        // Reserve the command's full text width so the column can't collapse to an ellipsis ("•••")
        // when the long descriptions push the grid past the viewport — commands stay fully
        // readable.
        command.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        GridPane.setValignment(command, VPos.TOP);

        Label description = new Label(L10n.t(entry.descriptionKey()));
        description.getStyleClass().add("help-description");
        description.setWrapText(true);
        description.setMaxWidth(Double.MAX_VALUE);
        GridPane.setValignment(description, VPos.TOP);

        grid.add(command, 0, row);
        grid.add(description, 1, row);
        row++;
      }
    }
    return grid;
  }
}

package ui.review;

import common.dto.save.CompletedCaseRecord;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import ui.i18n.L10n;
import ui.i18n.LocaleStyling;
import ui.pinboard.PinboardController;

/**
 * A dedicated, read-only viewer for a {@link CompletedCaseRecord} (docs/SAVE_AND_PROFILE.md). It is
 * <em>not</em> the live game screen: it reuses the existing Journal rendering (entries listed as in
 * the Journal window) and the existing Pinboard component, populated purely from the record's saved
 * {@code JournalEntryDTO} list and {@code PinboardStateDTO}. There is no Game Engine, no terminal,
 * and no command/sync path wired up, so Review is read-only <em>by construction</em>.
 *
 * <p>A summary card shows the Rank Tier, Deductions used, Final Exam score, and date solved. A
 * migrated (detail-less) record shows only the summary plus a graceful "no detailed record"
 * message, rather than empty panels.
 */
public class CaseReviewWindow {

  private final String displayTitle;
  private final CaseReviewModel model;
  private final Stage stage = new Stage();
  private PinboardController pinboard; // read-only; created lazily when the board is opened

  public CaseReviewWindow(String displayTitle, CompletedCaseRecord record) {
    this.displayTitle = displayTitle;
    this.model = new CaseReviewModel(record);
    build();
  }

  private void build() {
    stage.setTitle(L10n.t("review.windowTitle", displayTitle == null ? "" : displayTitle));
    ui.util.AppIcon.applyTo(stage);
    stage.setMinWidth(420);
    stage.setMinHeight(480);

    BorderPane root = new BorderPane();
    root.setPadding(new Insets(14));
    root.getStyleClass().add("panel");
    LocaleStyling.apply(root);

    Label title = new Label(displayTitle == null ? "" : displayTitle);
    title.getStyleClass().add("panel-title");
    title.setWrapText(true);
    root.setTop(new VBox(8, title, buildSummaryCard()));

    if (model.hasDetail()) {
      root.setCenter(buildJournalPanel());
      if (model.hasPinboard()) {
        Button openBoard = new Button(L10n.t("review.openPinboard"));
        openBoard.setOnAction(e -> openPinboard());
        VBox bottom = new VBox(openBoard);
        bottom.setAlignment(Pos.CENTER_RIGHT);
        bottom.setPadding(new Insets(10, 0, 0, 0));
        root.setBottom(bottom);
      }
    } else {
      Label message = new Label(L10n.t("review.noDetail"));
      message.getStyleClass().add("panel-title");
      message.setWrapText(true);
      message.setTextAlignment(TextAlignment.CENTER);
      VBox center = new VBox(message);
      center.setAlignment(Pos.CENTER);
      center.setPadding(new Insets(24));
      root.setCenter(center);
    }

    Scene scene = new Scene(root, 560, 560);
    ui.util.Theme.install(scene);
    stage.setScene(scene);
  }

  /** Rank / Deductions / Final Exam score / date — the at-a-glance summary of the solve. */
  private VBox buildSummaryCard() {
    VBox card = new VBox(4);
    card.getStyleClass().add("review-summary-card");
    if (model.hasDetail()) {
      card.getChildren().add(summaryLine("review.summary.rank", text(model.getRankName())));
      card.getChildren()
          .add(summaryLine("review.summary.deductions", text(model.getDeductionsUsed())));
      card.getChildren()
          .add(
              summaryLine(
                  "review.summary.score",
                  text(model.getFinalExamScore()),
                  text(model.getFinalExamTotal())));
      card.getChildren().add(summaryLine("review.summary.date", formatDate()));
    }
    return card;
  }

  private Label summaryLine(String key, Object... args) {
    Label label = new Label(L10n.t(key, args));
    label.getStyleClass().add("review-summary-line");
    label.setWrapText(true);
    return label;
  }

  private VBox buildJournalPanel() {
    Label heading = new Label(L10n.t("review.journalHeading"));
    heading.getStyleClass().add("panel-title");

    ListView<String> entries = new ListView<>();
    entries.getStyleClass().add("typewriter");
    entries.getItems().setAll(model.journalLines());

    VBox panel = new VBox(6, heading, entries);
    panel.setPadding(new Insets(10, 0, 0, 0));
    VBox.setVgrow(entries, Priority.ALWAYS);
    return panel;
  }

  /** Opens the saved Pinboard read-only: no command handler, sync, or update callback is wired. */
  private void openPinboard() {
    if (pinboard == null) {
      pinboard = new PinboardController();
      // Read-only by construction: populate the side panel from the saved Journal, then restore the
      // saved board. No callbacks are set, so nothing the viewer does can be sent or persisted.
      if (model.getPinboard() != null) {
        pinboard.applyState(model.getPinboard());
      }
    }
    pinboard.show();
  }

  public void show() {
    // Closing the review window also closes the read-only Pinboard it may have opened.
    stage.setOnHidden(
        e -> {
          if (pinboard != null) {
            pinboard.close();
          }
        });
    stage.show();
    stage.toFront();
  }

  private static String text(Object value) {
    return value == null ? L10n.t("review.unknown") : String.valueOf(value);
  }

  private String formatDate() {
    Long epoch = model.getDateSolvedEpochMillis();
    if (epoch == null) {
      return L10n.t("review.unknown");
    }
    Locale locale = new Locale(L10n.language());
    return DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(new Date(epoch));
  }
}

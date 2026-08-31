package ui.casemaker;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import ui.casemaker.model.CaseDraft;
import ui.casemaker.model.CombineRuleDraft;
import ui.casemaker.model.ContradictionDraft;
import ui.casemaker.model.ExamChoiceDraft;
import ui.casemaker.model.ExamSlotDraft;
import ui.casemaker.model.FinalExamQuestionDraft;
import ui.casemaker.model.LocalizedText;
import ui.casemaker.model.ObjectDraft;
import ui.casemaker.model.RankTierDraft;
import ui.casemaker.model.RoomDraft;
import ui.casemaker.model.SuspectDraft;
import ui.casemaker.model.WatsonHintDraft;
import ui.i18n.L10n;

/**
 * The Case Maker's "Localization" tab (slice 5). Manages the case's languages and lets the author
 * translate every localizable field into the selected language, with the primary-language text
 * shown as a reference. Also hosts the optional soundtrack picker.
 *
 * <p>Each editable field is backed by a {@link LocalizedText} on the model, so translations live
 * next to the thing they translate and survive renames. {@link #refresh()} rebuilds the field list
 * (call when the tab is shown, since rooms/objects/suspects/logic are authored on other tabs).
 */
public final class LocalizationView extends BorderPane {

  private static final double GAP = 8;
  private static final double PAD = 16;

  private final CaseDraft draft;
  private final ComboBox<String> languagePicker = new ComboBox<>();
  private final TextField newLanguageField = new TextField();
  private final TextField languageNameField = new TextField();
  private final TextField soundtrackField = new TextField();
  private final VBox fieldList = new VBox(GAP);
  private java.util.function.Consumer<String> onLanguageChange;

  /** One translatable field: a human label and the {@link LocalizedText} it edits. */
  private record Field(String label, LocalizedText text) {}

  public LocalizationView(CaseDraft draft) {
    this.draft = draft;
    setTop(buildToolbar());
    ScrollPane scroll = new ScrollPane(fieldList);
    scroll.setFitToWidth(true);
    scroll.getStyleClass().add("casemaker-sidebar-scroll");
    fieldList.setPadding(new Insets(PAD));
    setCenter(scroll);
    setPadding(new Insets(0, PAD, 0, 0));
    refresh();
  }

  private Region buildToolbar() {
    Label langLabel = label("casemaker.l10n.language");
    languagePicker
        .getSelectionModel()
        .selectedItemProperty()
        .addListener((o, a, b) -> onLanguageSelected(b));

    newLanguageField.setPromptText(L10n.t("casemaker.l10n.addLanguage"));
    Button add = new Button(L10n.t("casemaker.l10n.add"));
    add.setOnAction(e -> addLanguage());
    Button remove = new Button(L10n.t("casemaker.l10n.remove"));
    remove.setOnAction(e -> removeLanguage());

    languageNameField.setPromptText(L10n.t("casemaker.l10n.languageName"));
    languageNameField
        .textProperty()
        .addListener(
            (o, a, b) -> {
              String lang = languagePicker.getValue();
              if (lang != null) {
                draft.setLanguageName(lang, b);
              }
            });

    soundtrackField.setPromptText(L10n.t("casemaker.soundtrack"));
    soundtrackField
        .textProperty()
        .addListener((o, a, b) -> draft.setSoundtrack(b == null || b.isBlank() ? null : b));
    soundtrackField.setText(draft.getSoundtrack());
    Button browse = new Button(L10n.t("casemaker.browse"));
    browse.setOnAction(e -> chooseSoundtrack());
    HBox.setHgrow(soundtrackField, Priority.ALWAYS);

    HBox langRow = new HBox(GAP, langLabel, languagePicker, newLanguageField, add, remove);
    langRow.setAlignment(Pos.CENTER_LEFT);
    HBox nameRow = new HBox(GAP, label("casemaker.l10n.languageName"), languageNameField);
    HBox.setHgrow(languageNameField, Priority.ALWAYS);
    nameRow.setAlignment(Pos.CENTER_LEFT);
    HBox soundRow = new HBox(GAP, label("casemaker.soundtrack"), soundtrackField, browse);
    soundRow.setAlignment(Pos.CENTER_LEFT);

    VBox bar = new VBox(GAP, langRow, nameRow, soundRow);
    bar.setPadding(new Insets(PAD, PAD, GAP, PAD));
    return bar;
  }

  // ---- Language management -----------------------------------------------------------------

  private void addLanguage() {
    String code = newLanguageField.getText() == null ? "" : newLanguageField.getText().trim();
    if (!code.isEmpty()) {
      draft.addLanguage(code);
      newLanguageField.clear();
      refresh();
      languagePicker.getSelectionModel().select(code);
    }
  }

  private void removeLanguage() {
    String lang = languagePicker.getValue();
    if (lang != null && !LocalizedText.PRIMARY.equals(lang)) {
      draft.removeLanguage(lang);
      refresh();
    }
  }

  private void onLanguageSelected(String lang) {
    if (lang != null) {
      languageNameField.setText(draft.getLanguageName(lang));
      rebuildFields(lang);
      if (onLanguageChange != null) {
        onLanguageChange.accept(lang);
      }
    }
  }

  /**
   * Selects a language in this tab's picker (rebuilding its field editors) — used to keep the tab in
   * step with the sidebar's shared "Editing language" selector. A no-op when the language is unknown
   * or already selected, so it never loops with the sidebar.
   */
  public void selectLanguage(String lang) {
    if (lang != null
        && draft.getLanguages().contains(lang)
        && !lang.equals(languagePicker.getValue())) {
      languagePicker.getSelectionModel().select(lang);
    }
  }

  /** Notified when the author picks a language here, so the shared selector can stay in sync. */
  public void setOnLanguageChange(java.util.function.Consumer<String> listener) {
    this.onLanguageChange = listener;
  }

  /** Repopulates the language picker and rebuilds the field editors. */
  public void refresh() {
    String selected = languagePicker.getValue();
    languagePicker.getItems().setAll(draft.getLanguages());
    // Null guard: getLanguages() is immutable (contains(null) throws) and the picker is empty until
    // a language is selected.
    if (selected != null && draft.getLanguages().contains(selected)) {
      languagePicker.getSelectionModel().select(selected);
      rebuildFields(selected);
    } else {
      languagePicker.getSelectionModel().select(LocalizedText.PRIMARY);
    }
  }

  // ---- Field editors -----------------------------------------------------------------------

  private void rebuildFields(String lang) {
    fieldList.getChildren().clear();
    List<Field> fields = collectFields();
    if (fields.isEmpty()) {
      Label empty = new Label(L10n.t("casemaker.l10n.empty"));
      empty.getStyleClass().add("casemaker-hint");
      empty.setWrapText(true);
      fieldList.getChildren().add(empty);
      return;
    }
    for (Field field : fields) {
      fieldList.getChildren().add(fieldRow(field, lang));
    }
  }

  private Region fieldRow(Field field, String lang) {
    Label label = new Label(field.label());
    label.getStyleClass().add("sidebar-label");

    String primary = field.text().get(LocalizedText.PRIMARY);
    Label reference =
        new Label(L10n.t("casemaker.l10n.reference") + ": " + (primary == null ? "—" : primary));
    reference.getStyleClass().add("casemaker-hint");
    reference.setWrapText(true);

    TextField editor = new TextField(field.text().get(lang));
    editor.textProperty().addListener((o, a, b) -> field.text().set(lang, b));
    editor.setMaxWidth(Double.MAX_VALUE);

    VBox row = new VBox(2, label, reference, editor);
    row.getStyleClass().add("casemaker-contradiction");
    row.setPadding(new Insets(GAP));
    return row;
  }

  /** Walks the draft and collects every translatable field, in a readable order. */
  private List<Field> collectFields() {
    List<Field> fields = new ArrayList<>();
    fields.add(new Field(L10n.t("casemaker.l10n.field.title"), draft.titleText()));
    fields.add(new Field(L10n.t("casemaker.l10n.field.invitation"), draft.invitationText()));
    fields.add(new Field(L10n.t("casemaker.l10n.field.description"), draft.descriptionText()));
    // Per-language character names (the primary/default is also editable in the sidebar details
    // form). Lets the author write e.g. the assistant's name in Arabic for the Arabic playthrough.
    fields.add(new Field(L10n.t("casemaker.field.detectiveName"), draft.detectiveNameText()));
    fields.add(new Field(L10n.t("casemaker.field.helperName"), draft.helperNameText()));

    for (RoomDraft room : draft.getRooms()) {
      // Per-language Display Name (.scratch/gui-localized-case-names); the Universal name (room.
      // getName()) stays the command-safe identifier.
      fields.add(
          new Field(
              L10n.t("casemaker.l10n.field.roomName", room.getName()), room.displayNameText()));
      fields.add(
          new Field(
              L10n.t("casemaker.l10n.field.roomDesc", room.getName()), room.descriptionText()));
      for (ObjectDraft object : room.getObjects()) {
        fields.add(
            new Field(
                L10n.t("casemaker.l10n.field.objName", object.getName()),
                object.displayNameText()));
        fields.add(
            new Field(
                L10n.t("casemaker.l10n.field.objExamine", object.getName()), object.examineText()));
        fields.add(
            new Field(
                L10n.t("casemaker.l10n.field.objDeduce", object.getName()), object.deduceText()));
        fields.add(
            new Field(
                L10n.t("casemaker.l10n.field.objDesc", object.getName()),
                object.descriptionText()));
      }
    }

    for (SuspectDraft suspect : draft.getSuspects()) {
      fields.add(
          new Field(
              L10n.t("casemaker.l10n.field.suspectName", suspect.getName()),
              suspect.displayNameText()));
      suspect
          .getStates()
          .forEach(
              (stateName, state) -> {
                fields.add(
                    new Field(
                        L10n.t("casemaker.l10n.field.statement", suspect.getName(), stateName),
                        state.statementText()));
                for (ContradictionDraft rule : state.getContradictions()) {
                  fields.add(
                      new Field(
                          L10n.t("casemaker.l10n.field.success", suspect.getName(), stateName),
                          rule.successMessageText()));
                }
              });
    }

    for (CombineRuleDraft rule : draft.getCombineRules()) {
      fields.add(
          new Field(
              L10n.t("casemaker.l10n.field.combine", orId(rule.getResultDeductionId())),
              rule.resultTextLocalized()));
    }
    for (WatsonHintDraft hint : draft.getWatsonHints()) {
      fields.add(
          new Field(L10n.t("casemaker.l10n.field.hint", orId(hint.getId())), hint.textLocalized()));
    }
    for (RankTierDraft tier : draft.getRankTiers()) {
      fields.add(
          new Field(
              L10n.t("casemaker.l10n.field.rankDesc", orId(tier.getRankName())),
              tier.descriptionText()));
      fields.add(
          new Field(
              L10n.t("casemaker.l10n.field.rankWin", orId(tier.getRankName())),
              tier.winningStatementText()));
    }
    int taskNo = 1;
    for (LocalizedText task : draft.getTaskTexts()) {
      fields.add(new Field(L10n.t("casemaker.l10n.field.task", taskNo++), task));
    }
    int qNo = 1;
    for (FinalExamQuestionDraft question : draft.getExamQuestions()) {
      fields.add(new Field(L10n.t("casemaker.l10n.field.examPrompt", qNo), question.promptText()));
      for (ExamSlotDraft slot : question.getSlots()) {
        for (ExamChoiceDraft choice : slot.getChoices()) {
          fields.add(
              new Field(
                  L10n.t("casemaker.l10n.field.examChoice", qNo, choice.getChoiceId()),
                  choice.textLocalized()));
        }
      }
      qNo++;
    }
    return fields;
  }

  private void chooseSoundtrack() {
    FileChooser chooser = new FileChooser();
    chooser
        .getExtensionFilters()
        .add(new FileChooser.ExtensionFilter("Audio", "*.mp3", "*.wav", "*.m4a", "*.ogg"));
    File file = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
    if (file != null) {
      soundtrackField.setText(file.getAbsolutePath());
    }
  }

  private static String orId(String s) {
    return s == null || s.isBlank() ? "?" : s;
  }

  private static Label label(String key) {
    Label label = new Label(L10n.t(key));
    label.getStyleClass().add("sidebar-label");
    return label;
  }
}

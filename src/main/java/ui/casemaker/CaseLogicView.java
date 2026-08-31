package ui.casemaker;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import java.util.ArrayList;
import java.util.List;
import javafx.util.StringConverter;
import ui.casemaker.model.CaseDraft;
import ui.casemaker.model.CombineRuleDraft;
import ui.casemaker.model.ExamChoiceDraft;
import ui.casemaker.model.ExamSlotDraft;
import ui.casemaker.model.FinalExamQuestionDraft;
import ui.casemaker.model.LocalizedText;
import ui.casemaker.model.RankTierDraft;
import ui.casemaker.model.WatsonHintDraft;
import ui.i18n.L10n;

/**
 * The Case Maker's "Case logic" tab (slice 4): Combine Rules, Tasks, Watson Hints, Rank Tiers, and
 * the Final Exam builder. Every Evidence reference (Combine {@code requires}) is chosen from the
 * editor's id registry — Object ids ∪ Deduction ids (DEC-2) — so it can never dangle; Combine
 * results and Contradiction rewards are the mint sites that grow that registry.
 *
 * <p>All text fields are working-language (DEC-8); slice 5 generalises them per language.
 */
public final class CaseLogicView extends BorderPane {

  private static final double GAP = 8;
  private static final double PAD = 16;

  private final CaseDraft draft;

  private final VBox combineList = new VBox(GAP);
  private final ListView<String> taskList = new ListView<>();
  private final VBox hintList = new VBox(GAP);
  private final VBox rankList = new VBox(GAP);
  private final VBox examList = new VBox(GAP);

  public CaseLogicView(CaseDraft draft) {
    this.draft = draft;
    TabPane tabs =
        new TabPane(
            tab("casemaker.logic.combine", buildCombinePane()),
            tab("casemaker.logic.tasks", buildTasksPane()),
            tab("casemaker.logic.hints", buildHintsPane()),
            tab("casemaker.logic.ranks", buildRanksPane()),
            tab("casemaker.logic.exam", buildExamPane()));
    tabs.setPadding(new Insets(GAP, PAD, 0, PAD));
    setCenter(tabs);
  }

  private Tab tab(String key, Region content) {
    Tab tab = new Tab(L10n.t(key), scroll(content));
    tab.setClosable(false);
    return tab;
  }

  /** The language the editor currently authors in (the sidebar's "Editing language" selector). */
  private String lang() {
    return draft.getAuthoringLanguage();
  }

  /**
   * Rebuilds every card list in the new authoring language so combine results, tasks, Watson hints,
   * rank tiers, and exam prompts/choices all switch language in place. Called when the sidebar
   * "Editing language" changes.
   */
  public void refreshLanguage() {
    populateCombine();
    populateTasks();
    populateHints();
    populateRanks();
    populateExam();
  }

  // ---- Combine rules -----------------------------------------------------------------------

  private Region buildCombinePane() {
    populateCombine();
    Button add = new Button(L10n.t("casemaker.logic.combine.add"));
    add.setOnAction(e -> combineList.getChildren().add(buildCombineCard(draft.addCombineRule())));
    return paneWith(combineList, add);
  }

  private void populateCombine() {
    combineList.getChildren().clear();
    for (CombineRuleDraft rule : draft.getCombineRules()) {
      combineList.getChildren().add(buildCombineCard(rule));
    }
  }

  private Region buildCombineCard(CombineRuleDraft rule) {
    VBox requires = new VBox(4);
    rule.getRequires().forEach(id -> requires.getChildren().add(requireRow(rule, requires, id)));

    ComboBox<String> addRequire = new ComboBox<>();
    addRequire.setPromptText(L10n.t("casemaker.logic.combine.addRequirement"));
    addRequire.setOnShowing(e -> addRequire.getItems().setAll(draft.evidenceChoices()));
    addRequire.setOnAction(
        e -> {
          String id = addRequire.getValue();
          if (id != null) {
            rule.addRequire(id);
            requires.getChildren().setAll();
            rule.getRequires()
                .forEach(r -> requires.getChildren().add(requireRow(rule, requires, r)));
            addRequire.getSelectionModel().clearSelection();
          }
        });

    TextField result = new TextField(rule.getResultDeductionId());
    result.textProperty().addListener((o, a, b) -> rule.setResultDeductionId(blankToNull(b)));
    TextField resultText = new TextField(rule.resultTextLocalized().get(lang()));
    resultText
        .textProperty()
        .addListener((o, a, b) -> rule.resultTextLocalized().set(lang(), blankToNull(b)));
    Spinner<Integer> tokens =
        new Spinner<>(0, 20, rule.getTokenReward() == null ? 1 : rule.getTokenReward());
    tokens.setEditable(true);
    tokens.valueProperty().addListener((o, a, b) -> rule.setTokenReward(b));
    CheckBox repeatable = new CheckBox(L10n.t("casemaker.logic.combine.repeatable"));
    repeatable.setSelected(rule.isRepeatable());
    repeatable.selectedProperty().addListener((o, a, b) -> rule.setRepeatable(b));

    Button remove =
        removeButton(() -> combineList.getChildren().remove(currentCard(combineList, rule)));
    VBox card =
        card(
            labeled("casemaker.logic.combine.requires", requires),
            addRequire,
            labeled("casemaker.logic.combine.result", result),
            labeled("casemaker.logic.combine.resultText", resultText),
            labeled("casemaker.logic.combine.tokenReward", tokens),
            repeatable,
            remove);
    card.setUserData(rule);
    return card;
  }

  private Region requireRow(CombineRuleDraft rule, VBox owner, String id) {
    Label label = new Label("• " + id);
    HBox.setHgrow(label, Priority.ALWAYS);
    label.setMaxWidth(Double.MAX_VALUE);
    Button remove = new Button("✕");
    HBox row = new HBox(GAP, label, remove);
    row.setAlignment(Pos.CENTER_LEFT);
    remove.setOnAction(
        e -> {
          rule.removeRequire(id);
          owner.getChildren().remove(row);
        });
    return row;
  }

  // ---- Tasks -------------------------------------------------------------------------------

  private Region buildTasksPane() {
    populateTasks();
    taskList.setEditable(false);
    VBox.setVgrow(taskList, Priority.ALWAYS);
    taskList.setMinHeight(200);

    TextField field = new TextField();
    field.setPromptText(L10n.t("casemaker.logic.tasks.prompt"));
    HBox.setHgrow(field, Priority.ALWAYS);
    Button add = new Button(L10n.t("casemaker.logic.tasks.add"));
    Runnable addTask =
        () -> {
          String text = field.getText() == null ? "" : field.getText().trim();
          if (!text.isEmpty()) {
            // Author the task in the current editing language (other languages stay blank).
            draft.addTaskFor(lang(), text);
            populateTasks();
            field.clear();
          }
        };
    add.setOnAction(e -> addTask.run());
    field.setOnAction(e -> addTask.run());
    Button remove = new Button(L10n.t("casemaker.logic.remove"));
    remove.setOnAction(
        e -> {
          int i = taskList.getSelectionModel().getSelectedIndex();
          if (i >= 0) {
            draft.removeTask(i);
            populateTasks();
          }
        });

    VBox box = new VBox(GAP, taskList, new HBox(GAP, field, add), new HBox(GAP, remove));
    box.setPadding(new Insets(PAD));
    return box;
  }

  /** Shows each task in the current editing language, falling back to the primary-language text. */
  private void populateTasks() {
    List<String> labels = new ArrayList<>();
    for (LocalizedText task : draft.getTaskTexts()) {
      String value = task.get(lang());
      if (value == null || value.isBlank()) {
        value = task.get(); // fall back to the primary language so the row is never blank
      }
      labels.add(value == null ? "" : value);
    }
    taskList.getItems().setAll(labels);
  }

  // ---- Watson hints ------------------------------------------------------------------------

  private Region buildHintsPane() {
    populateHints();
    Button add = new Button(L10n.t("casemaker.logic.hints.add"));
    add.setOnAction(e -> hintList.getChildren().add(buildHintCard(draft.addWatsonHint())));
    return paneWith(hintList, add);
  }

  private void populateHints() {
    hintList.getChildren().clear();
    for (WatsonHintDraft hint : draft.getWatsonHints()) {
      hintList.getChildren().add(buildHintCard(hint));
    }
  }

  private Region buildHintCard(WatsonHintDraft hint) {
    ComboBox<String> category = new ComboBox<>();
    category.setEditable(true);
    category.getItems().setAll("general", "contradiction", "red_herring");
    category.setValue(hint.getCategory());
    category.valueProperty().addListener((o, a, b) -> hint.setCategory(b));
    TextField id = new TextField(hint.getId());
    id.textProperty().addListener((o, a, b) -> hint.setId(blankToNull(b)));
    TextField text = new TextField(hint.textLocalized().get(lang()));
    text.textProperty().addListener((o, a, b) -> hint.textLocalized().set(lang(), blankToNull(b)));

    Button remove = removeButton(() -> hintList.getChildren().remove(currentCard(hintList, hint)));
    VBox card =
        card(
            labeled("casemaker.logic.hints.category", category),
            labeled("casemaker.logic.hints.id", id),
            labeled("casemaker.logic.hints.text", text),
            remove);
    card.setUserData(hint);
    return card;
  }

  // ---- Rank tiers --------------------------------------------------------------------------

  private Region buildRanksPane() {
    populateRanks();
    Button add = new Button(L10n.t("casemaker.logic.ranks.add"));
    add.setOnAction(e -> rankList.getChildren().add(buildRankCard(draft.addRankTier())));
    return paneWith(rankList, add);
  }

  private void populateRanks() {
    rankList.getChildren().clear();
    for (RankTierDraft tier : draft.getRankTiers()) {
      rankList.getChildren().add(buildRankCard(tier));
    }
  }

  private Region buildRankCard(RankTierDraft tier) {
    TextField name = new TextField(tier.getRankName());
    name.textProperty().addListener((o, a, b) -> tier.setRankName(blankToNull(b)));
    Spinner<Integer> max = new Spinner<>(0, 99, tier.getMaxDeductions());
    max.setEditable(true);
    max.valueProperty().addListener((o, a, b) -> tier.setMaxDeductions(b));
    TextField description = new TextField(tier.descriptionText().get(lang()));
    description
        .textProperty()
        .addListener((o, a, b) -> tier.descriptionText().set(lang(), blankToNull(b)));
    CheckBox isDefault = new CheckBox(L10n.t("casemaker.logic.ranks.default"));
    isDefault.setSelected(tier.isDefaultRank());
    isDefault.selectedProperty().addListener((o, a, b) -> tier.setDefaultRank(b));
    TextField winning = new TextField(tier.winningStatementText().get(lang()));
    winning
        .textProperty()
        .addListener((o, a, b) -> tier.winningStatementText().set(lang(), blankToNull(b)));

    Button remove = removeButton(() -> rankList.getChildren().remove(currentCard(rankList, tier)));
    VBox card =
        card(
            labeled("casemaker.logic.ranks.name", name),
            labeled("casemaker.logic.ranks.maxDeductions", max),
            labeled("casemaker.logic.ranks.description", description),
            isDefault,
            labeled("casemaker.logic.ranks.winning", winning),
            remove);
    card.setUserData(tier);
    return card;
  }

  // ---- Final exam --------------------------------------------------------------------------

  private Region buildExamPane() {
    populateExam();
    Button add = new Button(L10n.t("casemaker.logic.exam.addQuestion"));
    add.setOnAction(
        e ->
            examList
                .getChildren()
                .add(
                    buildQuestionCard(draft.addExamQuestion(), examList.getChildren().size() + 1)));
    return paneWith(examList, add);
  }

  private void populateExam() {
    examList.getChildren().clear();
    int i = 1;
    for (FinalExamQuestionDraft question : draft.getExamQuestions()) {
      examList.getChildren().add(buildQuestionCard(question, i++));
    }
  }

  private Region buildQuestionCard(FinalExamQuestionDraft question, int number) {
    Label title = new Label(L10n.t("casemaker.logic.exam.question", number));
    title.getStyleClass().add("casemaker-section");
    TextField prompt = new TextField(question.promptText().get(lang()));
    prompt.textProperty().addListener((o, a, b) -> question.promptText().set(lang(), blankToNull(b)));

    VBox slots = new VBox(GAP);
    question
        .getSlots()
        .forEach(slot -> slots.getChildren().add(buildSlotCard(question, slot, slots)));
    Button addSlot = new Button(L10n.t("casemaker.logic.exam.addSlot"));
    addSlot.setOnAction(
        e -> slots.getChildren().add(buildSlotCard(question, question.addSlot(), slots)));

    Button remove =
        removeButton(() -> examList.getChildren().remove(currentCard(examList, question)));
    VBox card = card(title, labeled("casemaker.logic.exam.prompt", prompt), slots, addSlot, remove);
    card.setUserData(question);
    return card;
  }

  private Region buildSlotCard(FinalExamQuestionDraft question, ExamSlotDraft slot, VBox owner) {
    TextField slotId = new TextField(slot.getSlotId());
    slotId.textProperty().addListener((o, a, b) -> slot.setSlotId(b));

    VBox choices = new VBox(4);
    slot.getChoices()
        .forEach(choice -> choices.getChildren().add(choiceRow(slot, choice, choices)));

    // Correct-answer dropdown over THIS slot's choices (repopulated on open) — can't dangle.
    ComboBox<ExamChoiceDraft> correct = new ComboBox<>();
    correct.setConverter(
        new StringConverter<>() {
          @Override
          public String toString(ExamChoiceDraft c) {
            return c == null ? "" : c.getChoiceId();
          }

          @Override
          public ExamChoiceDraft fromString(String s) {
            return null;
          }
        });
    correct.setOnShowing(e -> correct.getItems().setAll(slot.getChoices()));
    correct.getItems().setAll(slot.getChoices());
    correct.getSelectionModel().select(slot.getCorrectChoice());
    correct.valueProperty().addListener((o, a, b) -> slot.setCorrectChoice(b));

    Button addChoice = new Button(L10n.t("casemaker.logic.exam.addChoice"));
    addChoice.setOnAction(
        e -> {
          ExamChoiceDraft choice = slot.addChoice("c" + (slot.getChoices().size() + 1));
          choices.getChildren().add(choiceRow(slot, choice, choices));
          correct.getItems().setAll(slot.getChoices());
          correct.getSelectionModel().select(slot.getCorrectChoice());
        });

    Button remove = removeButton(() -> owner.getChildren().remove(currentSlotCard(owner, slot)));
    VBox card =
        card(
            labeled("casemaker.logic.exam.slotId", slotId),
            choices,
            addChoice,
            labeled("casemaker.logic.exam.correct", correct),
            remove);
    card.getStyleClass().add("casemaker-contradiction");
    card.setUserData(slot);
    return card;
  }

  private Region choiceRow(ExamSlotDraft slot, ExamChoiceDraft choice, VBox owner) {
    TextField id = new TextField(choice.getChoiceId());
    id.setPrefWidth(70);
    id.textProperty().addListener((o, a, b) -> choice.setChoiceId(b));
    TextField text = new TextField(choice.textLocalized().get(lang()));
    text.setPromptText(L10n.t("casemaker.logic.exam.choiceText"));
    HBox.setHgrow(text, Priority.ALWAYS);
    text.textProperty().addListener((o, a, b) -> choice.textLocalized().set(lang(), blankToNull(b)));
    Button remove = new Button("✕");
    HBox row = new HBox(GAP, id, text, remove);
    row.setAlignment(Pos.CENTER_LEFT);
    remove.setOnAction(
        e -> {
          slot.removeChoice(choice);
          owner.getChildren().remove(row);
        });
    return row;
  }

  // ---- Shared helpers ----------------------------------------------------------------------

  private Region paneWith(VBox list, Button addButton) {
    VBox box = new VBox(PAD, list, addButton);
    box.setPadding(new Insets(PAD));
    return box;
  }

  private VBox card(javafx.scene.Node... children) {
    VBox card = new VBox(GAP, children);
    card.getStyleClass().add("panel");
    card.setPadding(new Insets(PAD));
    return card;
  }

  private Button removeButton(Runnable action) {
    Button remove = new Button(L10n.t("casemaker.logic.remove"));
    remove.setOnAction(e -> action.run());
    return remove;
  }

  /** Finds the card whose userData is the given model, for removal. */
  private Region currentCard(VBox list, Object model) {
    return (Region)
        list.getChildren().stream().filter(n -> n.getUserData() == model).findFirst().orElse(null);
  }

  private Region currentSlotCard(VBox list, Object model) {
    return currentCard(list, model);
  }

  private static Region labeled(String key, Region control) {
    Label label = new Label(L10n.t(key));
    label.getStyleClass().add("sidebar-label");
    control.setMaxWidth(Double.MAX_VALUE);
    VBox box = new VBox(2, label, control);
    return box;
  }

  private ScrollPane scroll(Region content) {
    ScrollPane scroll = new ScrollPane(content);
    scroll.setFitToWidth(true);
    scroll.getStyleClass().add("casemaker-sidebar-scroll");
    return scroll;
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s;
  }
}

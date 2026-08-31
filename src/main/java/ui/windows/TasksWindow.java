package ui.windows;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import ui.i18n.L10n;
import ui.i18n.LocaleStyling;

/**
 * Tasks window for the Detective Game. Displays case tasks and recommendations, allowing players to
 * mark them as completed.
 */
public class TasksWindow {

  private Stage stage;
  private VBox tasksContainer;
  private ui.MainController mainController;
  private java.util.List<String> currentTasks;

  public TasksWindow(ui.MainController mainController) {
    this.mainController = mainController;
    this.currentTasks = new java.util.ArrayList<>();
    initializeWindow();
  }

  private void initializeWindow() {
    stage = new Stage();
    ui.util.AppIcon.applyTo(stage);
    stage.setTitle(L10n.t("toolbar.tasks"));
    // DESIGN.md §4 (.scratch/responsive-resizing issue 03): sensible min size, 8px scale.
    stage.setMinWidth(320);
    stage.setMinHeight(280);

    // Flat vellum surface, exactly like the Help card (gui-tasks-window) — no inner panel/border.
    BorderPane root = new BorderPane();
    root.getStyleClass().add("panel");
    LocaleStyling.apply(root);
    // The task list is reading content: calibrate it to the reading fonts and let it follow the
    // "Reading text size" slider (.scratch/gui-typography-readability); rebuilt on a scale change.
    ui.util.ContentScaleStyling.apply(
        root, ui.util.ContentScale.READING_PREFIX, mainController.getReadingTextScale());

    // Top: shared sub-window title treatment (petrol + ochre rule), like Help's "Command
    // reference".
    Label titleLabel = new Label(L10n.t("tasks.heading"));
    titleLabel.getStyleClass().add("window-title");
    titleLabel.setMaxWidth(Double.MAX_VALUE);
    BorderPane.setMargin(titleLabel, new Insets(0, 0, 12, 0));
    root.setTop(titleLabel);

    // Center: a simple vertical list of compact rows on the flat surface (transparent scroll, like
    // Help); vertical scroll only, descriptions wrap, no horizontal scrollbar.
    tasksContainer = new VBox(8); // Help's row rhythm — compact, even spacing
    ScrollPane scrollPane = new ScrollPane(tasksContainer);
    scrollPane.getStyleClass().add("help-scroll"); // transparent — same treatment as Help
    scrollPane.setFitToWidth(true);
    scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    root.setCenter(scrollPane);

    // Bottom: a quiet sepia tip.
    Label instructionsLabel = new Label(L10n.t("tasks.tip"));
    instructionsLabel.getStyleClass().add("sidebar-label");
    instructionsLabel.setWrapText(true);
    instructionsLabel.setMaxWidth(Double.MAX_VALUE);
    BorderPane.setMargin(instructionsLabel, new Insets(12, 0, 0, 0));
    root.setBottom(instructionsLabel);

    // Create scene
    Scene scene = new Scene(root, 500, 400);

    ui.util.Theme.install(scene);

    stage.setScene(scene);
  }

  /** Adds a task as a compact row: a checkbox + a wrapping label (gui-tasks-window). */
  private void addTask(String taskDescription, boolean completed) {
    CheckBox checkBox = new CheckBox();
    checkBox.setSelected(completed);

    Label taskLabel = new Label(taskDescription);
    taskLabel.getStyleClass().add("task-label");
    taskLabel.setWrapText(true);
    taskLabel.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(taskLabel, Priority.ALWAYS);

    HBox row = new HBox(8, checkBox, taskLabel);
    row.setAlignment(Pos.TOP_LEFT); // checkbox aligns with the first line of a wrapped label
    if (completed) {
      row.getStyleClass().add("task-completed"); // strike-through via .task-completed > .label
    }
    checkBox
        .selectedProperty()
        .addListener(
            (obs, oldVal, newVal) -> {
              row.getStyleClass().remove("task-completed");
              if (newVal) {
                row.getStyleClass().add("task-completed");
              }
              if (mainController != null) {
                mainController.updateTaskState(taskDescription, newVal);
              }
            });

    tasksContainer.getChildren().add(row);
  }

  public void close() {
    if (stage != null) {
      stage.close();
    }
  }

  /** Clears all tasks. */
  public void clearTasks() {
    tasksContainer.getChildren().clear();
  }

  /** Loads tasks from a list of strings. */
  public void loadTasks(java.util.List<String> tasks, java.util.Map<String, Boolean> taskStates) {
    clearTasks();
    this.currentTasks.clear();

    if (tasks != null) {
      for (String task : tasks) {
        boolean isCompleted = taskStates.getOrDefault(task, false);
        addTask(task, isCompleted);
      }
      this.currentTasks.addAll(tasks);
    }
  }

  /** Shows the tasks window. */
  public void show() {
    if (stage != null) {
      stage.show();
      stage.toFront();
    }
  }

  /** Hides the tasks window. */
  public void hide() {
    if (stage != null) {
      stage.hide();
    }
  }

  public boolean isShowing() {
    return stage != null && stage.isShowing();
  }
}

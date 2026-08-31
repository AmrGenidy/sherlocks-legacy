package ui.windows;

import common.dto.ChatMessage;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import ui.MainController;
import ui.i18n.L10n;
import ui.i18n.LocaleStyling;

/**
 * Chat window for the Detective Game multiplayer mode. Allows players to communicate with each
 * other during the game.
 */
public class ChatWindow {

  private Stage stage;
  private MainController mainController;
  private ListView<String> chatListView;
  private TextField chatInputField;
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

  public ChatWindow(MainController controller) {
    this.mainController = controller;
    initializeWindow();
  }

  private void initializeWindow() {
    stage = new Stage();
    ui.util.AppIcon.applyTo(stage);
    stage.setTitle(L10n.t("toolbar.chat"));
    // DESIGN.md §4 (.scratch/responsive-resizing issue 03): sensible min size, 8px scale.
    stage.setMinWidth(320);
    stage.setMinHeight(320);

    BorderPane root = new BorderPane();
    root.setPadding(new Insets(10));
    root.getStyleClass().add("panel");
    LocaleStyling.apply(root);

    VBox centerBox = new VBox(5);

    Label chatLabel = new Label(L10n.t("chat.historyLabel"));
    chatLabel.getStyleClass().add("panel-title");

    chatListView = new ListView<>();
    chatListView.setPrefHeight(400);

    centerBox.getChildren().addAll(chatLabel, chatListView);
    VBox.setVgrow(chatListView, javafx.scene.layout.Priority.ALWAYS);
    root.setCenter(centerBox);

    VBox bottomBox = new VBox(5);
    bottomBox.setPadding(new Insets(10, 0, 0, 0));

    HBox inputBox = new HBox(10);
    inputBox.setAlignment(Pos.CENTER_LEFT);

    chatInputField = new TextField();
    chatInputField.setPromptText(L10n.t("chat.inputPrompt"));
    chatInputField.getStyleClass().add("themed-input");
    chatInputField.setOnAction(e -> sendChatMessage());
    HBox.setHgrow(chatInputField, javafx.scene.layout.Priority.ALWAYS);

    inputBox.getChildren().addAll(chatInputField);

    bottomBox.getChildren().addAll(inputBox);
    root.setBottom(bottomBox);

    Scene scene = new Scene(root, 500, 500);

    ui.util.Theme.install(scene);

    stage.setScene(scene);
  }

  public void close() {
    if (stage != null) {
      stage.close();
    }
  }

  private void sendChatMessage() {
    String message = chatInputField.getText().trim();

    if (message.isEmpty()) {
      return;
    }

    if (mainController != null) {
      mainController.sendCommand("/chat " + message);
    }

    chatInputField.clear();
  }

  public void addChatMessage(String sender, String message) {
    long timestampMillis = System.currentTimeMillis();
    ChatMessage chatMessage = new ChatMessage(sender, message, timestampMillis);
    addChatMessage(chatMessage);
  }

  public void addChatMessage(ChatMessage chatMessage) {
    String formattedMessage = formatMessage(chatMessage);
    chatListView.getItems().add(formattedMessage);
    scrollToBottom();

    if (!stage.isShowing() && mainController != null) {
      mainController.incrementUnreadChat();
    }
  }

  public void loadHistory(List<ChatMessage> history) {
    chatListView.getItems().clear();
    for (ChatMessage message : history) {
      chatListView.getItems().add(formatMessage(message));
    }
    scrollToBottom();
  }

  private String formatMessage(ChatMessage message) {
    LocalTime time =
        Instant.ofEpochMilli(message.getTimestamp()).atZone(ZoneId.systemDefault()).toLocalTime();
    String timestamp = time.format(TIME_FORMATTER);
    return "[" + timestamp + "] " + message.getSenderDisplayId() + ": " + message.getText();
  }

  private void scrollToBottom() {
    if (chatListView.getItems().size() > 0) {
      chatListView.scrollTo(chatListView.getItems().size() - 1);
    }
  }

  public void show() {
    if (stage != null) {
      stage.show();
      stage.toFront();
      chatInputField.requestFocus();
    }
  }

  public void hide() {
    if (stage != null) {
      stage.hide();
    }
  }

  public boolean isShowing() {
    return stage != null && stage.isShowing();
  }
}

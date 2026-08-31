package ui.menu;

import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * The host's join code on a large engraved plate (MENU_DESIGN #3): a vellum plate carrying a small
 * spaced-caps caption, the code itself set big in the typewriter face (a detective's written
 * record, DESIGN.md §3), and a copy affordance. The code is <b>copyable</b> — clicking the code or
 * the Copy plate writes it to the system clipboard and the button flashes a brief confirmation. All
 * text is passed in already localized, so the plate stays a dumb presentation node (like {@link
 * CasebookCover} / {@link Frontispiece}).
 */
public class JoinCodePlate extends VBox {

  public JoinCodePlate(String code, String caption, String copyLabel, String copiedLabel) {
    getStyleClass().add("join-code-plate");
    setAlignment(Pos.CENTER);
    setSpacing(10);
    setFillWidth(false);
    setMaxWidth(VBox.USE_PREF_SIZE);

    Label captionLabel = new Label(caption);
    captionLabel.getStyleClass().add("join-code-caption");

    Label value = new Label(code);
    value.getStyleClass().add("join-code-value");
    value.setCursor(Cursor.HAND);

    Button copy = new Button(copyLabel);
    copy.getStyleClass().add("menu-plate");

    PauseTransition revert = new PauseTransition(Duration.seconds(1.6));
    revert.setOnFinished(event -> copy.setText(copyLabel));

    Runnable doCopy =
        () -> {
          ClipboardContent content = new ClipboardContent();
          content.putString(code);
          Clipboard.getSystemClipboard().setContent(content);
          copy.setText(copiedLabel);
          revert.playFromStart();
        };

    copy.setOnAction(event -> doCopy.run());
    value.setOnMouseClicked(event -> doCopy.run());

    HBox codeRow = new HBox(value);
    codeRow.setAlignment(Pos.CENTER);

    getChildren().addAll(captionLabel, codeRow, copy);
  }
}

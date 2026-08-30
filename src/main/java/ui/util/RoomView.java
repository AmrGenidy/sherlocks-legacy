package ui.util;

import common.dto.RoomDescriptionDTO;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javafx.animation.FadeTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ui.MainController;
import ui.i18n.L10n;
import ui.i18n.LocaleStyling;

/** Custom component for displaying room visualizations with clickable suspects and objects. */
public class RoomView extends StackPane {
  private static final Logger logger = LoggerFactory.getLogger(RoomView.class);

  // Default Dr. Watson spot when a room authored no per-room watsonPos (Case Maker placement tab
  // uses the same default). Kept in sync with SuspectPlacementView's WATSON default.
  private static final double WATSON_DEFAULT_X = 0.85;
  private static final double WATSON_DEFAULT_Y = 0.6;

  private MainController mainController;
  private ImageView roomBackgroundImage;
  private Pane interactiveLayer;
  private Map<String, ClickableElement> suspects;
  private Map<String, ClickableElement> objects;
  // Universal-Name -> Display-Name for the click popup only (.scratch/gui-localized-case-names).
  // Sprites, commands, and labels keep using the Universal name; only the popup title/header shows
  // the Display Name. Absent entries fall back to the Universal name.
  private Map<String, String> objectDisplayNames = new HashMap<>();
  private Map<String, String> occupantDisplayNames = new HashMap<>();
  private Label roomNameLabel;

  public RoomView(MainController controller) {
    this.mainController = controller;
    this.suspects = new HashMap<>();
    this.objects = new HashMap<>();
    initializeView();
  }

  private void initializeView() {
    // Opaque themed mat (DESIGN.md §5/§8): the room plate paints -sl-faded-vellum under the single
    // contained image, so a letterboxed (landscape-in-narrow-pane) image shows themed parchment
    // bars
    // and NOTHING behind RoomView can leak through — never a second/previous image. Re-themes on a
    // dark swap because it is a -sl-* token.
    this.getStyleClass().add("room-view");

    // Background image layer.
    roomBackgroundImage = new ImageView();
    // DESIGN.md §4/§5: the room canvas scales proportionally — preserve aspect ratio (contain),
    // never stretch — and sits centered like a plate on a page, letterbox margins split evenly.
    roomBackgroundImage.setPreserveRatio(true);

    // Bind the fit box to the view; the image is contained within it.
    roomBackgroundImage.fitWidthProperty().bind(this.widthProperty());
    roomBackgroundImage.fitHeightProperty().bind(this.heightProperty());

    // Position the artwork explicitly from RoomViewLayout.renderedImageRect — the SAME rect the
    // sprites anchor to — instead of relying on StackPane auto-centering. The fit bindings mutate
    // the ImageView's bounds during the parent's layout pass, which can leave the StackPane's
    // child placement stale at the left/top edge; an unmanaged node keeps the layoutX/Y we set.
    roomBackgroundImage.setManaged(false);

    // Interactive layer for suspects and objects
    interactiveLayer = new Pane();
    interactiveLayer.getStyleClass().add("room-interactive-layer");
    interactiveLayer.prefWidthProperty().bind(this.widthProperty());
    interactiveLayer.prefHeightProperty().bind(this.heightProperty());

    // Re-anchor the artwork and every sprite to the rendered-artwork rect on any geometry change.
    this.widthProperty().addListener((obs, oldVal, newVal) -> relayoutArtworkAndSprites());
    this.heightProperty().addListener((obs, oldVal, newVal) -> relayoutArtworkAndSprites());
    roomBackgroundImage
        .imageProperty()
        .addListener((obs, oldVal, newVal) -> relayoutArtworkAndSprites());

    // Room name label
    roomNameLabel = new Label(L10n.t("room.namePlaceholder"));
    roomNameLabel.getStyleClass().add("room-name-label");
    StackPane.setAlignment(roomNameLabel, Pos.TOP_CENTER);

    // Add layers to the stack
    // Step 2: Ensure background is bottom-most (first in list)
    this.getChildren().addAll(roomBackgroundImage, interactiveLayer, roomNameLabel);
  }

  /** Loads and displays a room based on RoomDescriptionDTO. */
  public void loadRoom(RoomDescriptionDTO roomDescription) {
    // Clear previous room elements
    clear();

    // Per-language Display Names for the click popup (Universal names still drive
    // sprites/commands).
    objectDisplayNames = roomDescription.getObjectDisplayNames();
    occupantDisplayNames = roomDescription.getOccupantDisplayNames();

    // Set room name — the Room shows by its Display Name everywhere (falls back to the Universal).
    roomNameLabel.setText(roomDescription.getDisplayName());

    // Step 1: Add temporary log
    logger.debug(
        "[DEBUG] RoomView.loadRoom: room={}, imagePath={}",
        roomDescription.getName(),
        roomDescription.getImagePath());

    // Step 4: Use ImageManager correctly
    Image roomImage =
        mainController
            .getImageManager()
            .getRoomImage(roomDescription.getImagePath(), roomDescription.getName());

    if (roomImage != null) {
      roomBackgroundImage.setImage(roomImage);
    } else {
      createPlaceholderBackground(roomDescription.getName());
    }

    Map<String, Double> spriteScales = roomDescription.getSpriteScales(); // horizontal scale
    Map<String, Double> spriteScalesY =
        roomDescription.getSpriteScalesY(); // vertical (absent=uniform)
    Map<String, common.dto.VisualPositionDTO> flips = roomDescription.getFlips();
    // Authored element positions (Case Maker): objects AND suspects share this map, keyed by name.
    Map<String, common.dto.VisualPositionDTO> objPositions = roomDescription.getObjectPositions();
    // Authored name-label offsets (Case Maker placement tab), keyed by element name.
    Map<String, common.dto.VisualPositionDTO> labelOffsets = roomDescription.getLabelOffsets();

    // Watson carries no per-case suspect entry. If the case authored metadata.watsonImageScale* it
    // arrives as an explicit "Dr. Watson" spriteScales entry (engine); otherwise size him to match
    // the room's other suspects (the average of their scales) so he never renders tiny at 1.0.
    Double explicitWatsonScale = spriteScales != null ? spriteScales.get("Dr. Watson") : null;
    double watsonScaleX =
        (explicitWatsonScale != null && explicitWatsonScale > 0)
            ? explicitWatsonScale
            : watsonScaleFrom(roomDescription.getOccupantNames(), spriteScales);
    double watsonScaleY = scaleYFor(spriteScalesY, watsonScaleX, "Dr. Watson");

    // Add suspects
    for (int i = 0; i < roomDescription.getOccupantNames().size(); i++) {
      String suspectName = roomDescription.getOccupantNames().get(i);
      // Authored placement (Case Maker placement tab) wins; otherwise spread suspects horizontally.
      double xPos;
      double yPos;
      if (objPositions != null && objPositions.containsKey(suspectName)) {
        common.dto.VisualPositionDTO pos = objPositions.get(suspectName);
        xPos = pos.getX();
        yPos = pos.getY();
      } else if ("Dr. Watson".equals(suspectName)) {
        // Watson has no authored spot in this room: anchor him at the default corner rather than
        // the suspect spread. Matches the Case Maker placement tab's default watsonPos.
        xPos = WATSON_DEFAULT_X;
        yPos = WATSON_DEFAULT_Y;
      } else {
        xPos = 0.2 + (i * 0.3);
        yPos = 0.5;
      }
      boolean isWatson = "Dr. Watson".equals(suspectName);
      // A suspect/assistant whose authored art is missing gets a fallback preset/placeholder that
      // fills its own frame; the authored sprite scale was calibrated for the (absent) cut-out art
      // and would make the substitute huge, so render the fallback at a neutral 1.0
      // (.scratch/gui-placeholder-size).
      boolean fallbackArt =
          mainController != null
              && (isWatson
                  ? !mainController.isWatsonImageAuthored()
                  : !mainController.isSuspectImageAuthored(suspectName));
      double scaleX =
          isWatson
              ? (fallbackArt ? 1.0 : watsonScaleX)
              : (fallbackArt ? 1.0 : scaleFor(spriteScales, suspectName));
      double scaleY =
          isWatson
              ? (fallbackArt ? 1.0 : watsonScaleY)
              : (fallbackArt ? 1.0 : scaleYFor(spriteScalesY, scaleX, suspectName));
      addSuspect(
          suspectName,
          xPos,
          yPos,
          scaleX,
          scaleY,
          flipXFor(flips, suspectName),
          flipYFor(flips, suspectName),
          rotationFor(flips, suspectName),
          labelOffsets.get(suspectName));
    }

    // Add objects
    for (int i = 0; i < roomDescription.getObjectNames().size(); i++) {
      String objectName = roomDescription.getObjectNames().get(i);
      double xPos;
      double yPos;

      if (objPositions != null && objPositions.containsKey(objectName)) {
        common.dto.VisualPositionDTO pos = objPositions.get(objectName);
        xPos = pos.getX();
        yPos = pos.getY();
      } else {
        // Fallback: Position objects at different locations
        xPos = 0.3 + (i * 0.2);
        yPos = 0.7;
      }
      // As with suspects: an object whose authored art is missing renders its fallback at a neutral
      // 1.0 scale instead of the authored (calibrated-for-missing-art) scale.
      boolean objFallbackArt =
          mainController != null && !mainController.isObjectImageAuthored(objectName);
      double scaleX = objFallbackArt ? 1.0 : scaleFor(spriteScales, objectName);
      addObject(
          objectName,
          xPos,
          yPos,
          scaleX,
          objFallbackArt ? 1.0 : scaleYFor(spriteScalesY, scaleX, objectName),
          flipXFor(flips, objectName),
          flipYFor(flips, objectName),
          rotationFor(flips, objectName),
          labelOffsets.get(objectName));
    }
  }

  /**
   * Vertical scale for {@code name}; absent/invalid falls back to the horizontal scale (uniform).
   */
  private static double scaleYFor(
      Map<String, Double> spriteScalesY, double fallbackX, String name) {
    if (spriteScalesY == null) return fallbackX;
    Double s = spriteScalesY.get(name);
    return (s != null && s > 0) ? s : fallbackX;
  }

  private static boolean flipXFor(Map<String, common.dto.VisualPositionDTO> flips, String name) {
    common.dto.VisualPositionDTO f = flips == null ? null : flips.get(name);
    return f != null && f.getX() != 0;
  }

  private static boolean flipYFor(Map<String, common.dto.VisualPositionDTO> flips, String name) {
    common.dto.VisualPositionDTO f = flips == null ? null : flips.get(name);
    return f != null && f.getY() != 0;
  }

  /** Authored clockwise sprite rotation (degrees) for {@code name}; carried on the flips map. */
  private static double rotationFor(Map<String, common.dto.VisualPositionDTO> flips, String name) {
    common.dto.VisualPositionDTO f = flips == null ? null : flips.get(name);
    return (f != null && f.getRotation() != null) ? f.getRotation() : 0.0;
  }

  /** Per-sprite imageScale from the DTO, defaulting to 1.0 when absent. */
  private static double scaleFor(Map<String, Double> spriteScales, String name) {
    if (spriteScales == null) return 1.0;
    Double s = spriteScales.get(name);
    return (s != null && s > 0) ? s : 1.0;
  }

  /**
   * The scale to render Dr. Watson at so he matches the room's other suspects (he has no
   * spriteScales entry of his own). Uses the average of the present scale values for the other
   * occupants; falls back to 1.0 only when there are none.
   */
  private static double watsonScaleFrom(
      java.util.List<String> occupantNames, Map<String, Double> spriteScales) {
    if (spriteScales == null || spriteScales.isEmpty() || occupantNames == null) {
      return 1.0;
    }
    double sum = 0;
    int count = 0;
    for (String name : occupantNames) {
      if ("Dr. Watson".equals(name)) {
        continue;
      }
      Double s = spriteScales.get(name);
      if (s != null && s > 0) {
        sum += s;
        count++;
      }
    }
    return count > 0 ? sum / count : 1.0;
  }

  /** Creates a placeholder background when image is not available. */
  private void createPlaceholderBackground(String roomName) {
    // Solid parchment-tinted placeholder; DESIGN.md §1 hard-bans decorative gradients.
    if (!this.getStyleClass().contains("room-placeholder-bg")) {
      this.getStyleClass().add("room-placeholder-bg");
    }
  }

  /** Adds a clickable suspect to the room. */
  private void addSuspect(
      String suspectName,
      double xPos,
      double yPos,
      double scaleX,
      double scaleY,
      boolean flipX,
      boolean flipY,
      double rotation,
      common.dto.VisualPositionDTO labelOffset) {
    // Watson's portrait is served separately from metadata.watsonImagePath (getWatsonImage), not
    // the
    // generic suspect art — so his room sprite must load via getWatsonImage too.
    Image image =
        "Dr. Watson".equals(suspectName)
            ? mainController.getWatsonImage()
            : mainController.getSuspectImage(suspectName);
    ClickableElement element =
        createClickableElement(
            suspectName,
            xPos,
            yPos,
            true,
            image,
            scaleX,
            scaleY,
            flipX,
            flipY,
            rotation,
            labelOffset);
    suspects.put(suspectName, element);
    interactiveLayer.getChildren().add(element);
  }

  /** Adds a clickable object to the room. */
  private void addObject(
      String objectName,
      double xPos,
      double yPos,
      double scaleX,
      double scaleY,
      boolean flipX,
      boolean flipY,
      double rotation,
      common.dto.VisualPositionDTO labelOffset) {
    Image image = mainController.getObjectImage(objectName);
    ClickableElement element =
        createClickableElement(
            objectName,
            xPos,
            yPos,
            false,
            image,
            scaleX,
            scaleY,
            flipX,
            flipY,
            rotation,
            labelOffset);
    objects.put(objectName, element);
    interactiveLayer.getChildren().add(element);
  }

  /**
   * Creates a clickable element (suspect or object). Suspects use a larger base factor than objects
   * (DESIGN.md: characters read as people, not props); {@code scaleX/scaleY} multiply that base
   * size independently and {@code flipX/flipY} mirror the sprite. {@code labelOffset} (nullable)
   * places the name caption; null keeps the default spot.
   */
  private ClickableElement createClickableElement(
      String name,
      double normX,
      double normY,
      boolean isSuspect,
      Image image,
      double scaleX,
      double scaleY,
      boolean flipX,
      boolean flipY,
      double rotation,
      common.dto.VisualPositionDTO labelOffset) {
    double baseFactor =
        isSuspect ? RoomViewLayout.SUSPECT_BASE_FACTOR : RoomViewLayout.OBJECT_BASE_FACTOR;
    Double labelDX = labelOffset != null ? labelOffset.getX() : null;
    Double labelDY = labelOffset != null ? labelOffset.getY() : null;
    // The caption shows the per-language Display Name (Universal name is the click/command id).
    // "Dr. Watson" carries an authored helper name in the map when renamed, else the localized label.
    String caption;
    if (isSuspect && "Dr. Watson".equals(name)) {
      caption = occupantDisplayNames.getOrDefault(name, L10n.t("game.watsonSpeaker"));
    } else if (isSuspect) {
      caption = occupantDisplayNames.getOrDefault(name, name);
    } else {
      caption = objectDisplayNames.getOrDefault(name, name);
    }
    ClickableElement element =
        new ClickableElement(
            name,
            caption,
            isSuspect,
            image,
            normX,
            normY,
            baseFactor,
            scaleX,
            scaleY,
            flipX,
            flipY,
            rotation,
            labelDX,
            labelDY);

    // Initial positioning + sizing against the currently rendered artwork rectangle.
    updateElementPosition(element);

    // Set click handler, unless it's a player character. Bound to the sprite ImageView (not the
    // StackPane) so per-pixel picking limits the click to the opaque figure.
    if (!name.startsWith("Player-")) {
      element.setSpriteOnMouseClicked(
          e -> {
            if (isSuspect) {
              showSuspectDialog(name);
            } else {
              showObjectDialog(name);
            }
          });
    }

    return element;
  }

  /** Re-anchors the background artwork and every sprite to the rendered-artwork rect. */
  private void relayoutArtworkAndSprites() {
    positionBackgroundImage();
    for (ClickableElement el : suspects.values()) {
      updateElementPosition(el);
    }
    for (ClickableElement el : objects.values()) {
      updateElementPosition(el);
    }
  }

  /**
   * Centers the rendered artwork in the view (offset = (paneSize − renderedSize) / 2 on the
   * leftover axis). Sprites anchor to the same rect, so they move with the centered image.
   */
  private void positionBackgroundImage() {
    RoomViewLayout.Rect rect = currentArtworkRect();
    roomBackgroundImage.relocate(rect.x(), rect.y());
  }

  /** The rectangle the room artwork currently occupies, used to anchor and size sprites. */
  private RoomViewLayout.Rect currentArtworkRect() {
    double w = this.getWidth();
    double h = this.getHeight();
    Image img = roomBackgroundImage.getImage();
    double iw = img != null ? img.getWidth() : 0;
    double ih = img != null ? img.getHeight() : 0;
    return RoomViewLayout.renderedImageRect(w, h, iw, ih);
  }

  private void updateElementPosition(ClickableElement element) {
    double w = this.getWidth();
    double h = this.getHeight();
    // Avoid placing at 0,0 if width/height are not yet initialized.
    if (w == 0 || h == 0) return;

    RoomViewLayout.Rect rect = currentArtworkRect();
    // Base (scale-1) sprite height, then independent width/height from the two scales. Width uses
    // the image aspect so scaleX==scaleY==1 renders exactly as the old preserveRatio path did.
    double baseH = RoomViewLayout.spriteHeight(rect.height(), element.getBaseFactor(), 1.0);
    double fitH = baseH * element.authoredScaleY();
    double fitW = baseH * element.getAspect() * element.authoredScaleX();
    double centerX = RoomViewLayout.anchorX(rect, element.getNormX());
    double centerY = RoomViewLayout.anchorY(rect, element.getNormY());
    // Anchor the sprite's centre on the normalized point so it stays glued to background features.
    element.applyLayout(centerX - fitW / 2.0, centerY - fitH / 2.0, fitW, fitH);
  }

  /** Shows a dialog for interacting with a suspect. */
  private void showSuspectDialog(String suspectName) {
    if ("Dr. Watson".equals(suspectName)) {
      showAskWatsonDialog();
    } else {
      showGenericSuspectDialog(suspectName);
    }
  }

  /**
   * Attaches the application stylesheet to a JavaFX DialogPane. Dialogs use their own internal
   * Stage, so the main Scene's stylesheets don't reach them automatically — without this, dialog
   * content falls back to platform defaults.
   */
  private void applyDialogTheme(javafx.scene.control.DialogPane pane) {
    // Dialogs live in their own Stage; install the base + active theme (DESIGN.md §8) and mirror
    // them per the active language.
    ui.util.Theme.install(pane);
    LocaleStyling.apply(pane);
  }

  /** Shows a dialog for asking Dr. Watson for a hint. */
  /** Shows a dialog for asking Dr. Watson for a hint. */
  private void showAskWatsonDialog() {
    // The assistant's display name: the case's author-defined helper name when present, else the
    // localized "Dr. Watson" chrome. The sprite is still looked up by the stable "Dr. Watson" key.
    String helper = mainController == null ? null : mainController.getActiveHelperName();
    javafx.scene.control.Dialog<String> dialog = new javafx.scene.control.Dialog<>();
    dialog.setTitle(
        helper != null ? L10n.t("room.askHelperTitle", helper) : L10n.t("room.askWatsonTitle"));
    dialog.setHeaderText(
        helper != null ? L10n.t("room.askHelperHeader", helper) : L10n.t("room.askWatsonHeader"));

    ButtonType askButtonType =
        new ButtonType(L10n.t("room.ask"), javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
    ButtonType cancelButtonType =
        new ButtonType(
            L10n.t("common.cancel"), javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
    dialog.getDialogPane().getButtonTypes().addAll(askButtonType, cancelButtonType);

    // Create the content
    javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(10);
    content.setPadding(new javafx.geometry.Insets(20, 20, 10, 20));
    content.getStyleClass().add("dialog-content");

    javafx.scene.control.Label instructionLabel =
        new javafx.scene.control.Label(L10n.t("room.selectTarget"));

    final String generalHint = L10n.t("room.generalHint");
    javafx.scene.control.ComboBox<String> targetComboBox = new javafx.scene.control.ComboBox<>();
    targetComboBox.getItems().add(generalHint);
    targetComboBox.getItems().addAll(mainController.getWatsonTargets());
    targetComboBox.getSelectionModel().selectFirst();

    javafx.scene.control.Label costLabel = new javafx.scene.control.Label(L10n.t("room.costFree"));
    costLabel.getStyleClass().add("cost-label");

    targetComboBox
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (options, oldValue, newValue) -> {
              if (newValue != null && !newValue.equals(generalHint)) {
                costLabel.setText(L10n.t("room.costWatson"));
                if (!costLabel.getStyleClass().contains("cost-label--paid")) {
                  costLabel.getStyleClass().add("cost-label--paid");
                }
              } else {
                costLabel.setText(L10n.t("room.costFree"));
                costLabel.getStyleClass().remove("cost-label--paid");
              }
            });

    content.getChildren().addAll(instructionLabel, targetComboBox, costLabel);
    dialog.getDialogPane().setContent(content);

    applyDialogTheme(dialog.getDialogPane());

    // Convert the result
    dialog.setResultConverter(
        dialogButton -> {
          if (dialogButton == askButtonType) {
            return targetComboBox.getSelectionModel().getSelectedItem();
          }
          return null;
        });

    Optional<String> result = dialog.showAndWait();

    result.ifPresent(
        target -> {
          if (target.equals(generalHint)) {
            mainController.sendCommand("ask watson");
            showSpeechBubble(
                suspects.get("Dr. Watson"),
                helper != null ? L10n.t("room.askingHelper", helper) : L10n.t("room.askingWatson"));
          } else {
            mainController.sendCommand("ask watson " + target);
            showSpeechBubble(suspects.get("Dr. Watson"), L10n.t("room.askingAbout", target));
          }
        });
  }

  /** Shows a generic dialog for interacting with any suspect other than Dr. Watson. */
  private void showGenericSuspectDialog(String suspectName) {
    // The popup shows the per-language Display Name; the command below stays on the Universal name.
    String display = occupantDisplayNames.getOrDefault(suspectName, suspectName);
    Alert dialog = new Alert(Alert.AlertType.NONE);
    dialog.setTitle(L10n.t("room.interactTitle", display));
    dialog.setHeaderText(display);
    dialog.setContentText(L10n.t("room.suspectPrompt"));

    ButtonType questionButton = new ButtonType(L10n.t("room.question"));
    ButtonType deduceButton = new ButtonType(L10n.t("room.deduceCost"));
    ButtonType cancelButton =
        new ButtonType(
            L10n.t("common.cancel"), javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);

    dialog.getButtonTypes().setAll(questionButton, deduceButton, cancelButton);

    applyDialogTheme(dialog.getDialogPane());

    // Add Image to Dialog
    Image image = mainController.getSuspectImage(suspectName);
    ImageView imageView = new ImageView(image);
    imageView.setFitWidth(100);
    imageView.setFitHeight(100);
    imageView.setPreserveRatio(true);
    imageView.setSmooth(true); // 100px from ~1264px art — filter it
    dialog.setGraphic(imageView);

    Optional<ButtonType> result = dialog.showAndWait();

    if (result.isPresent()) {
      if (result.get() == questionButton) {
        mainController.sendCommand(
            "question " + suspectName); // Universal name resolves the command
        showSpeechBubble(suspects.get(suspectName), L10n.t("room.questioning", display));
      } else if (result.get() == deduceButton) {
        mainController.sendCommand("deduce " + suspectName);
        showSpeechBubble(suspects.get(suspectName), L10n.t("room.analyzing", display));
      }
    }
  }

  /** Shows a dialog for interacting with an object. */
  private void showObjectDialog(String objectName) {
    // The popup shows the per-language Display Name; the command below stays on the Universal name.
    String display = objectDisplayNames.getOrDefault(objectName, objectName);
    Alert dialog = new Alert(Alert.AlertType.NONE);
    dialog.setTitle(L10n.t("room.interactTitle", display));
    dialog.setHeaderText(display);
    dialog.setContentText(L10n.t("room.objectPrompt"));

    ButtonType examineButton = new ButtonType(L10n.t("room.examine"));
    ButtonType deduceButton = new ButtonType(L10n.t("room.deduceCost"));
    ButtonType cancelButton =
        new ButtonType(
            L10n.t("common.cancel"), javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);

    dialog.getButtonTypes().setAll(examineButton, deduceButton, cancelButton);

    applyDialogTheme(dialog.getDialogPane());

    // Add Image to Dialog
    Image image = mainController.getObjectImage(objectName);
    ImageView imageView = new ImageView(image);
    imageView.setFitWidth(100);
    imageView.setFitHeight(100);
    imageView.setPreserveRatio(true);
    imageView.setSmooth(true); // 100px from ~1264px art — filter it
    dialog.setGraphic(imageView);

    Optional<ButtonType> result = dialog.showAndWait();

    if (result.isPresent()) {
      if (result.get() == examineButton) {
        mainController.sendCommand("examine " + objectName); // Universal name resolves the command
        showSpeechBubble(objects.get(objectName), L10n.t("room.examining", display));
      } else if (result.get() == deduceButton) {
        mainController.sendCommand("deduce " + objectName);
        showSpeechBubble(objects.get(objectName), L10n.t("room.deducingFrom", display));
      }
    }
  }

  /** Shows a speech bubble above an element with a message. */
  private void showSpeechBubble(ClickableElement element, String message) {
    if (element == null) return;

    Label bubble = new Label(message);
    bubble.getStyleClass().add("speech-bubble");
    bubble.setWrapText(true);
    bubble.setMaxWidth(200);

    // Position bubble above the element
    bubble.setTranslateX(element.getTranslateX() - 60);
    bubble.setTranslateY(element.getTranslateY() - 80);

    interactiveLayer.getChildren().add(bubble);

    // Fade out and remove after 3 seconds
    FadeTransition fadeOut = new FadeTransition(Duration.seconds(1), bubble);
    fadeOut.setDelay(Duration.seconds(2));
    fadeOut.setFromValue(1.0);
    fadeOut.setToValue(0.0);
    fadeOut.setOnFinished(e -> interactiveLayer.getChildren().remove(bubble));
    fadeOut.play();
  }

  /** Shows a speech bubble with a response message (called from external updates). */
  public void showResponseBubble(String targetName, String response) {
    ClickableElement element = suspects.get(targetName);
    if (element == null) {
      element = objects.get(targetName);
    }

    if (element != null) {
      showSpeechBubble(element, response);
    }
  }

  /** Clears all room elements and resets the view to a default state. */
  public void clear() {
    suspects.clear();
    objects.clear();
    objectDisplayNames = new HashMap<>();
    occupantDisplayNames = new HashMap<>();
    interactiveLayer.getChildren().clear();
    roomBackgroundImage.setImage(null);
    roomNameLabel.setText(L10n.t("room.namePlaceholder"));
    // Reset any inline placeholder styling; the StackPane falls back to its CSS classes.
    this.setStyle("");
  }

  /** Inner class representing a clickable element (suspect or object). */
  private static class ClickableElement extends StackPane {
    private String name;
    // The per-language Display Name shown on the caption; the Universal `name` stays the command id.
    private String caption;
    private boolean isSuspect;
    private Image image;
    private double normX;
    private double normY;
    private final double baseFactor;
    // Independent horizontal/vertical scale + mirror flags. flipSignX/Y are ±1 factors folded into
    // the ImageView's scaleX/scaleY so a flip mirrors the sprite (kept when hover re-applies
    // scale).
    private final double scaleX;
    private final double scaleY;
    private final double flipSignX;
    private final double flipSignY;
    // Authored clockwise rotation in degrees, applied to the sprite ImageView about its centre.
    // Composes with the flip (scaleX/Y sign) and the non-uniform fit sizing.
    private final double rotation;
    // Authored name-label offset from the sprite centre (fraction of sprite height); null = the
    // default "just below the sprite" position.
    private final Double labelDX;
    private final Double labelDY;
    private ImageView imageView;
    // Yellow/ochre silhouette outline (.scratch/...): a theme-coloured DropShadow on the
    // transparent-background sprite renders as a glowing line that hugs the figure/object outline —
    // replacing the old circular clip + ring.
    private DropShadow outline;

    // Outline intensity: resting vs hover (a sharper, slightly wider glow on hover).
    private static final double OUTLINE_RADIUS = 6;
    private static final double OUTLINE_SPREAD = 0.85;
    private static final double OUTLINE_RADIUS_HOVER = 10;
    private static final double OUTLINE_SPREAD_HOVER = 0.9;

    public ClickableElement(
        String name,
        String caption,
        boolean isSuspect,
        Image image,
        double normX,
        double normY,
        double baseFactor,
        double scaleX,
        double scaleY,
        boolean flipX,
        boolean flipY,
        double rotation,
        Double labelDX,
        Double labelDY) {
      this.name = name;
      this.caption = caption;
      this.isSuspect = isSuspect;
      this.image = image;
      this.normX = normX;
      this.normY = normY;
      this.baseFactor = baseFactor;
      this.scaleX = scaleX;
      this.scaleY = scaleY;
      this.flipSignX = flipX ? -1.0 : 1.0;
      this.flipSignY = flipY ? -1.0 : 1.0;
      this.rotation = rotation;
      this.labelDX = labelDX;
      this.labelDY = labelDY;
      createVisual();
    }

    /** The image's width/height aspect (1.0 when unknown), so width can scale independently. */
    public double getAspect() {
      return (image != null && image.getHeight() > 0) ? image.getWidth() / image.getHeight() : 1.0;
    }

    public double authoredScaleX() {
      return scaleX;
    }

    public double authoredScaleY() {
      return scaleY;
    }

    public double getNormX() {
      return normX;
    }

    public double getNormY() {
      return normY;
    }

    public double getBaseFactor() {
      return baseFactor;
    }

    /** Positions the element's top-left and sets its rendered width + height in pixels. */
    public void applyLayout(double layoutX, double layoutY, double widthPx, double heightPx) {
      imageView.setFitWidth(widthPx);
      imageView.setFitHeight(heightPx);
      setLayoutX(layoutX);
      setLayoutY(layoutY);
    }

    private void createVisual() {
      imageView = new ImageView(image);
      // Independent horizontal/vertical sizing (set in applyLayout), so no aspect lock.
      imageView.setPreserveRatio(false);
      // Filtered scaling. Sprites are drawn far smaller than the authored art, so unfiltered
      // sampling makes flat-colour art look pixelated (ImageManager also resamples at decode time).
      imageView.setSmooth(true);
      // Per-pixel picking (.scratch/sprite-pixel-hit): cut-out PNGs carry large transparent
      // margins,
      // so hover/click must respond to the opaque figure only — not the bounding rectangle. This
      // makes JavaFX test the image alpha under the cursor instead of the box, so neighbouring
      // sprites (and Watson's padded PNG) no longer overlap. The DropShadow glow is NOT part of the
      // pick test, so the outline may still extend visually past the figure — which is fine.
      imageView.setPickOnBounds(false);
      // Initial size, will be overridden by applyLayout.
      imageView.setFitWidth(80);
      imageView.setFitHeight(80);
      // Mirror the sprite per the authored flip flags (kept when hover re-applies scale).
      imageView.setScaleX(flipSignX);
      imageView.setScaleY(flipSignY);
      // Authored rotation about the sprite centre. setRotate is independent of scaleX/Y, so it
      // composes with the flip and the fit sizing (the label is a separate child and stays
      // upright).
      imageView.setRotate(rotation);

      // Silhouette outline: a theme-ochre DropShadow on the transparent-background sprite glows as
      // a
      // line tracing the figure/object outline (no circular clip — the full silhouette shows).
      // Palette.OCHRE follows the active theme (the room re-renders on a theme switch via
      // onThemeChanged, recreating this with the current colour). It is applied ONLY on hover (see
      // the mouse handlers below) — nothing is outlined at rest.
      outline = new DropShadow();
      outline.setColor(Palette.OCHRE);
      outline.setRadius(OUTLINE_RADIUS);
      outline.setSpread(OUTLINE_SPREAD);
      outline.setOffsetX(0);
      outline.setOffsetY(0);
      imageView.setEffect(null); // no outline at rest

      String captionText = (caption != null && !caption.isBlank()) ? caption : name;
      // Show the full Display Name. The plate auto-sizes to the text (no fixed cut-off, no ellipsis);
      // a very long name wraps onto a second line instead of being truncated with dots.
      Label label = new Label(captionText);
      label.getStyleClass().add("character-label");
      label.setWrapText(true);
      label.setMaxWidth(200);
      label.setAlignment(Pos.CENTER);
      label.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
      // The caption is never part of the hit area — only the drawn figure is interactive. Otherwise
      // the wide label rectangle would re-introduce the overlapping boxes we are removing.
      label.setMouseTransparent(true);

      // The caption is UNMANAGED so its width never expands this StackPane. A caption wider than the
      // sprite used to widen the plate, and since the figure is positioned by the sprite width, that
      // pushed the (centred) figure off-centre horizontally — making objects appear to shift
      // left/right when the sprite rescaled between fullscreen and windowed (.scratch/gui-caption-
      // shift). As an unmanaged node it is placed explicitly: centred under the figure by default,
      // or offset by the authored label offset (a fraction of sprite height). The figure stays glued.
      label.setManaged(false);
      Runnable placeCaption =
          () -> {
            label.autosize(); // size the unmanaged label to its (wrapped) content
            double fw = imageView.getFitWidth();
            double fh = imageView.getFitHeight();
            double cw = label.getWidth();
            double ch = label.getHeight();
            double centreX = fw / 2.0 + (labelDX != null ? fh * labelDX : 0.0);
            double centreY = (labelDY != null) ? (fh / 2.0 + fh * labelDY) : (fh + 15.0);
            label.setLayoutX(centreX - cw / 2.0);
            label.setLayoutY(centreY - ch / 2.0);
          };
      imageView.fitWidthProperty().addListener((o, a, b) -> placeCaption.run());
      imageView.fitHeightProperty().addListener((o, a, b) -> placeCaption.run());
      label.layoutBoundsProperty().addListener((o, a, b) -> placeCaption.run());
      // Re-place once the sprite is actually in the scene so the caption is sized AFTER its CSS font
      // is applied — otherwise the very first fullscreen paint measures the label before styling and
      // it comes out zero-sized (invisible until a window resize re-ran the sizing).
      this.sceneProperty()
          .addListener(
              (o, was, now) -> {
                if (now != null) {
                  javafx.application.Platform.runLater(placeCaption);
                }
              });
      placeCaption.run();

      this.getChildren().addAll(imageView, label);

      // Make non-player characters interactive
      if (!name.startsWith("Player-")) {
        this.getStyleClass().add("clickable-element");
        // The StackPane itself must not be a pick target on its empty (transparent) area: it
        // carries
        // no background, so pickOnBounds(false) leaves only the imageView's opaque pixels pickable.
        // The hand cursor from .clickable-element is inherited down to the imageView, so it still
        // shows over the figure.
        this.setPickOnBounds(false);

        // Hover + click live on the imageView, whose pickOnBounds(false) restricts them to the
        // opaque figure. Hover shows the ochre silhouette outline on THIS sprite only (nothing is
        // outlined at rest) and grows the sprite a touch. The 1.1 scale is about the sprite centre
        // (JavaFX's default scale pivot), so the opaque region only expands under a stationary
        // cursor — the pixel stays inside the figure and enter/exit does not flicker.
        imageView.setOnMouseEntered(
            e -> {
              imageView.setScaleX(flipSignX * 1.1);
              imageView.setScaleY(flipSignY * 1.1);
              outline.setRadius(OUTLINE_RADIUS_HOVER);
              outline.setSpread(OUTLINE_SPREAD_HOVER);
              imageView.setEffect(outline);
            });

        imageView.setOnMouseExited(
            e -> {
              imageView.setScaleX(flipSignX);
              imageView.setScaleY(flipSignY);
              outline.setRadius(OUTLINE_RADIUS);
              outline.setSpread(OUTLINE_SPREAD);
              imageView.setEffect(null); // remove the outline on mouse-out
            });
      }
    }

    /**
     * Routes a click handler onto the sprite {@link ImageView} (per-pixel picking), so only the
     * opaque figure — not the StackPane's transparent margins or the caption — opens the dialog.
     */
    public void setSpriteOnMouseClicked(
        javafx.event.EventHandler<? super javafx.scene.input.MouseEvent> handler) {
      imageView.setOnMouseClicked(handler);
    }

    public String getName() {
      return name;
    }
  }
}

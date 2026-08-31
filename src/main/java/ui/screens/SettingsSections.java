package ui.screens;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import ui.MainController;
import ui.i18n.L10n;
import ui.menu.WaxSealToggle;
import ui.settings.AppSettings;

/**
 * The reusable Settings section builders (.scratch/gui-ingame-settings), shared by the full-window
 * main-menu {@link SettingsController} and the trimmed in-game Settings overlay ({@code
 * GameScreenController.showInGameSettings}). Both build from the SAME {@link MainController} and
 * {@link AppSettings}, so audio, the two text-size sliders, the theme toggle and the language
 * plates behave identically and changes persist + apply live wherever they are shown.
 *
 * <p>The text-size sections take a {@code withHint} flag: the long font-slider explanation
 * paragraph is shown only in the main-menu Settings (the in-game overlay keeps the sliders + live
 * previews but drops the explanation).
 */
public class SettingsSections {

  private final MainController shell;

  public SettingsSections(MainController shell) {
    this.shell = shell;
  }

  // ====================== Sections ======================

  /** Audio: an engraved master-volume slider + a wax-seal mute toggle. */
  public Node audioSection() {
    ui.audio.SoundtrackService audio = shell.getSoundtrackService();
    ui.audio.AudioSettings settings = audio.getSettings();

    Label caption = sectionCaption("settings.audio");

    Slider volume = new Slider(0, 1, settings.volume());
    volume.getStyleClass().addAll("volume-slider", "engraved-slider");
    volume.setMaxWidth(Double.MAX_VALUE);
    volume.setBlockIncrement(0.05);
    volume.valueProperty().addListener((obs, old, value) -> audio.setVolume(value.doubleValue()));
    volume
        .valueChangingProperty()
        .addListener(
            (obs, wasChanging, changing) -> {
              if (!changing) {
                audio.save();
              }
            });
    volume.setOnMouseReleased(event -> audio.save());

    Label volumeCaption = rowLabel("settings.volume");
    VBox volumeRow = new VBox(6, volumeCaption, volume);

    WaxSealToggle mute =
        new WaxSealToggle(
            L10n.t("settings.mute"),
            settings.muted(),
            value -> {
              audio.setMuted(value);
              audio.save();
              shell.playSound("click.wav");
            });

    return new VBox(12, caption, volumeRow, mute);
  }

  /**
   * "Terminal text size": drives ONLY the terminal via {@link MainController#setTerminalTextScale},
   * with a live preview in the terminal typewriter face (a {@code .terminal-line} in a vellum
   * card).
   */
  public Node terminalTextSizeSection(boolean withHint) {
    javafx.scene.text.Text sample =
        new javafx.scene.text.Text(L10n.t("settings.terminalTextSize.sample"));
    sample.getStyleClass().add("terminal-line");
    javafx.scene.text.TextFlow well = new javafx.scene.text.TextFlow(sample);
    well.getStyleClass().add("terminal-flow");
    well.setMaxWidth(540);
    VBox preview = new VBox(well);
    preview.getStyleClass().add("panel");

    return scaleSection(
        "settings.terminalTextSize",
        "settings.terminalTextSize.hint",
        shell.getTerminalTextScale(),
        shell::setTerminalTextScale,
        preview,
        withHint);
  }

  /**
   * "Reading text size": drives the reading content (popups/journal/case file/exam/room text/Watson
   * hints + pinboard notes) via {@link MainController#setReadingTextScale}, with a live preview in
   * the reading/body face (a wrapping {@code .dialogue-bubble-content} sample).
   */
  public Node readingTextSizeSection(boolean withHint) {
    Label sample = new Label(L10n.t("settings.readingTextSize.sample"));
    sample.setWrapText(true);
    sample.setMaxWidth(540);
    sample.getStyleClass().add("dialogue-bubble-content");
    VBox preview = new VBox(sample);
    preview.getStyleClass().add("panel");

    return scaleSection(
        "settings.readingTextSize",
        "settings.readingTextSize.hint",
        shell.getReadingTextScale(),
        shell::setReadingTextScale,
        preview,
        withHint);
  }

  /** Theme: a wax-seal "Candlelight" toggle switching light ⇆ dark (DESIGN.md §8). */
  public Node themeSection() {
    Label caption = sectionCaption("settings.theme");
    boolean dark = AppSettings.DARK.equals(shell.getThemeName());
    WaxSealToggle candlelight =
        new WaxSealToggle(
            L10n.t("theme.candlelight"),
            dark,
            value -> {
              shell.setTheme(value ? AppSettings.DARK : AppSettings.LIGHT);
              shell.playSound("click.wav");
            });
    Label hint = new Label(L10n.t("theme.hint"));
    hint.getStyleClass().add("settings-hint");
    hint.setWrapText(true);
    return new VBox(12, caption, candlelight, hint);
  }

  /** Language: engraved per-language plates (main-menu Settings only — never shown in-game). */
  public Node languageSection() {
    Label caption = sectionCaption("settings.language");
    // Eight languages wrap to the form width so the row never overflows (the menu never scrolls);
    // each renders in its own script (endonym + lang-name-<code> face).
    FlowPane plates = new FlowPane(10, 10);
    plates.setAlignment(Pos.CENTER_LEFT);
    for (String code : L10n.uiLanguages()) {
      Button plate = languagePlate(L10n.endonym(code), code);
      plate.setMinWidth(120);
      plates.getChildren().add(plate);
    }
    return new VBox(12, caption, plates);
  }

  // ====================== Helpers ======================

  /**
   * Shared builder for an engraved text-size slider section (0.8x–1.6x, snapped to 0.2) with its
   * own live preview. The preview is a descendant of the shared scene root, so the same scale class
   * that drives the game resizes it the instant the slider moves (two-way). {@code withHint}
   * appends the explanatory paragraph (main-menu Settings only).
   */
  private Node scaleSection(
      String captionKey,
      String hintKey,
      double current,
      java.util.function.DoubleConsumer onChange,
      Node preview,
      boolean withHint) {
    Label caption = sectionCaption(captionKey);

    // A DISCRETE notch slider over the text-size STEPS (0.8 / 1.0 "Normal" / 1.2 / 1.4 / 1.6):
    // it snaps notch-to-notch (snapToTicks) and never lands on an in-between value; the visible
    // tick marks (themed ochre in CSS) show the steps the thumb sits in. 20% per notch so every
    // step is an obviously different size.
    Slider slider = new Slider(AppSettings.MIN_TEXT_SCALE, AppSettings.MAX_TEXT_SCALE, current);
    slider.getStyleClass().addAll("text-size-slider", "engraved-slider");
    slider.setMaxWidth(Double.MAX_VALUE);
    slider.setMajorTickUnit(AppSettings.TEXT_SCALE_STEP);
    slider.setBlockIncrement(AppSettings.TEXT_SCALE_STEP);
    slider.setMinorTickCount(0);
    slider.setSnapToTicks(true);
    slider.setShowTickMarks(true);
    slider.valueProperty().addListener((obs, old, value) -> onChange.accept(value.doubleValue()));

    VBox section = new VBox(12, caption, slider, preview);
    if (withHint) {
      Label hint = new Label(L10n.t(hintKey));
      hint.getStyleClass().add("settings-hint");
      hint.setWrapText(true);
      section.getChildren().add(hint);
    }
    return section;
  }

  private Label sectionCaption(String key) {
    Label label = new Label(L10n.t(key));
    label.getStyleClass().add("settings-section");
    return label;
  }

  private Label rowLabel(String key) {
    Label label = new Label(L10n.t(key));
    label.getStyleClass().add("settings-row-label");
    return label;
  }

  private Button languagePlate(String name, String code) {
    Button button = new Button(name);
    button.getStyleClass().add("menu-plate");
    // Render each name in its own script's face, whatever the active UI language (DEC-10).
    button.getStyleClass().add("lang-name-" + code);
    if (code.equals(L10n.language())) {
      button.getStyleClass().add("menu-plate--primary");
    }
    button.setOnAction(
        event -> {
          shell.playSound("click.wav");
          shell.setUiLanguage(code);
        });
    return button;
  }
}

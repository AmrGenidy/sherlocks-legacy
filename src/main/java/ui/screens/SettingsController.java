package ui.screens;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import ui.MainController;
import ui.i18n.L10n;
import ui.menu.MenuPage;
import ui.shell.ScreenController;

/**
 * Settings as a full-window dossier form (MENU_DESIGN #6): a {@link MenuPage} with the full set of
 * sections — Audio, Terminal/Reading text size (WITH their explanatory hints), Language, and Theme.
 * The sections themselves are built by the shared {@link SettingsSections} so the trimmed in-game
 * Settings overlay ({@code GameScreenController.showInGameSettings}) reuses the identical controls,
 * {@link MainController} and {@code AppSettings} (.scratch/gui-ingame-settings).
 *
 * <p>One screen, two entry points (DEC-1): the injected {@code onBack} returns to wherever Settings
 * was opened from (the main-menu gear → the menu; the pause menu → the paused game).
 */
public class SettingsController implements ScreenController {

  private final MainController shell;
  private final Runnable onBack;
  private final SettingsSections sections;
  private final StackPane container = new StackPane();

  public SettingsController(MainController shell, Runnable onBack) {
    this.shell = shell;
    this.onBack = onBack != null ? onBack : () -> {};
    this.sections = new SettingsSections(shell);
  }

  @Override
  public Node getView() {
    return container;
  }

  @Override
  public void onShow() {
    render();
  }

  @Override
  public void onLanguageChanged() {
    render();
  }

  @Override
  public void onThemeChanged() {
    // Redraw the dossier so its MenuPage frame/grain canvas picks up the new palette immediately.
    render();
  }

  @Override
  public boolean usesFullWindow() {
    return true;
  }

  @Override
  public boolean onEscape() {
    onBack.run();
    return true;
  }

  private void render() {
    MenuPage page = new MenuPage(L10n.t("settings.title"), L10n.t("settings.subtitle"));

    VBox form = new VBox(22);
    form.getStyleClass().add("settings-card");
    form.setMaxWidth(620);
    form.setMaxHeight(Region.USE_PREF_SIZE);
    // The full menu Settings: audio, BOTH text-size sliders WITH their explanatory hints,
    // languages,
    // theme. The in-game overlay reuses these same builders with withHint=false and no language
    // row.
    form.getChildren()
        .addAll(
            sections.audioSection(),
            sections.terminalTextSizeSection(true),
            sections.readingTextSizeSection(true),
            sections.languageSection(),
            sections.themeSection());

    StackPane holder = new StackPane(form);
    holder.setMinSize(0, 0);
    StackPane.setAlignment(form, Pos.CENTER);

    // The dossier can be taller than a short window once every section is shown (audio, two
    // text-size
    // sliders + previews, eight language plates, theme). Scroll ONLY the form area vertically so it
    // never spills past the engraved frame or the window edge; the bar appears only when needed
    // (AS_NEEDED) and the title block + Back strip stay fixed. fitToWidth keeps the 620-wide card
    // centred; the holder's min height tracks the viewport so the form stays vertically centred
    // while
    // it fits and scrolls only once it doesn't.
    ScrollPane scroll = new ScrollPane(holder);
    scroll.setFitToWidth(true);
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    scroll.getStyleClass().add("settings-scroll");
    scroll.setMinHeight(0);
    holder
        .minHeightProperty()
        .bind(
            javafx.beans.binding.Bindings.createDoubleBinding(
                () -> scroll.getViewportBounds().getHeight(), scroll.viewportBoundsProperty()));

    page.setContent(scroll);
    page.setBottomStrip(backStrip());

    container.getChildren().setAll(page);
    shell.relayoutScreen();
  }

  private Node backStrip() {
    Button back = new Button(L10n.t("common.back"));
    back.getStyleClass().add("menu-plate");
    back.setMaxWidth(Region.USE_PREF_SIZE);
    back.setOnAction(
        event -> {
          shell.playSound("click.wav");
          onBack.run();
        });
    HBox strip = new HBox(back);
    strip.setAlignment(Pos.CENTER_LEFT);
    strip.getStyleClass().add("menu-bottom-strip");
    return strip;
  }
}

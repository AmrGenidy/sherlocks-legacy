package ui.screens;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Bounds;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.util.Duration;
import singleplayer.SinglePlayerMain;
import ui.MainController;
import ui.i18n.L10n;
import ui.menu.AvatarImages;
import ui.menu.Frontispiece;
import ui.menu.MenuPage;
import ui.menu.quotes.MenuQuotes;
import ui.menu.quotes.QuoteRotator;
import ui.shell.ScreenController;
import ui.util.Palette;

/**
 * Menu screen (ADR-0002): the main menu, the tutorials list, the single-player case-selection
 * <b>gallery</b> (a shelf of engraved casebook covers, MENU_DESIGN #2) and the
 * case-<b>invitation</b> dossier (the invitation letter + a per-case language pick + "Begin
 * investigation"). The main menu and tutorials list keep their terminal parity (numeric input
 * mirrors the buttons, per .scratch/navigation-ux-smoothness); the gallery and dossier are
 * full-window visual screens driven by click/keyboard, not the terminal.
 */
public class MenuController implements ScreenController {

  private enum SubState {
    MAIN,
    TUTORIALS,
    CHOOSING_CASE,
    PROFILE
  }

  /** Tutorial bundle key → tutorial script id, in display order. */
  private static final String[][] TUTORIALS = {
    {"tutorial.move", "move_tutorial"},
    {"tutorial.look", "look_tutorial"},
    {"tutorial.journal", "journal_tutorial"},
    {"tutorial.tasks", "tasks_tutorial"},
    {"tutorial.examine", "examine_tutorial"},
    {"tutorial.question", "question_tutorial"},
    {"tutorial.askWatson", "ask_watson_tutorial"},
    {"tutorial.deduce", "deduce_tutorial"},
    {"tutorial.combine", "combine_tutorial"},
    {"tutorial.contradict", "contradict_tutorial"},
    {"tutorial.pinboard", "pinboard_tutorial"},
    {"tutorial.finalExam", "final_exam_tutorial"}
  };

  // The title/emblem "ink drying" entrance plays once per app run, on the first main-menu showing
  // (MENU_DESIGN Motion: "once"). Static so returning to the menu later never replays it.
  private static boolean inkSettlePlayed = false;

  // The preset portraits bake an oval portrait in the top fraction and a name-plate box beneath it;
  // showing only this top fraction (an ImageView viewport) drops the empty plate (gui-profile-
  // portrait-picker). The picker chips use the same crop for a consistent look.
  private static final double PORTRAIT_VIEWPORT_FRACTION = 0.80;

  /** Side of a square portrait chip in the "Choose your portrait" picker grid. */
  private static final double PICKER_PORTRAIT = 96;

  private final MainController shell;
  private final StackPane container = new StackPane();
  private SubState subState = SubState.MAIN;
  // The transient "Choose your portrait" modal over the profile screen, or null when closed.
  private Node portraitPickerOverlay;
  // The shared case-selection screen (gallery + invitation dossier) while CHOOSING_CASE. It owns
  // the
  // selected case + language and the internal gallery↔dossier navigation.
  private ui.menu.CaseSelectionView caseSelectionView;

  // Rotating epigraph for the frontispiece caption ribbon (.scratch/main-menu; MENU_DESIGN). Loaded
  // lazily; the rotator never repeats back-to-back, the timer changes it every intervalSeconds, and
  // showMainMenu picks a fresh one each time the menu becomes visible (per
  // changeOnReturnToMainMenu).
  private MenuQuotes menuQuotes;
  private QuoteRotator quoteRotator;
  private MenuQuotes.Quote currentQuote;
  private Timeline quoteTimer;
  private Frontispiece currentFrontispiece;

  public MenuController(MainController shell) {
    this.shell = shell;
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
  public void onHide() {
    // Leaving the menu entirely (to game/lobby/exam): stop the epigraph timer.
    stopQuoteRotation();
  }

  @Override
  public void onLanguageChanged() {
    render();
  }

  @Override
  public void onThemeChanged() {
    // The menu's canvas chrome rebuilds on navigation; redraw the live frontispiece glow now so the
    // lamplight recolours immediately on the palette swap (DESIGN.md §8) if the menu is on screen.
    if (currentFrontispiece != null && currentFrontispiece.getScene() != null) {
      currentFrontispiece.onThemeChanged();
    }
  }

  /**
   * The menu owns the whole window for every sub-state (.scratch/main-menu DEC-1/DEC-2): the main
   * menu, the case-selection <b>gallery</b> + <b>invitation</b> dossier, and the <b>tutorials</b>
   * contents cards are all chromed {@link MenuPage}s with no terminal/sidebar/toolbar.
   */
  @Override
  public boolean usesFullWindow() {
    return true;
  }

  @Override
  public boolean onEscape() {
    switch (subState) {
      case PROFILE:
        // Escape closes the portrait picker first (if open), then steps back to the main menu.
        if (portraitPickerOverlay != null) {
          dismissPortraitPicker();
          return true;
        }
        showMainMenu();
        return true;
      case TUTORIALS:
        showMainMenu();
        return true;
      case CHOOSING_CASE:
        // The view steps the dossier back to the gallery, and the gallery back to the main menu.
        if (caseSelectionView != null) {
          return caseSelectionView.handleEscape();
        }
        showMainMenu();
        return true;
      case MAIN:
      default:
        return false;
    }
  }

  @Override
  public boolean handleTerminalInput(String input) {
    switch (subState) {
      case MAIN:
        handleMainMenuInput(input);
        return true;
      case TUTORIALS:
      case CHOOSING_CASE:
        // The tutorials cards, the gallery and the invitation dossier are full-window visual
        // screens with no terminal; selection happens by clicking, not by typing.
        return false;
      default:
        return false;
    }
  }

  /**
   * The menu's numeric choices as bare completions (.scratch/terminal-autocomplete issue 03) — the
   * same numbers the terminal menu prints, so a digit Tab-completes with a labeled chip.
   */
  @Override
  public ui.terminal.CompletionContext completionContext() {
    ui.terminal.CompletionContext.Builder builder = ui.terminal.CompletionContext.builder();
    switch (subState) {
      case MAIN:
        builder
            .bareOption("1", "1. " + L10n.t("menu.singlePlayer"))
            .bareOption("2", "2. " + L10n.t("menu.hostMultiplayer"))
            .bareOption("3", "3. " + L10n.t("menu.joinMultiplayer"))
            .bareOption("4", "4. " + L10n.t("menu.addCustomCase"))
            .bareOption("5", "5. " + L10n.t("menu.tutorials"))
            .bareOption("6", "6. " + L10n.t("menu.quit"));
        break;
      case TUTORIALS:
      case CHOOSING_CASE:
        // Full-window visual screens — no terminal completions.
        break;
      default:
        break;
    }
    return builder.build();
  }

  private void render() {
    switch (subState) {
      case TUTORIALS:
        showTutorials();
        break;
      case PROFILE:
        showProfile();
        break;
      case CHOOSING_CASE:
        // Keep the gallery/dossier position; rebuild it in the freshly chosen UI language.
        if (caseSelectionView != null) {
          caseSelectionView.rerender();
        } else {
          showCaseSelection();
        }
        break;
      case MAIN:
      default:
        showMainMenu();
        break;
    }
  }

  // --- Main menu ---

  /** Marker drawer for the small engraved corner icons (gear, power). */
  private interface IconDrawer {
    void draw(GraphicsContext g, double size);
  }

  /**
   * The main menu, built to match docs/art-refs/main_menu_reference.png (.scratch/main-menu): a
   * full-window {@link MenuPage} with an engraved frontispiece on the left, a weighted plate stack
   * on the right, demoted gear/power corner controls, and a language + version bottom strip. It is
   * laid out on grow/percentage constraints, so it reflows to a centred stack on narrow/tall
   * windows and never scrolls.
   */
  public void showMainMenu() {
    subState = SubState.MAIN;

    MenuPage page = new MenuPage(L10n.t("app.title"), L10n.t("menu.subtitle"));

    // The caption ribbon shows a rotating epigraph: pick a fresh one each time the menu becomes
    // visible (honouring changeOnReturnToMainMenu), resolved to the current UI language.
    Frontispiece frontispiece = new Frontispiece(nextCaptionText());
    currentFrontispiece = frontispiece;

    // The ordered focus ring for arrow/Tab navigation: the plate stack, then the corner controls.
    List<Button> focusOrder = new ArrayList<>();
    VBox stack = buildButtonStack(focusOrder);
    StackPane stackHolder = new StackPane(stack); // fills height, centres the stack vertically
    stackHolder.setMinWidth(360);

    // The profile chip sits high in the TOP-RIGHT corner of the page (gui-profile-reflow
    // follow-up):
    // up in the corner, clear of the corner flourish, the centred title and the button stack. It
    // joins the focus ring right after the plate stack, before the bottom-right corner controls.
    Button profileChip = buildProfileChip();
    focusOrder.add(profileChip);

    page.setContent(buildReflowContent(frontispiece, stackHolder));
    page.setTopRightOverlay(profileChip);
    page.setBottomStrip(buildBottomStrip(focusOrder));
    installKeyNav(page, focusOrder);

    // Page-turn back to the main menu (negative = settles in from the left, opposite to going
    // deeper into a submenu).
    ui.util.Motion.pageTurn(container, page, -1);
    shell.relayoutScreen();
    startQuoteRotation();

    // The title and the engraved emblem settle in like ink drying — once, on first load only.
    if (!inkSettlePlayed) {
      inkSettlePlayed = true;
      ui.util.Motion.inkSettle(page.titleBlock());
      ui.util.Motion.inkSettle(frontispiece);
    }

    // The primary plate (Continue when a save exists, else Single player) takes initial focus.
    if (!focusOrder.isEmpty()) {
      Button primary = focusOrder.get(0);
      javafx.application.Platform.runLater(primary::requestFocus);
    }
  }

  private QuoteRotator quoteRotator() {
    if (quoteRotator == null) {
      menuQuotes = MenuQuotes.load();
      quoteRotator = new QuoteRotator(menuQuotes.quotes(), menuQuotes.rotation().order());
    }
    return quoteRotator;
  }

  /**
   * Picks the caption for a fresh menu showing. A new quote is chosen on the first show and
   * whenever {@code changeOnReturnToMainMenu} is set (so returning from a submenu shows a fresh
   * one); otherwise the current quote is kept and merely re-resolved to the active language.
   */
  private String nextCaptionText() {
    QuoteRotator rotator = quoteRotator();
    if (currentQuote == null || menuQuotes.rotation().changeOnReturnToMainMenu()) {
      MenuQuotes.Quote picked = rotator.next();
      if (picked != null) {
        currentQuote = picked;
      }
    }
    return resolveCaption();
  }

  /** The current quote in the active UI language (en fallback), or the i18n caption if none. */
  private String resolveCaption() {
    String text = currentQuote != null ? currentQuote.text(L10n.language()) : null;
    return text == null || text.isBlank() ? L10n.t("menu.frontispieceCaption") : text;
  }

  /**
   * (Re)starts the epigraph timer: every {@code intervalSeconds} the caption cross-fades to the
   * next quote. No-op when there is nothing to rotate (≤1 quote or no interval).
   */
  private void startQuoteRotation() {
    QuoteRotator rotator = quoteRotator();
    int interval = menuQuotes.rotation().intervalSeconds();
    if (interval <= 0 || rotator.size() <= 1) {
      return;
    }
    if (quoteTimer == null) {
      quoteTimer = new Timeline();
      quoteTimer.setCycleCount(Timeline.INDEFINITE);
    }
    quoteTimer.stop();
    quoteTimer
        .getKeyFrames()
        .setAll(new KeyFrame(Duration.seconds(interval), e -> rotateCaption()));
    quoteTimer.playFromStart();
  }

  private void stopQuoteRotation() {
    if (quoteTimer != null) {
      quoteTimer.stop();
    }
  }

  private void rotateCaption() {
    if (currentFrontispiece == null || currentFrontispiece.getScene() == null) {
      return; // menu not currently on screen — nothing to update
    }
    MenuQuotes.Quote picked = quoteRotator().next();
    if (picked != null) {
      currentQuote = picked;
    }
    currentFrontispiece.crossfadeCaption(resolveCaption(), menuQuotes.rotation().fadeMs());
  }

  /**
   * The weighted plate stack (MENU_DESIGN): a petrol Continue primary only when a save exists, else
   * Single player is the primary; then Single player, Multiplayer, Create a case, Tutorials. Adds
   * each plate to {@code focusOrder} in display order.
   */
  private VBox buildButtonStack(List<Button> focusOrder) {
    VBox stack = new VBox(14);
    stack.setAlignment(Pos.CENTER);
    stack.setFillWidth(true);
    stack.setMaxWidth(480);
    stack.setMaxHeight(Region.USE_PREF_SIZE);

    boolean hasSave = shell.hasResumableGame();
    if (hasSave) {
      Button cont = plate("menu.continue", true, shell::resumeGame);
      focusOrder.add(cont);
      stack.getChildren().add(cont);
    }

    Button single = plate("menu.singlePlayer", !hasSave, this::startSinglePlayerFlow);
    Button multiplayer = plate("menu.multiplayer", false, shell::showMultiplayerHub);
    Button createCase = plate("menu.createCase", false, shell::showCaseMaker);
    Button tutorials = plate("menu.tutorials", false, this::showTutorials);

    focusOrder.add(single);
    focusOrder.add(multiplayer);
    focusOrder.add(createCase);
    focusOrder.add(tutorials);
    stack.getChildren().addAll(single, multiplayer, createCase, tutorials);
    return stack;
  }

  private Button plate(String key, boolean primary, Runnable action) {
    Button button = new Button(L10n.t(key));
    button.getStyleClass().add("menu-plate");
    if (primary) {
      button.getStyleClass().add("menu-plate--primary");
    }
    button.setMaxWidth(Double.MAX_VALUE);
    button.setOnAction(
        event -> {
          shell.playSound("click.wav");
          action.run();
        });
    return button;
  }

  /**
   * Asymmetric content that reflows (DESIGN.md §4): a wide window puts the frontispiece beside the
   * plate stack; a narrow/tall window stacks them vertically, centred. The same two nodes are
   * re-parented between the two arrangements, and the swap only happens when the breakpoint is
   * actually crossed, so resizing never churns the layout.
   */
  private Node buildReflowContent(Frontispiece frontispiece, Node stackHolder) {
    HBox wide = new HBox(48);
    wide.setAlignment(Pos.CENTER);
    wide.setFillHeight(true);
    VBox tall = new VBox(28);
    tall.setAlignment(Pos.CENTER);

    StackPane holder = new StackPane();
    holder.setMinSize(0, 0);

    boolean[] initialized = {false};
    boolean[] stackedNow = {false};
    Runnable reflow =
        () -> {
          double w = holder.getWidth();
          double h = holder.getHeight();
          if (w <= 0 || h <= 0) {
            return;
          }
          boolean stacked = w < h * 1.05 || w < 900;
          if (initialized[0] && stacked == stackedNow[0]) {
            return;
          }
          initialized[0] = true;
          stackedNow[0] = stacked;
          if (stacked) {
            wide.getChildren().clear();
            HBox.setHgrow(frontispiece, Priority.NEVER);
            VBox.setVgrow(frontispiece, Priority.ALWAYS);
            tall.getChildren().setAll(frontispiece, stackHolder);
            holder.getChildren().setAll(tall);
          } else {
            tall.getChildren().clear();
            VBox.setVgrow(frontispiece, Priority.NEVER);
            HBox.setHgrow(frontispiece, Priority.ALWAYS);
            wide.getChildren().setAll(frontispiece, stackHolder);
            holder.getChildren().setAll(wide);
          }
        };
    holder.widthProperty().addListener((obs, a, b) -> reflow.run());
    holder.heightProperty().addListener((obs, a, b) -> reflow.run());
    return holder;
  }

  /**
   * The bottom strip: a small version label on the left; the demoted Language (globe), Settings
   * (gear) and Quit (power) engraved icon buttons on the right — one shared icon-button style.
   * There is no separate text language dropdown (MENU_DESIGN). The corner controls join the focus
   * ring after the plate stack. (The profile chip is pinned top-right by {@code showMainMenu}, not
   * here, so the bottom-left holds only the version.)
   */
  private Node buildBottomStrip(List<Button> focusOrder) {
    Label version = new Label(L10n.t("menu.version"));
    version.getStyleClass().add("menu-version");
    HBox left = new HBox(14, version);
    left.setAlignment(Pos.CENTER_LEFT);

    Button language = iconButton("menu.language", this::drawGlobe);
    language.setOnAction(
        event -> {
          shell.playSound("click.wav");
          showLanguagePopover(language);
        });
    Button settings = iconButton("menu.settings", this::drawGear);
    settings.setOnAction(
        event -> {
          shell.playSound("click.wav");
          shell.showSettings(shell::showMainMenuScreen);
        });
    Button quit = iconButton("menu.quit", this::drawPower);
    quit.setOnAction(
        event -> {
          shell.playSound("click.wav");
          shell.shutdown();
        });
    focusOrder.add(language);
    focusOrder.add(settings);
    focusOrder.add(quit);
    HBox right = new HBox(12, language, settings, quit);
    right.setAlignment(Pos.CENTER_RIGHT);

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox strip = new HBox(left, spacer, right);
    strip.setAlignment(Pos.CENTER);
    strip.getStyleClass().add("menu-bottom-strip");
    return strip;
  }

  private Button iconButton(String tooltipKey, IconDrawer drawer) {
    double size = 22;
    Canvas icon = new Canvas(size, size);
    drawer.draw(icon.getGraphicsContext2D(), size);
    Button button = new Button();
    button.getStyleClass().add("menu-icon-button");
    button.setGraphic(icon);
    button.setTooltip(new javafx.scene.control.Tooltip(L10n.t(tooltipKey)));
    return button;
  }

  /**
   * A thin-line engraved gear (Settings) — DESIGN.md §6 hand-drawn icon, not a flat material glyph.
   */
  private void drawGear(GraphicsContext g, double s) {
    g.clearRect(0, 0, s, s);
    double cx = s / 2;
    double cy = s / 2;
    double rOuter = s * 0.30;
    double rInner = s * 0.14;
    g.setStroke(Palette.SEPIA);
    g.setLineWidth(1.6);
    g.strokeOval(cx - rOuter, cy - rOuter, rOuter * 2, rOuter * 2);
    g.strokeOval(cx - rInner, cy - rInner, rInner * 2, rInner * 2);
    for (int i = 0; i < 8; i++) {
      double a = Math.PI * i / 4;
      double x1 = cx + Math.cos(a) * rOuter;
      double y1 = cy + Math.sin(a) * rOuter;
      double x2 = cx + Math.cos(a) * (rOuter + s * 0.11);
      double y2 = cy + Math.sin(a) * (rOuter + s * 0.11);
      g.strokeLine(x1, y1, x2, y2);
    }
  }

  /** A thin-line engraved power glyph (Quit): a broken ring with a stem at the top. */
  private void drawPower(GraphicsContext g, double s) {
    g.clearRect(0, 0, s, s);
    double cx = s / 2;
    double cy = s / 2 + s * 0.04;
    double r = s * 0.28;
    g.setStroke(Palette.SEPIA);
    g.setLineWidth(1.6);
    g.strokeArc(cx - r, cy - r, r * 2, r * 2, 110, 320, javafx.scene.shape.ArcType.OPEN);
    g.strokeLine(cx, cy - r - s * 0.06, cx, cy - s * 0.02);
  }

  /** A thin-line engraved globe (Language): a circle with a central meridian and parallels. */
  private void drawGlobe(GraphicsContext g, double s) {
    g.clearRect(0, 0, s, s);
    double cx = s / 2;
    double cy = s / 2;
    double r = s * 0.30;
    g.setStroke(Palette.SEPIA);
    g.setLineWidth(1.6);
    g.strokeOval(cx - r, cy - r, r * 2, r * 2);
    g.strokeLine(cx, cy - r, cx, cy + r); // central meridian
    g.strokeOval(cx - r * 0.5, cy - r, r, r * 2); // curved meridian (narrow ellipse)
    g.strokeLine(cx - r, cy, cx + r, cy); // equator
    double hw = r * Math.sqrt(1 - 0.45 * 0.45); // chord half-width at the parallels
    g.strokeLine(cx - hw, cy - r * 0.45, cx + hw, cy - r * 0.45);
    g.strokeLine(cx - hw, cy + r * 0.45, cx + hw, cy + r * 0.45);
  }

  /**
   * Language chooser popover, opened by the globe corner control (MENU_DESIGN): the UI languages
   * listed in their own script, switching the whole UI live. The current language reads as the
   * petrol primary. Replaces the old bottom-left text dropdown.
   */
  private void showLanguagePopover(Node anchor) {
    Popup popup = new Popup();
    popup.setAutoHide(true);

    Label title = new Label(L10n.t("menu.language"));
    title.getStyleClass().add("menu-popover-title");

    // Eight languages: a tidy wrapping grid (~2 columns) so the popover stays compact and never
    // overflows. Each name shows in its own script (endonym + lang-name-<code> face).
    FlowPane grid = new FlowPane(8, 8);
    grid.setAlignment(Pos.CENTER);
    grid.setPrefWrapLength(284);
    for (String code : L10n.uiLanguages()) {
      Button plate = languagePlate(L10n.endonym(code), code, popup);
      plate.setMinWidth(130);
      grid.getChildren().add(plate);
    }

    VBox box = new VBox(10, title, grid);
    box.getStyleClass().add("menu-popover");
    box.setMinWidth(300);
    box.setMaxWidth(320);
    popup.getContent().add(box);
    showAboveAnchor(popup, anchor);
  }

  /**
   * One language option in the chooser popover; the active language reads as the petrol primary.
   */
  private Button languagePlate(String name, String code, Popup popup) {
    Button button = new Button(name);
    button.getStyleClass().add("menu-plate");
    // Render each name in its own script's face, whatever the active UI language (DEC-10).
    button.getStyleClass().add("lang-name-" + code);
    if (code.equals(L10n.language())) {
      button.getStyleClass().add("menu-plate--primary");
    }
    button.setMaxWidth(Double.MAX_VALUE);
    button.setOnAction(
        event -> {
          popup.hide();
          shell.playSound("click.wav");
          shell.setUiLanguage(code);
        });
    return button;
  }

  /**
   * Shows {@code popup} just above {@code anchor}, right-aligned (used for the corner controls).
   */
  private void showAboveAnchor(Popup popup, Node anchor) {
    Bounds b = anchor.localToScreen(anchor.getBoundsInLocal());
    if (b == null) {
      return;
    }
    popup.show(anchor, b.getMinX(), b.getMinY());
    popup.setX(b.getMaxX() - popup.getWidth());
    popup.setY(b.getMinY() - popup.getHeight() - 10);
  }

  /**
   * Keyboard navigation across the menu (MENU_DESIGN): arrows move focus through the plate stack
   * and corner controls, Enter activates the focused control. Tab traversal and Escape (the shell's
   * step-back chain) work as usual.
   */
  private void installKeyNav(MenuPage page, List<Button> order) {
    page.addEventFilter(
        KeyEvent.KEY_PRESSED,
        event -> {
          KeyCode code = event.getCode();
          if (code == KeyCode.UP || code == KeyCode.LEFT) {
            moveFocus(order, -1);
            event.consume();
          } else if (code == KeyCode.DOWN || code == KeyCode.RIGHT) {
            moveFocus(order, 1);
            event.consume();
          } else if (code == KeyCode.ENTER) {
            Node focusOwner = page.getScene() != null ? page.getScene().getFocusOwner() : null;
            if (focusOwner instanceof Button) {
              ((Button) focusOwner).fire();
              event.consume();
            }
          }
        });
  }

  private void moveFocus(List<Button> order, int delta) {
    if (order.isEmpty()) {
      return;
    }
    int idx = -1;
    for (int i = 0; i < order.size(); i++) {
      if (order.get(i).isFocused()) {
        idx = i;
        break;
      }
    }
    int next =
        idx < 0
            ? (delta > 0 ? 0 : order.size() - 1)
            : (((idx + delta) % order.size()) + order.size()) % order.size();
    order.get(next).requestFocus();
  }

  private void handleMainMenuInput(String input) {
    switch (input) {
      case "1":
        startSinglePlayerFlow();
        break;
      case "2":
        shell.startHostMultiplayer();
        break;
      case "3":
        shell.startJoinMultiplayer();
        break;
      case "4":
        shell.showAddCaseWindow();
        break;
      case "5":
        showTutorials();
        break;
      case "6":
        shell.shutdown();
        break;
      default:
        shell.appendTerminalText(L10n.t("menu.invalid") + "\n");
        break;
    }
  }

  // --- Tutorials ---

  /**
   * The tutorials contents page (MENU_DESIGN #5): a full-window {@link MenuPage} holding a shelf of
   * engraved index cards that wraps to the available width. Each card is a lesson, stamped with a
   * drawn <b>wax-seal ✓</b> once completed (ties to the tutorial progress store). Selection is by
   * click/keyboard; the back plate returns to the main menu.
   */
  public void showTutorials() {
    subState = SubState.TUTORIALS;
    shell.prepareTutorialsMenu();

    MenuPage page = new MenuPage(L10n.t("tutorials.title"), L10n.t("tutorials.subtitle"));

    FlowPane shelf = new FlowPane(18, 18);
    shelf.setAlignment(Pos.CENTER);
    shelf.setColumnHalignment(HPos.CENTER);
    shelf.setMinSize(0, 0);

    List<Button> focusOrder = new ArrayList<>();
    for (String[] tutorial : TUTORIALS) {
      Button card = buildTutorialCard(tutorial);
      focusOrder.add(card);
      shelf.getChildren().add(card);
    }

    StackPane holder = new StackPane(shelf);
    holder.setMinSize(0, 0);
    StackPane.setAlignment(shelf, Pos.CENTER);

    page.setContent(holder);
    page.setBottomStrip(tutorialsBackStrip(focusOrder));
    installKeyNav(page, focusOrder);

    // Page-turn deeper into the tutorials (positive = settles in from the right).
    ui.util.Motion.pageTurn(container, page, 1);
    stopQuoteRotation();
    shell.relayoutScreen();

    if (!focusOrder.isEmpty()) {
      javafx.application.Platform.runLater(focusOrder.get(0)::requestFocus);
    }
  }

  /**
   * One engraved tutorial contents card (MENU_DESIGN #5): the lesson title, plus a drawn wax-seal ✓
   * stamp once the lesson is completed (ties to the tutorial progress store).
   */
  private Button buildTutorialCard(String[] tutorial) {
    String scriptId = tutorial[1];
    boolean completed = shell.isTutorialCompleted(scriptId);

    Button card = new Button(L10n.t(tutorial[0]));
    card.getStyleClass().add("tutorial-card");
    card.setWrapText(true);
    card.setPrefSize(196, 116);
    if (completed) {
      card.getStyleClass().add("tutorial-card--done");
      Canvas seal = new Canvas(28, 28);
      drawTutorialSeal(seal.getGraphicsContext2D(), 28);
      card.setGraphic(seal);
      card.setContentDisplay(javafx.scene.control.ContentDisplay.TOP);
    }
    card.setOnAction(
        event -> {
          shell.playSound("click.wav");
          shell.startTutorial(scriptId);
        });
    return card;
  }

  /** A small oxblood wax seal with an embossed check — the "completed" stamp on a tutorial card. */
  private void drawTutorialSeal(GraphicsContext g, double s) {
    double cx = s / 2;
    double cy = s / 2;
    double r = s * 0.42;
    g.setFill(Palette.OXBLOOD);
    g.fillOval(cx - r, cy - r, r * 2, r * 2);
    g.setStroke(javafx.scene.paint.Color.color(1, 1, 1, 0.8));
    g.setLineWidth(2.4);
    g.strokeLine(cx - r * 0.4, cy, cx - r * 0.05, cy + r * 0.4);
    g.strokeLine(cx - r * 0.05, cy + r * 0.4, cx + r * 0.46, cy - r * 0.4);
  }

  private Node tutorialsBackStrip(List<Button> focusOrder) {
    Button back = plate("tutorials.backToMenu", false, this::showMainMenu);
    back.setMaxWidth(Region.USE_PREF_SIZE);
    focusOrder.add(back);
    HBox strip = new HBox(back);
    strip.setAlignment(Pos.CENTER_LEFT);
    strip.getStyleClass().add("menu-bottom-strip");
    return strip;
  }

  // --- Player profile (display name + preset avatar) ---

  /**
   * The corner profile chip on the main menu (player-profile feature): the chosen avatar thumbnail
   * + display name (or a "set up profile" prompt when none is set). Clicking opens the Profile
   * screen.
   */
  private Button buildProfileChip() {
    ui.settings.PlayerProfile profile = shell.getPlayerProfile();
    Button chip = new Button();
    chip.getStyleClass().add("menu-profile-chip");
    chip.setTooltip(new javafx.scene.control.Tooltip(L10n.t("profile.title")));

    javafx.scene.image.Image avatar = ui.menu.AvatarImages.image(profile.avatarId());
    if (avatar != null) {
      javafx.scene.image.ImageView view = new javafx.scene.image.ImageView(avatar);
      view.setPreserveRatio(true);
      view.setSmooth(true);
      view.setFitWidth(26);
      view.setFitHeight(26);
      StackPane disc = new StackPane(view);
      disc.getStyleClass().add("menu-profile-chip-avatar");
      chip.setGraphic(disc);
      chip.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
    }
    chip.setText(profile.hasDisplayName() ? profile.displayName() : L10n.t("profile.setUp"));
    chip.setOnAction(
        event -> {
          shell.playSound("click.wav");
          showProfile();
        });
    return chip;
  }

  /**
   * The Profile screen (player-profile feature): a full-window {@link MenuPage} to edit the single
   * local player profile — the chosen portrait in its engraved frame, a sunken-well display-name
   * field, and a "Change portrait" button that opens the picker dialog. Save persists the profile
   * and returns to the main menu (refreshing the chip); Back/Escape returns without saving. Laid
   * out on grow constraints; uncluttered, never scrolls.
   */
  public void showProfile() {
    subState = SubState.PROFILE;
    ui.settings.PlayerProfile profile = shell.getPlayerProfile();

    MenuPage page = new MenuPage(L10n.t("profile.title"), L10n.t("profile.subtitle"));

    String[] chosen = {profile.avatarId()};

    // The chosen portrait, shown prominently under the title in its engraved frame. The preview is
    // square and the image is cropped to the portrait region (PORTRAIT_VIEWPORT_FRACTION), so the
    // preset's baked name-plate box never shows and the oval fills the frame with no empty gaps.
    ImageView previewView = new ImageView();
    previewView.setPreserveRatio(true);
    previewView.setSmooth(true);
    // Responsive: the square preview shrinks as the window gets short so the column reflows to fit
    // without a scrollbar or colliding with the strip/frame (gui-profile-layout).
    previewView
        .fitWidthProperty()
        .bind(
            javafx.beans.binding.Bindings.createDoubleBinding(
                () -> ProfileLayout.previewHeight(page.getHeight()), page.heightProperty()));
    previewView
        .fitHeightProperty()
        .bind(
            javafx.beans.binding.Bindings.createDoubleBinding(
                () -> ProfileLayout.previewHeight(page.getHeight()), page.heightProperty()));
    StackPane preview = new StackPane(previewView);
    preview.getStyleClass().add("avatar-preview");
    Runnable updatePreview = () -> showPortrait(previewView, chosen[0]);
    updatePreview.run();

    // Display-name field — a sunken vellum well, capped to the wire display-name length.
    Label nameLabel = new Label(L10n.t("profile.displayName"));
    nameLabel.getStyleClass().add("profile-field-label");
    javafx.scene.control.TextField nameField = new javafx.scene.control.TextField();
    nameField.getStyleClass().add("mp-code-well");
    nameField.setPromptText(L10n.t("profile.namePrompt"));
    nameField.setText(profile.hasDisplayName() ? profile.displayName() : "");
    nameField.setMaxWidth(360);
    nameField.setTextFormatter(
        new javafx.scene.control.TextFormatter<String>(
            change ->
                change.getControlNewText().length() <= common.WireLimits.MAX_DISPLAY_NAME_LENGTH
                    ? change
                    : null));
    VBox nameBox = new VBox(8, nameLabel, nameField);
    nameBox.setAlignment(Pos.CENTER);

    // A single secondary plate opens the "Choose your portrait" picker dialog (the long inline
    // gallery now lives there), keeping the profile screen uncluttered.
    Button changePortrait =
        plate("profile.changePortrait", false, () -> showPortraitPicker(chosen, updatePreview));
    changePortrait.setMaxWidth(Region.USE_PREF_SIZE);

    VBox content = new VBox(preview, nameBox, changePortrait);
    content.setAlignment(Pos.CENTER);
    content.setMinSize(0, 0);
    content.setMaxHeight(Region.USE_PREF_SIZE);
    // Inter-block gaps step down on the 8px scale as the window shrinks (gui-profile-layout).
    content
        .spacingProperty()
        .bind(
            javafx.beans.binding.Bindings.createDoubleBinding(
                () -> ProfileLayout.blockGap(page.getHeight()), page.heightProperty()));

    page.setContent(content);
    page.setBottomStrip(profileSaveStrip(nameField, chosen));

    ui.util.Motion.pageTurn(container, page, 1);
    stopQuoteRotation();
    shell.relayoutScreen();
    javafx.application.Platform.runLater(nameField::requestFocus);
  }

  /**
   * Sets {@code avatarId}'s portrait on {@code view}, cropped to the portrait region so the
   * preset's baked name-plate box is never shown. A missing portrait clears the view.
   */
  private static void showPortrait(ImageView view, String avatarId) {
    Image image = AvatarImages.image(avatarId);
    view.setImage(image);
    view.setViewport(portraitViewport(image));
  }

  /** The top portrait region of a preset image (drops the baked name-plate); null if no image. */
  private static Rectangle2D portraitViewport(Image image) {
    if (image == null) {
      return null;
    }
    return new Rectangle2D(0, 0, image.getWidth(), image.getHeight() * PORTRAIT_VIEWPORT_FRACTION);
  }

  /**
   * The "Choose your portrait" picker (gui-profile-portrait-picker): a modal dossier over a warm
   * {@code -sl-scrim} veil (the shared {@code .pause-scrim} + {@code .menu-popover} chrome). The
   * presets are a wrapping grid of engraved {@code .avatar-chip}s with the current pick
   * pre-selected (petrol {@code --selected} ring); the grid scrolls vertically inside the dialog
   * only. "Choose" commits the pending pick to the preview + {@code chosen[0]} and closes; Cancel /
   * Escape / veil-click discard. Full keyboard nav: arrows move the highlight, Enter = Choose,
   * Escape = Cancel.
   */
  private void showPortraitPicker(String[] chosen, Runnable onChosen) {
    String[] pending = {chosen[0]};

    Region scrim = new Region();
    scrim.getStyleClass().add("pause-scrim");
    scrim.setOnMouseClicked(event -> dismissPortraitPicker());

    Label title = new Label(L10n.t("profile.chooseAvatar"));
    title.getStyleClass().add("menu-popover-title");

    FlowPane grid = new FlowPane(16, 16);
    grid.setAlignment(Pos.CENTER);
    grid.setColumnHalignment(HPos.CENTER);
    grid.setPrefWrapLength(520);
    grid.setMaxWidth(520);

    List<Button> chips = new ArrayList<>();
    Map<String, Button> byId = new LinkedHashMap<>();
    for (String id : common.PlayerAvatars.IDS) {
      // Skip any preset whose PNG has been removed from the bundle, so the gallery never shows an
      // empty slot as a choice (the id stays valid on the wire for existing saved profiles).
      if (!AvatarImages.exists(id)) {
        continue;
      }
      Button chip = buildPickerChip(id);
      chip.setOnAction(
          event -> {
            shell.playSound("click.wav");
            applySelection(chips, byId, pending, id);
          });
      chips.add(chip);
      byId.put(id, chip);
      grid.getChildren().add(chip);
    }
    applySelection(chips, byId, pending, pending[0]); // pre-select the current avatar

    // The base .scroll-pane rule already makes the viewport transparent (the vellum card shows
    // through in both themes — no light patch); we only cap the height so overflow scrolls
    // in-dialog.
    ScrollPane scroller = new ScrollPane(grid);
    scroller.setFitToWidth(true);
    scroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scroller.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    scroller.setMaxHeight(360); // overflow scrolls vertically inside the dialog only
    scroller.setMinViewportWidth(536);

    Runnable commit =
        () -> {
          chosen[0] = pending[0];
          if (onChosen != null) {
            onChosen.run();
          }
          dismissPortraitPicker();
        };
    Button choose = plate("profile.confirmPortrait", true, commit);
    choose.setMaxWidth(Region.USE_PREF_SIZE);
    Button cancel = plate("common.cancel", false, this::dismissPortraitPicker);
    cancel.setMaxWidth(Region.USE_PREF_SIZE);
    HBox actions = new HBox(12, cancel, choose);
    actions.setAlignment(Pos.CENTER_RIGHT);

    VBox card = new VBox(14, title, scroller, actions);
    card.getStyleClass().add("menu-popover");
    card.setAlignment(Pos.CENTER);
    card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

    StackPane overlay = new StackPane(scrim, card);
    StackPane.setAlignment(card, Pos.CENTER);
    overlay.setViewOrder(-100);
    overlay.addEventFilter(
        KeyEvent.KEY_PRESSED,
        event -> {
          switch (event.getCode()) {
            case ESCAPE:
              dismissPortraitPicker();
              event.consume();
              break;
            case ENTER:
              shell.playSound("click.wav");
              commit.run();
              event.consume();
              break;
            case LEFT:
            case UP:
              moveHighlight(byId, pending, -1, scroller);
              event.consume();
              break;
            case RIGHT:
            case DOWN:
              moveHighlight(byId, pending, 1, scroller);
              event.consume();
              break;
            default:
              break;
          }
        });

    portraitPickerOverlay = overlay;
    container.getChildren().add(overlay);
    ui.util.Motion.fadeIn(overlay, ui.util.Motion.SCREEN).play();
    javafx.application.Platform.runLater(
        () -> {
          Button current = byId.get(pending[0]);
          (current != null ? current : choose).requestFocus();
        });
  }

  /** One engraved, cropped portrait chip for the picker grid (square, hand cursor). */
  private Button buildPickerChip(String avatarId) {
    Button chip = new Button();
    chip.getStyleClass().add("avatar-chip");
    chip.setCursor(Cursor.HAND);
    Image image = AvatarImages.image(avatarId);
    if (image != null) {
      ImageView view = new ImageView(image);
      view.setPreserveRatio(true);
      view.setSmooth(true);
      view.setViewport(portraitViewport(image));
      view.setFitWidth(PICKER_PORTRAIT);
      view.setFitHeight(PICKER_PORTRAIT);
      chip.setGraphic(view);
    }
    return chip;
  }

  /** Makes {@code id} the pending pick and moves the petrol selection ring onto its chip. */
  private void applySelection(
      List<Button> chips, Map<String, Button> byId, String[] pending, String id) {
    pending[0] = id;
    for (Button chip : chips) {
      chip.getStyleClass().remove("avatar-chip--selected");
    }
    Button selected = byId.get(id);
    if (selected != null) {
      selected.getStyleClass().add("avatar-chip--selected");
    }
  }

  /**
   * Arrow-key highlight: step the pending pick by {@code delta} (wrapping), focus + scroll to it.
   */
  private void moveHighlight(
      Map<String, Button> byId, String[] pending, int delta, ScrollPane scroller) {
    List<String> ids = new ArrayList<>(byId.keySet());
    if (ids.isEmpty()) {
      return;
    }
    int idx = Math.max(0, ids.indexOf(pending[0]));
    int next = ((idx + delta) % ids.size() + ids.size()) % ids.size();
    String nextId = ids.get(next);
    applySelection(new ArrayList<>(byId.values()), byId, pending, nextId);
    Button chip = byId.get(nextId);
    if (chip != null) {
      chip.requestFocus();
      ensureVisible(scroller, chip);
    }
  }

  /**
   * Scrolls {@code pane} just enough that {@code node} (a direct child of its content) is visible.
   */
  private static void ensureVisible(ScrollPane pane, Node node) {
    double contentH = pane.getContent().getBoundsInLocal().getHeight();
    double viewH = pane.getViewportBounds().getHeight();
    if (contentH <= viewH) {
      return; // nothing to scroll
    }
    double range = contentH - viewH;
    double top = pane.getVvalue() * range;
    double nodeMinY = node.getBoundsInParent().getMinY();
    double nodeMaxY = node.getBoundsInParent().getMaxY();
    if (nodeMinY < top) {
      pane.setVvalue(nodeMinY / range);
    } else if (nodeMaxY > top + viewH) {
      pane.setVvalue((nodeMaxY - viewH) / range);
    }
  }

  /** Closes the portrait picker modal if it is open (Choose, Cancel, Escape, or veil-click). */
  private void dismissPortraitPicker() {
    if (portraitPickerOverlay != null) {
      container.getChildren().remove(portraitPickerOverlay);
      portraitPickerOverlay = null;
    }
  }

  /** Save (persist the profile + return to the menu) and Back (discard) for the Profile screen. */
  private Node profileSaveStrip(javafx.scene.control.TextField nameField, String[] chosen) {
    Button save =
        plate(
            "profile.save",
            true,
            () -> {
              shell.savePlayerProfile(
                  new ui.settings.PlayerProfile(nameField.getText().trim(), chosen[0]));
              showMainMenu();
            });
    save.setMaxWidth(Region.USE_PREF_SIZE);
    Button back = plate("profile.back", false, this::showMainMenu);
    back.setMaxWidth(Region.USE_PREF_SIZE);

    HBox strip = new HBox(12, back, save);
    strip.setAlignment(Pos.CENTER_LEFT);
    strip.getStyleClass().add("menu-bottom-strip");
    return strip;
  }

  // --- Single-player Case selection (engraved casebook gallery) ---

  private void startSinglePlayerFlow() {
    shell.startSinglePlayerSession();
    showCaseSelection();
  }

  /**
   * The case-selection centrepiece (MENU_DESIGN #2): the shared {@link ui.menu.CaseSelectionView}
   * (an engraved casebook-cover gallery + invitation dossier) over the local single-player case
   * list. On confirm it starts the offline, in-process case; the multiplayer host mounts the same
   * component. Also the re-entry point when the engine emits ReturnToCaseSelectionDTO.
   */
  public void showCaseSelection() {
    subState = SubState.CHOOSING_CASE;
    SinglePlayerMain game = shell.getSinglePlayerGame();
    if (game == null) {
      game = shell.startSinglePlayerSession();
    }
    List<JsonDTO.CaseFile> cases = game.getAvailableCases();

    caseSelectionView =
        new ui.menu.CaseSelectionView.Builder()
            .cases(cases)
            .title("caseSelect.title", "caseSelect.subtitle")
            .primaryLabel("invitation.begin")
            .galleryBackLabel("caseSelect.backToMenu")
            .solved(shell::isCaseSolved)
            .bestRank(t -> shell.bestRankLabel(t, cases).orElse(null))
            .onReview(shell::openReview)
            .playSound(shell::playSound)
            .onBack(this::showMainMenu)
            .onAddCase(
                shell::showAddCaseWindow) // "file a new case" — a single-player/local affordance
            .onConfirm(shell::beginSinglePlayerCase) // start the offline case, unchanged
            .build();

    // Page-turn deeper into case selection (positive = settles in from the right).
    ui.util.Motion.pageTurn(container, caseSelectionView, 1);
    stopQuoteRotation();
    shell.relayoutScreen();
  }

  /**
   * Rebuilds the case-selection gallery in place if it is the active screen — e.g. right after a
   * case is imported through the Add-a-case window, so the newly copied case appears immediately. A
   * no-op when the player is elsewhere.
   */
  public void refreshCaseSelectionIfChoosing() {
    if (subState == SubState.CHOOSING_CASE) {
      showCaseSelection();
    }
  }
}

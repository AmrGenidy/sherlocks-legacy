package ui.menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import ui.i18n.L10n;
import ui.util.Palette;

/**
 * The shared case-selection screen (MENU_DESIGN #2): an engraved {@link CasebookCover} gallery that
 * wraps to the available width, opening into a letter/dossier invitation page with a per-case
 * language pick and a primary "confirm" action. <b>One component, two callers</b> — single player
 * and the multiplayer host mount the exact same screen; only the title, the primary label and the
 * confirm action differ.
 *
 * <p>It is presentation-only and <b>list-agnostic</b>: the case list is passed in (the host gets it
 * from the server, single player reads it locally), and the chosen case + language are handed back
 * through {@link Builder#onConfirm(BiConsumer)}. Gallery↔dossier navigation is internal; {@link
 * #handleEscape()} steps the dossier back to the gallery, and the gallery back to {@link
 * Builder#onBack(Runnable)}. The view swaps a {@link MenuPage} into itself, so a host controller
 * just mounts the view and forwards Escape / language changes.
 */
public class CaseSelectionView extends StackPane {

  private final List<JsonDTO.CaseFile> cases;
  private final String titleKey;
  private final String subtitleKey;
  private final String primaryKey;
  private final String galleryBackKey;
  private final Predicate<String> solved;
  private final Function<String, String> bestRank; // best Rank Tier name for the seal, or null
  // Enter the solved case as a Review Session, in the resolved language (gui-review-enter-case).
  private final BiConsumer<JsonDTO.CaseFile, String> onReview;
  private final Consumer<String> playSound;
  private final Runnable onBack;
  private final Runnable onAddCase; // nullable — no tile when absent
  private final BiConsumer<JsonDTO.CaseFile, String> onConfirm;

  private boolean onInvitation;
  private JsonDTO.CaseFile selectedCaseFile;
  private String chosenLangCode;

  // Language tabs on the shelf (gui-g5b). activeTabLang is the selected tab, preserved across
  // rerender() (only re-defaulted when unset/invalid). dismissedSuggest remembers which viewed-tab
  // languages the player waved off, so the cross-language banner stays stable and never nags.
  private String activeTabLang;
  private final java.util.Set<String> dismissedSuggest = new java.util.HashSet<>();

  private CaseSelectionView(Builder b) {
    this.cases = b.cases != null ? b.cases : new ArrayList<>();
    this.titleKey = b.titleKey;
    this.subtitleKey = b.subtitleKey;
    this.primaryKey = b.primaryKey;
    this.galleryBackKey = b.galleryBackKey;
    this.solved = b.solved != null ? b.solved : t -> false;
    this.bestRank = b.bestRank != null ? b.bestRank : t -> null;
    this.onReview = b.onReview != null ? b.onReview : (c, l) -> {};
    this.playSound = b.playSound != null ? b.playSound : s -> {};
    this.onBack = b.onBack != null ? b.onBack : () -> {};
    this.onAddCase = b.onAddCase;
    this.onConfirm = b.onConfirm != null ? b.onConfirm : (c, l) -> {};
    setMinSize(0, 0);
    showGallery();
  }

  /** Fluent builder — the confirm action and labels are caller-supplied (nothing is hardcoded). */
  public static final class Builder {
    private List<JsonDTO.CaseFile> cases;
    private String titleKey = "caseSelect.title";
    private String subtitleKey = "caseSelect.subtitle";
    private String primaryKey = "invitation.begin";
    private String galleryBackKey = "caseSelect.backToMenu";
    private Predicate<String> solved;
    private Function<String, String> bestRank;
    private BiConsumer<JsonDTO.CaseFile, String> onReview;
    private Consumer<String> playSound;
    private Runnable onBack;
    private Runnable onAddCase;
    private BiConsumer<JsonDTO.CaseFile, String> onConfirm;

    public Builder cases(List<JsonDTO.CaseFile> cases) {
      this.cases = cases;
      return this;
    }

    public Builder title(String titleKey, String subtitleKey) {
      this.titleKey = titleKey;
      this.subtitleKey = subtitleKey;
      return this;
    }

    public Builder primaryLabel(String primaryKey) {
      this.primaryKey = primaryKey;
      return this;
    }

    public Builder galleryBackLabel(String galleryBackKey) {
      this.galleryBackKey = galleryBackKey;
      return this;
    }

    public Builder solved(Predicate<String> solved) {
      this.solved = solved;
      return this;
    }

    /**
     * Best Rank Tier name for a solved case's seal — returns {@code null} when none (e.g.
     * migrated).
     */
    public Builder bestRank(Function<String, String> bestRank) {
      this.bestRank = bestRank;
      return this;
    }

    /** Enters a Review Session for a solved case (case + resolved language). */
    public Builder onReview(BiConsumer<JsonDTO.CaseFile, String> onReview) {
      this.onReview = onReview;
      return this;
    }

    public Builder playSound(Consumer<String> playSound) {
      this.playSound = playSound;
      return this;
    }

    public Builder onBack(Runnable onBack) {
      this.onBack = onBack;
      return this;
    }

    public Builder onAddCase(Runnable onAddCase) {
      this.onAddCase = onAddCase;
      return this;
    }

    public Builder onConfirm(BiConsumer<JsonDTO.CaseFile, String> onConfirm) {
      this.onConfirm = onConfirm;
      return this;
    }

    public CaseSelectionView build() {
      return new CaseSelectionView(this);
    }
  }

  /**
   * Steps back one level: the dossier returns to the gallery (internal); the gallery fires the
   * caller's {@code onBack}. Always handled here, so a host's Escape just delegates to this.
   */
  public boolean handleEscape() {
    if (getChildren().size() > 1) {
      dismissOverlay(); // close the solved-choice dialog first
    } else if (onInvitation) {
      showGallery();
    } else {
      onBack.run();
    }
    return true;
  }

  /**
   * Rebuilds the current sub-view in the active UI language (call from {@code onLanguageChanged}).
   */
  public void rerender() {
    if (onInvitation && selectedCaseFile != null) {
      showInvitation(selectedCaseFile);
    } else {
      showGallery();
    }
  }

  // ====================== Gallery ======================

  private void showGallery() {
    onInvitation = false;

    // Build the language tabs from the union of every case's languages (gui-g5b). Keep the selected
    // tab across rerender; only (re-)default it when it's unset or its language no longer has
    // cases.
    List<String> tabs = LanguageTabs.available(cases, CaseSelectionView::langCodesOf);
    if (activeTabLang == null || !tabs.contains(activeTabLang)) {
      activeTabLang = LanguageTabs.defaultTab(tabs, L10n.language());
    }
    List<JsonDTO.CaseFile> shown =
        activeTabLang == null
            ? cases
            : LanguageTabs.itemsIn(cases, activeTabLang, CaseSelectionView::langCodesOf);

    MenuPage page = new MenuPage(L10n.t(titleKey), L10n.t(subtitleKey));

    List<Button> focusOrder = new ArrayList<>();
    VBox content = new VBox(16);
    content.setAlignment(Pos.TOP_CENTER);
    content.setMinSize(0, 0);

    // Tabs only when there's a real choice (a single-language shelf needs no filter).
    if (tabs.size() >= 2) {
      content.getChildren().add(buildLanguageTabRow(tabs, focusOrder));
    }

    // Cross-language suggestion: a calm, dismissible offer shown only on a true UI≠tab mismatch.
    if (LanguageTabs.shouldSuggestSwitch(activeTabLang, L10n.language())
        && !dismissedSuggest.contains(activeTabLang)) {
      content.getChildren().add(buildLanguageSuggestion(activeTabLang, focusOrder));
    }

    // Friendly empty state: with no cases to show, the shelf still opens (never a blank/dead
    // screen)
    // with a calm line pointing at the always-present "Add a case" tile below.
    if (shown.isEmpty()) {
      Label empty = new Label(L10n.t("caseSelect.empty"));
      empty.getStyleClass().add("menu-subtitle");
      empty.setWrapText(true);
      empty.setTextAlignment(TextAlignment.CENTER);
      empty.setMaxWidth(560);
      content.getChildren().add(empty);
    }

    FlowPane shelf = new FlowPane(24, 24);
    shelf.setAlignment(Pos.CENTER);
    shelf.setColumnHalignment(HPos.CENTER);
    shelf.setMinSize(0, 0);
    for (JsonDTO.CaseFile caseFile : shown) {
      CasebookCover cover = buildCover(caseFile);
      focusOrder.add(cover);
      shelf.getChildren().add(cover);
    }
    if (onAddCase != null) {
      Button addCase = buildAddCaseTile();
      focusOrder.add(addCase);
      shelf.getChildren().add(addCase);
    }

    // The shelf scrolls vertically once there are more covers than fit on screen: the FlowPane wraps
    // its fixed-size covers to the viewport width (fitToWidth), and any extra rows scroll instead of
    // overflowing the page or cramming the covers together (gui-case-shelf-scroll). Transparent
    // (.scroll-pane is themed transparent) so the parchment shows through; horizontal bar is never
    // needed because the covers wrap. A little padding keeps the outer covers off the frame edge.
    shelf.setPadding(new Insets(6, 10, 14, 10));
    ScrollPane shelfScroll = new ScrollPane(shelf);
    shelfScroll.setFitToWidth(true);
    shelfScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    shelfScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    shelfScroll.setMinSize(0, 0);
    shelfScroll.setPannable(true);
    VBox.setVgrow(shelfScroll, Priority.ALWAYS);
    content.getChildren().add(shelfScroll);

    page.setContent(content);
    page.setBottomStrip(buildBackStrip(focusOrder, onBack, galleryBackKey));
    installKeyNav(page, focusOrder);

    getChildren().setAll(page);

    if (!focusOrder.isEmpty()) {
      Button first = focusOrder.get(0);
      Platform.runLater(first::requestFocus);
    }
  }

  /**
   * The engraved language-tab row: one {@code .menu-plate} chip per available language (gui-g5b).
   */
  private Node buildLanguageTabRow(List<String> tabs, List<Button> focusOrder) {
    FlowPane tabRow = new FlowPane(10, 10);
    tabRow.setAlignment(Pos.CENTER);
    tabRow.getStyleClass().add("lang-tab-row");
    for (String code : tabs) {
      Button chip = new Button(tabDisplayName(code));
      // Reuse the invitation's language-plate treatment: engraved plate + per-script face; the
      // active tab takes the primary (petrol) fill.
      chip.getStyleClass().addAll("menu-plate", "lang-name-" + code);
      if (code.equals(activeTabLang)) {
        chip.getStyleClass().add("menu-plate--primary");
      }
      chip.setOnAction(
          event -> {
            playSound.accept("click.wav");
            activeTabLang = code;
            showGallery();
          });
      focusOrder.add(chip);
      tabRow.getChildren().add(chip);
    }
    return tabRow;
  }

  /**
   * The in-tab cross-language suggestion banner (gui-g5b): a calm vellum offer to switch the whole
   * interface to the viewed tab's language. Never forced — a quiet "Keep" dismisses it for that
   * language so it stays stable and never nags.
   */
  private Node buildLanguageSuggestion(String viewedLang, List<Button> focusOrder) {
    String viewedName = tabDisplayName(viewedLang);
    String currentName = L10n.endonym(L10n.language());

    // The body names the viewed language INSIDE the active-UI sentence. One font can't cover both
    // scripts (e.g. Cyrillic "Русский" in an English/typewriter line fell back glyph-by-glyph), so
    // it
    // is built from spans: the sentence keeps the active face, the embedded NAME carries its own
    // .lang-name-<code> (gui-crosslang-banner-font).
    TextFlow text = bannerSpans(L10n.t("caseSelect.langSuggest.body"), viewedLang, viewedName);
    text.getStyleClass().add("lang-suggest-text");
    text.setMaxWidth(Double.MAX_VALUE);
    text.setTextAlignment(TextAlignment.CENTER);

    // The two plates sit as a tidy, equal-height horizontal pair (8px-scale gap, centred). They hug
    // their content (single-line HBox label graphics), so the banner card hugs the title + button
    // row instead of stretching the plates vertically (.scratch/gui-crosslang-banner-font
    // follow-up).
    HBox actions = new HBox(10);
    actions.setAlignment(Pos.CENTER);

    // Only offer the switch for a language the interface can actually load (en/ar/ru).
    if (L10n.isUiLanguage(viewedLang)) {
      Button switchBtn =
          spanPlate(L10n.t("caseSelect.langSuggest.switch"), viewedLang, viewedName, true);
      switchBtn.setOnAction(
          event -> {
            playSound.accept("click.wav");
            L10n.setLanguage(viewedLang); // live switch → listeners re-render in the new script
          });
      focusOrder.add(switchBtn);
      actions.getChildren().add(switchBtn);
    }

    Button keepBtn =
        spanPlate(L10n.t("caseSelect.langSuggest.keep"), L10n.language(), currentName, false);
    keepBtn.setOnAction(
        event -> {
          playSound.accept("click.wav");
          dismissedSuggest.add(viewedLang);
          showGallery();
        });
    focusOrder.add(keepBtn);
    actions.getChildren().add(keepBtn);

    // Compact: the banner is a passing offer, not a page — tight gaps and a narrower cap so it
    // doesn't eat the shelf's vertical space.
    VBox banner = new VBox(6, text, actions);
    banner.getStyleClass().add("lang-suggest-banner");
    banner.setAlignment(Pos.CENTER);
    banner.setMaxWidth(560);
    banner.setMaxHeight(Region.USE_PREF_SIZE); // the card hugs its content, never stretches
    return banner;
  }

  /**
   * An engraved {@code menu-plate} whose label is a single-line {@link HBox} of {@link
   * #bannerSpanRuns} Text spans, so the embedded language NAME renders in its own script while the
   * sentence keeps the active-UI plate font, the full label always shows (a graphic doesn't
   * truncate), and the plate hugs its content height. A {@code TextFlow} graphic is deliberately
   * NOT used here: as a button graphic it has no laid-out width, so its width-dependent preferred
   * height blows up and stretches the plate vertically (.scratch/gui-crosslang-banner-font). The
   * HBox is a fixed single row.
   */
  static Button spanPlate(String template, String nameLang, String nameText, boolean primary) {
    HBox label = new HBox();
    label.setAlignment(Pos.CENTER);
    label.getChildren().addAll(bannerSpanRuns(template, nameLang, nameText));

    Button button = new Button();
    button.setGraphic(label);
    button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    button.setMaxHeight(Region.USE_PREF_SIZE); // hug content height — never stretch vertically
    button.getStyleClass().add("menu-plate");
    if (primary) {
      button.getStyleClass().add("menu-plate--primary");
    }
    return button;
  }

  /**
   * Splits a localized {@code template} on its {@code {0}} placeholders into a {@link TextFlow}
   * (which wraps to width — used for the banner BODY line). The literal segments are {@code
   * .banner-span} sentence runs (they inherit the active-UI face of their container), and each
   * {@code {0}} becomes a {@code nameText} run carrying {@code .lang-name-<nameLang>} so it renders
   * in that language's own script face, identical regardless of the active UI language
   * (.scratch/gui-crosslang-banner-font). No glyph-by-glyph fallback.
   */
  static TextFlow bannerSpans(String template, String nameLang, String nameText) {
    TextFlow flow = new TextFlow();
    flow.getChildren().addAll(bannerSpanRuns(template, nameLang, nameText));
    return flow;
  }

  /**
   * The per-script Text runs for a {@code {0}}-templated string: literal segments are {@code
   * .banner-span} sentence runs; each {@code {0}} is a {@code nameText} run tagged {@code
   * .lang-name-<nameLang>} so it renders in its own script. The container (TextFlow for the
   * wrapping body, HBox for a single-line plate label) is chosen by the caller.
   */
  private static List<Text> bannerSpanRuns(String template, String nameLang, String nameText) {
    List<Text> runs = new ArrayList<>();
    String[] segments = template.split("\\{0\\}", -1);
    for (int i = 0; i < segments.length; i++) {
      if (!segments[i].isEmpty()) {
        Text sentence = new Text(segments[i]);
        sentence.getStyleClass().add("banner-span");
        runs.add(sentence);
      }
      if (i < segments.length - 1) {
        Text name = new Text(nameText);
        name.getStyleClass().addAll("banner-span", "lang-name-" + nameLang);
        runs.add(name);
      }
    }
    return runs;
  }

  /**
   * Languages a case provides (its localization keys), or empty — the tab/cover language source.
   */
  private static java.util.Collection<String> langCodesOf(JsonDTO.CaseFile caseFile) {
    return caseFile.getLocalizations() == null
        ? java.util.List.of()
        : caseFile.getLocalizations().keySet();
  }

  /** A language tab's label: the case-authored name in its own script, else the endonym. */
  private String tabDisplayName(String code) {
    for (JsonDTO.CaseFile caseFile : cases) {
      if (caseFile.getLocalizations() != null && caseFile.getLocalizations().containsKey(code)) {
        String name = caseFile.getLocalizations().get(code).getLanguageName();
        if (name != null && !name.isBlank()) {
          return name;
        }
      }
    }
    return L10n.endonym(code);
  }

  /** Builds one engraved casebook cover for {@code caseFile}, wired to open its invitation. */
  private CasebookCover buildCover(JsonDTO.CaseFile caseFile) {
    String title = ui.i18n.CaseTitles.displayTitle(caseFile);
    String author = caseFile.getMetadata() != null ? caseFile.getMetadata().getAuthor() : null;
    boolean isSolved = solved.test(caseFile.getUniversalTitle());
    String rank = isSolved ? bestRank.apply(caseFile.getUniversalTitle()) : null;
    CasebookCover cover =
        new CasebookCover(
            title, author, sortedLanguageCodes(caseFile), isSolved, rank, excerptOf(caseFile));
    // The cover grows with the Reading text size so its em-sized title/author/tags keep the same fit
    // at any font size instead of being clipped — the whole book scales by the same factor as the
    // text (gui-casebook-scale). The shelf is a wrapping, scrolling FlowPane, so larger covers just
    // reflow.
    double s = ui.util.ContentScale.snap(ui.util.ContentScaleStyling.getActiveReadingScale());
    cover.setPrefSize(180 * s, 234 * s);
    cover.setOnAction(
        event -> {
          playSound.accept("pageflip.mp3");
          if (isSolved) {
            // A solved case offers Review (read-only) or Play again (a fresh attempt) first.
            showSolvedChoice(caseFile);
          } else {
            chosenLangCode = null; // pick a fresh default for the newly opened case
            showInvitation(caseFile);
          }
        });
    return cover;
  }

  /**
   * The Review / Play-again dialog for a solved case (docs/SAVE_AND_PROFILE.md): a centred dossier
   * card over a warm, dimmed veil (the shared pause-menu chrome). "Review investigation" opens the
   * read-only viewer; "Play again" starts a fresh attempt through the normal invitation flow.
   */
  private void showSolvedChoice(JsonDTO.CaseFile caseFile) {
    Region scrim = new Region();
    scrim.getStyleClass().add("pause-scrim");

    Label title = new Label(ui.i18n.CaseTitles.displayTitle(caseFile));
    title.getStyleClass().add("pause-title");
    title.setWrapText(true);
    title.setTextAlignment(TextAlignment.CENTER);

    Label subtitle = new Label(L10n.t("review.solvedSubtitle"));
    subtitle.getStyleClass().add("menu-subtitle");
    subtitle.setWrapText(true);
    subtitle.setTextAlignment(TextAlignment.CENTER);

    Button review =
        plate(
            "review.investigation",
            true,
            () -> {
              dismissOverlay();
              // Enter the Review Session in the chosen language, or the case's preferred default.
              String lang =
                  (chosenLangCode != null
                          && caseFile.getLocalizations() != null
                          && caseFile.getLocalizations().containsKey(chosenLangCode))
                      ? chosenLangCode
                      : preferredLangCode(caseFile);
              onReview.accept(caseFile, lang);
            });
    review.setMaxWidth(Double.MAX_VALUE);
    Button playAgain =
        plate(
            "review.playAgain",
            false,
            () -> {
              dismissOverlay();
              chosenLangCode = null; // pick a fresh default for the replay
              showInvitation(caseFile);
            });
    playAgain.setMaxWidth(Double.MAX_VALUE);
    Button cancel = plate("common.cancel", false, this::dismissOverlay);
    cancel.setMaxWidth(Double.MAX_VALUE);

    VBox card = new VBox(14, title, subtitle, review, playAgain, cancel);
    card.getStyleClass().add("pause-card");
    card.setAlignment(Pos.CENTER);
    card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

    StackPane overlay = new StackPane(scrim, card);
    StackPane.setAlignment(card, Pos.CENTER);
    overlay.setViewOrder(-100);
    scrim.setOnMouseClicked(event -> dismissOverlay()); // clicking the veil cancels

    getChildren().add(overlay);
    ui.util.Motion.fadeIn(overlay, ui.util.Motion.SCREEN).play();
    Platform.runLater(review::requestFocus);
  }

  /** Removes the topmost transient overlay (the solved-choice dialog) if present. */
  private void dismissOverlay() {
    if (getChildren().size() > 1) {
      getChildren().remove(getChildren().size() - 1);
    }
  }

  /** The dashed "Add a case" affordance closing the shelf (MENU_DESIGN #2 / #8). */
  private Button buildAddCaseTile() {
    Canvas canvas = new Canvas();
    canvas.setMouseTransparent(true);

    Label label = new Label(L10n.t("caseSelect.addCase"));
    label.getStyleClass().add("casebook-add-label");
    label.setWrapText(true);
    label.setTextAlignment(TextAlignment.CENTER);
    StackPane.setAlignment(label, Pos.CENTER);

    StackPane face = new StackPane(canvas, label);
    face.setMinSize(0, 0);
    canvas.widthProperty().bind(face.widthProperty());
    canvas.heightProperty().bind(face.heightProperty());
    canvas.widthProperty().addListener((obs, a, b) -> drawAddTile(canvas));
    canvas.heightProperty().addListener((obs, a, b) -> drawAddTile(canvas));

    Button button = new Button();
    button.getStyleClass().addAll("casebook-cover", "casebook-add");
    button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    button.setPadding(Insets.EMPTY);
    button.setGraphic(face);
    // The graphic fills the whole plate (a Button otherwise centres it at its own pref size).
    face.prefWidthProperty().bind(button.widthProperty());
    face.prefHeightProperty().bind(button.heightProperty());
    // Match the casebook covers so the tile lines up at any Reading text size.
    double s = ui.util.ContentScale.snap(ui.util.ContentScaleStyling.getActiveReadingScale());
    button.setPrefSize(180 * s, 234 * s);
    button.setOnAction(
        event -> {
          playSound.accept("click.wav");
          onAddCase.run();
        });
    return button;
  }

  /** A dashed engraved frame with a quill-plus motif behind the "Add a case" label. */
  private void drawAddTile(Canvas canvas) {
    double w = canvas.getWidth();
    double h = canvas.getHeight();
    GraphicsContext g = canvas.getGraphicsContext2D();
    g.clearRect(0, 0, w, h);
    if (w <= 8 || h <= 8) {
      return;
    }
    double pad = Math.max(2, Math.min(w, h) * 0.04);
    g.setStroke(Palette.SEPIA);
    g.setLineWidth(Math.max(1.5, Math.min(w, h) * 0.01));
    g.setLineDashes(8, 7);
    g.strokeRect(pad, pad, w - pad * 2, h - pad * 2);
    g.setLineDashes(null);

    // A plus in the upper third, ochre — "file a new case".
    double cx = w / 2;
    double cy = h * 0.36;
    double arm = Math.min(w, h) * 0.12;
    g.setStroke(Palette.OCHRE);
    g.setLineWidth(Math.max(2, Math.min(w, h) * 0.018));
    g.strokeLine(cx - arm, cy, cx + arm, cy);
    g.strokeLine(cx, cy - arm, cx, cy + arm);
  }

  // ====================== Invitation dossier ======================

  /**
   * Builds the invitation as a written letter: a salutation line, the body, and a trailing (bottom,
   * reading-end-aligned) signature. Case invitations are authored as {@code Salutation — body —
   * Signature} (em-dash delimited); when that shape isn't present the whole text falls back to a
   * single justified paragraph, so an oddly-formatted invitation never breaks.
   */
  /**
   * The per-script face class for the language the letter is written in. The {@code .lang-<code>}
   * faces are keyed on the ACTIVE UI language, but a case can be read in a different language than
   * the interface (English UI, Russian case). Tagging the letter with the bare {@code
   * .lang-name-<code>} face — the same seam the cross-language banner uses — makes it render in a
   * font that covers its own script, instead of the typewriter face falling back glyph-by-glyph
   * (which is what produced the wide, gap-ridden Cyrillic).
   */
  private String letterScriptClass() {
    String code = chosenLangCode == null || chosenLangCode.isBlank() ? L10n.language() : chosenLangCode;
    return "lang-name-" + code;
  }

  private Node buildLetterContent(String invitation) {
    String text = invitation == null ? "" : invitation.trim();
    int first = text.indexOf('—'); // em dash (U+2014)
    int last = text.lastIndexOf('—');
    String greeting = first >= 0 ? text.substring(0, first).trim() : "";
    String signature = (last > first) ? text.substring(last + 1).trim() : "";
    String body = (first >= 0 && last > first) ? text.substring(first + 1, last).trim() : text;

    // Only treat it as a letter when the salutation + signature are short, real, and distinct;
    // otherwise show the plain paragraph.
    boolean isLetter =
        first >= 0
            && last > first
            && !greeting.isEmpty()
            && greeting.length() <= 40
            && !signature.isEmpty()
            && signature.length() <= 60;

    String script = letterScriptClass(); // face for the language the LETTER is written in

    if (!isLetter) {
      Label plain = new Label(text);
      plain.getStyleClass().addAll("case-letter", script);
      plain.setWrapText(true);
      plain.setMaxWidth(Double.MAX_VALUE);
      return plain;
    }

    Label salutation = new Label(greeting + " —");
    salutation.getStyleClass().addAll("case-letter", "case-letter-salutation", script);
    salutation.setWrapText(true);
    salutation.setMaxWidth(Double.MAX_VALUE);

    Label bodyLabel = new Label(body);
    bodyLabel.getStyleClass().addAll("case-letter", script);
    bodyLabel.setWrapText(true);
    bodyLabel.setMaxWidth(Double.MAX_VALUE);

    Label sig = new Label("— " + signature);
    sig.getStyleClass().addAll("case-letter", "case-letter-signature", script);
    sig.setWrapText(true);
    // A leading spacer pushes the signature to the reading-END edge (right in LTR, left in RTL, so
    // Arabic/Hebrew sign on their own side).
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox sigRow = new HBox(spacer, sig);

    VBox box = new VBox(salutation, bodyLabel, sigRow);
    box.getStyleClass().add("case-letter-page");
    box.setMaxWidth(Double.MAX_VALUE);
    VBox.setMargin(bodyLabel, new Insets(14, 0, 18, 0));
    return box;
  }

  /**
   * The case invitation as a letter/dossier page (MENU_DESIGN #2): the invitation letter in the
   * typewriter face, a per-case language pick, and the primary confirm action. Picking a language
   * re-renders the letter in that language; confirm hands {@code (caseFile, langCode)} to the
   * caller.
   */
  private void showInvitation(JsonDTO.CaseFile caseFile) {
    this.selectedCaseFile = caseFile;
    onInvitation = true;
    if (chosenLangCode == null
        || caseFile.getLocalizations() == null
        || !caseFile.getLocalizations().containsKey(chosenLangCode)) {
      chosenLangCode = preferredLangCode(caseFile);
    }

    MenuPage page =
        new MenuPage(ui.i18n.CaseTitles.displayTitle(caseFile), L10n.t("invitation.title"));

    // The letter: invitation prose laid out as a written letter — salutation, body, and a trailing
    // signature — on a vellum dossier card in the typewriter face (a detective's written record —
    // DESIGN.md §3). The reading surface may scroll for a long letter; the rest of the page
    // (language pick, confirm) stays fixed.
    Node letter = buildLetterContent(invitationText(caseFile, chosenLangCode));

    ScrollPane letterScroll = new ScrollPane(letter);
    letterScroll.setFitToWidth(true);
    letterScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    letterScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    letterScroll.getStyleClass().add("case-letter-scroll");
    letterScroll.setMinHeight(0);

    VBox letterCard = new VBox(letterScroll);
    letterCard.getStyleClass().add("case-letter-card");
    letterCard.setMaxWidth(760);
    VBox.setVgrow(letterScroll, Priority.ALWAYS);
    VBox.setVgrow(letterCard, Priority.ALWAYS);

    // Per-case language pick.
    Label langCaption = new Label(L10n.t("invitation.chooseLanguage"));
    langCaption.getStyleClass().add("menu-subtitle");
    HBox langRow = new HBox(10);
    langRow.setAlignment(Pos.CENTER);
    List<Button> focusOrder = new ArrayList<>();
    for (String code : sortedLanguageCodes(caseFile)) {
      String langName = caseFile.getLocalizations().get(code).getLanguageName();
      Button langButton = new Button(langName == null || langName.isBlank() ? code : langName);
      langButton.getStyleClass().add("menu-plate");
      // Render each language name in its own script's face, whatever the UI language (DEC-10).
      langButton.getStyleClass().add("lang-name-" + code);
      if (code.equals(chosenLangCode)) {
        langButton.getStyleClass().add("menu-plate--primary");
      }
      langButton.setOnAction(
          event -> {
            playSound.accept("click.wav");
            chosenLangCode = code;
            showInvitation(caseFile); // re-render the letter + selection in this language
          });
      focusOrder.add(langButton);
      langRow.getChildren().add(langButton);
    }
    VBox langBlock = new VBox(8, langCaption, langRow);
    langBlock.setAlignment(Pos.CENTER);

    Button confirm = plate(primaryKey, true, () -> onConfirm.accept(caseFile, chosenLangCode));
    confirm.setMaxWidth(Region.USE_PREF_SIZE);

    VBox content = new VBox(20, letterCard, langBlock, confirm);
    content.setAlignment(Pos.CENTER);
    content.setMinSize(0, 0);

    // Confirm is the primary; it leads the focus ring, then the language plates.
    List<Button> order = new ArrayList<>();
    order.add(confirm);
    order.addAll(focusOrder);

    page.setContent(content);
    page.setBottomStrip(buildBackStrip(order, this::showGallery, "langSelect.back"));
    installKeyNav(page, order);

    getChildren().setAll(page);
    Platform.runLater(confirm::requestFocus);
  }

  // ====================== Shared chrome helpers ======================

  private Button plate(String key, boolean primary, Runnable action) {
    Button button = new Button(L10n.t(key));
    button.getStyleClass().add("menu-plate");
    if (primary) {
      button.getStyleClass().add("menu-plate--primary");
    }
    button.setMaxWidth(Double.MAX_VALUE);
    button.setOnAction(
        event -> {
          playSound.accept("click.wav");
          action.run();
        });
    return button;
  }

  private Node buildBackStrip(List<Button> focusOrder, Runnable backAction, String labelKey) {
    Button back = plate(labelKey, false, backAction);
    back.setMaxWidth(Region.USE_PREF_SIZE);
    focusOrder.add(back);
    HBox strip = new HBox(back);
    strip.setAlignment(Pos.CENTER_LEFT);
    strip.getStyleClass().add("menu-bottom-strip");
    return strip;
  }

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

  // ====================== Case metadata helpers ======================

  /** The invitation text for {@code caseFile} in {@code langCode}, or "" when unavailable. */
  private static String invitationText(JsonDTO.CaseFile caseFile, String langCode) {
    if (caseFile.getLocalizations() == null || langCode == null) {
      return "";
    }
    JsonDTO.CaseFile.LocalizedData data = caseFile.getLocalizations().get(langCode);
    String invitation = data == null ? null : data.getInvitation();
    return invitation == null ? "" : invitation;
  }

  /** A one-line invitation teaser for a cover's hover excerpt (collapsed whitespace, clipped). */
  private static String excerptOf(JsonDTO.CaseFile caseFile) {
    String invitation = invitationText(caseFile, preferredLangCode(caseFile));
    if (invitation.isBlank()) {
      return null;
    }
    String oneLine = invitation.replaceAll("\\s+", " ").trim();
    int max = 120;
    return oneLine.length() <= max ? oneLine : oneLine.substring(0, max).trim() + "…";
  }

  /** The UI language if the case offers it, else the case's first available language code. */
  private static String preferredLangCode(JsonDTO.CaseFile caseFile) {
    List<String> codes = sortedLanguageCodes(caseFile);
    if (codes.isEmpty()) {
      return null;
    }
    String ui = L10n.language();
    return codes.contains(ui) ? ui : codes.get(0);
  }

  private static List<String> sortedLanguageCodes(JsonDTO.CaseFile caseFile) {
    if (caseFile.getLocalizations() == null) {
      return new ArrayList<>();
    }
    List<String> langCodes = new ArrayList<>(caseFile.getLocalizations().keySet());
    Collections.sort(langCodes);
    return langCodes;
  }
}

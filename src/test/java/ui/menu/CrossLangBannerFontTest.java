package ui.menu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import ui.i18n.L10n;

/**
 * Locks the cross-language banner's per-script name rendering (.scratch/gui-crosslang-banner-font):
 * an embedded language NAME must render in a face that covers ITS OWN script (via {@code
 * .lang-name-<code>}), identical regardless of the active UI font, while the surrounding sentence
 * keeps the active-UI face. Before the fix the whole banner element used one font, so the embedded
 * name (or the sentence) fell back glyph-by-glyph (stretched Cyrillic).
 */
public class CrossLangBannerFontTest {

  private static final Color INK = Color.web("#241E17"); // -sl-ink (light)
  private static final Color VELLUM = Color.web("#F6EEDB"); // -sl-vellum (light)

  @BeforeClass
  public static void initJfx() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    try {
      Platform.startup(latch::countDown);
    } catch (IllegalStateException already) {
      latch.countDown();
    }
    assertTrue(latch.await(5, TimeUnit.SECONDS));
    onFx(ui.util.FontLoader::loadAll);
  }

  @Test
  public void bodyEmbeddedNameKeepsItsScriptUnderEnglishUi() throws Exception {
    Assume.assumeTrue(
        Font.getFamilies().contains("PT Serif") && Font.getFamilies().contains("Special Elite"));
    onFx(
        () -> {
          L10n.setLanguage("en");
          TextFlow body = mountBody("ru", "Русский");
          for (Text span : spans(body)) {
            if (span.getStyleClass().contains("lang-name-ru")) {
              assertEquals(
                  "embedded Russian name keeps its Cyrillic face under English UI",
                  "PT Serif",
                  span.getFont().getFamily());
            } else {
              assertEquals(
                  "the sentence keeps the active English typewriter face",
                  "Special Elite",
                  span.getFont().getFamily());
            }
            assertEquals("body spans are ink on the vellum banner", INK, span.getFill());
          }
        });
  }

  @Test
  public void bodyEmbeddedNameKeepsItsScriptUnderRussianUi() throws Exception {
    Assume.assumeTrue(
        Font.getFamilies().contains("PT Serif") && Font.getFamilies().contains("Spectral"));
    onFx(
        () -> {
          L10n.setLanguage("ru");
          TextFlow body = mountBody("en", "English");
          for (Text span : spans(body)) {
            if (span.getStyleClass().contains("lang-name-en")) {
              assertEquals(
                  "embedded English name renders in Spectral even under a Russian UI",
                  "Spectral",
                  span.getFont().getFamily());
            } else {
              assertEquals(
                  "the Russian sentence renders in its Cyrillic face (PT Serif)",
                  "PT Serif",
                  span.getFont().getFamily());
            }
          }
          L10n.setLanguage("en");
        });
  }

  @Test
  public void switchPlateSpansAreVellumSized19AndPerScript() throws Exception {
    Assume.assumeTrue(
        Font.getFamilies().contains("PT Serif") && Font.getFamilies().contains("Spectral"));
    onFx(
        () -> {
          L10n.setLanguage("en");
          Button btn = mountPlate(L10n.t("caseSelect.langSuggest.switch"), "ru", "Русский", true);
          List<Text> spans = spans((javafx.scene.layout.Pane) btn.getGraphic());
          assertTrue("the plate label must split into spans", spans.size() >= 2);
          assertTrue(
              "the plate hugs its content height (no TextFlow-graphic vertical blow-up)",
              btn.prefHeight(-1) < 120);
          for (Text span : spans) {
            assertEquals("primary plate spans are vellum on petrol", VELLUM, span.getFill());
            assertEquals(
                "plate spans inherit the 19px plate size", 19.0, span.getFont().getSize(), 0.5);
            if (span.getStyleClass().contains("lang-name-ru")) {
              assertEquals(
                  "the name keeps its Cyrillic face", "PT Serif", span.getFont().getFamily());
            } else {
              assertEquals(
                  "the sentence keeps the active English plate face",
                  "Spectral",
                  span.getFont().getFamily());
            }
          }
        });
  }

  @Test
  public void arabicNameRendersInAmiri() throws Exception {
    Assume.assumeTrue(Font.getFamilies().contains("Amiri"));
    onFx(
        () -> {
          L10n.setLanguage("en");
          TextFlow body = mountBody("ar", "العربية");
          boolean sawAmiri = false;
          for (Text span : spans(body)) {
            if (span.getStyleClass().contains("lang-name-ar")) {
              assertEquals(
                  "embedded Arabic name renders in Amiri", "Amiri", span.getFont().getFamily());
              sawAmiri = true;
            }
          }
          assertTrue("an Arabic name span must exist", sawAmiri);
        });
  }

  // --- harness ---

  private static TextFlow mountBody(String nameLang, String nameText) {
    TextFlow body =
        CaseSelectionView.bannerSpans(L10n.t("caseSelect.langSuggest.body"), nameLang, nameText);
    body.getStyleClass().add("lang-suggest-text");
    mount(body);
    return body;
  }

  private static Button mountPlate(
      String template, String nameLang, String nameText, boolean primary) {
    Button button = CaseSelectionView.spanPlate(template, nameLang, nameText, primary);
    mount(button);
    return button;
  }

  /** Wraps a node in the real banner + active-language root and realises CSS/layout. */
  private static void mount(Node node) {
    VBox banner = new VBox(node);
    banner.getStyleClass().add("lang-suggest-banner");
    VBox root = new VBox(banner);
    root.getStyleClass().add("lang-" + L10n.language());
    Scene scene = new Scene(root, 700, 300);
    scene.getStylesheets().add(ui.util.Theme.baseStylesheet());
    root.applyCss();
    root.layout();
  }

  private static List<Text> spans(javafx.scene.layout.Pane container) {
    List<Text> out = new ArrayList<>();
    for (Node child : container.getChildren()) {
      if (child instanceof Text t) {
        out.add(t);
      }
    }
    return out;
  }

  private interface FxTask {
    void run() throws Exception;
  }

  private static void onFx(FxTask task) throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    Throwable[] err = new Throwable[1];
    Platform.runLater(
        () -> {
          try {
            task.run();
          } catch (Throwable t) {
            err[0] = t;
          } finally {
            done.countDown();
          }
        });
    assertTrue("FX task timed out", done.await(10, TimeUnit.SECONDS));
    if (err[0] instanceof AssertionError ae) {
      throw ae;
    } else if (err[0] != null) {
      throw new RuntimeException(err[0]);
    }
  }
}

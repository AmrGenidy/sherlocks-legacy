package ui.util;

import java.io.InputStream;
import java.util.List;
import javafx.scene.text.Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads the brand typefaces declared by DESIGN.md §3 from {@code /fonts/*.ttf} at JavaFX startup.
 * Missing or malformed files are reported but never abort startup — JavaFX falls back to whatever
 * the CSS font-family chain resolves.
 *
 * <p>Call {@link #loadAll()} once, before any {@code Scene} is built.
 */
public final class FontLoader {
  private static final Logger logger = LoggerFactory.getLogger(FontLoader.class);

  /** Resource paths to load, in the order they should appear at registration. */
  private static final List<String> FONT_RESOURCES =
      List.of(
          // Spectral — body / UI text per DESIGN.md §3
          "/fonts/Spectral-Regular.ttf",
          "/fonts/Spectral-Bold.ttf",
          // Playfair Display — display / headings per DESIGN.md §3
          "/fonts/PlayfairDisplay-Regular.ttf",
          "/fonts/PlayfairDisplay-Bold.ttf",
          // Special Elite — typewriter face for journal, exam, terminal per DESIGN.md §3
          "/fonts/SpecialElite-Regular.ttf",
          // Amiri — Arabic script (UI language "ar"; .lang-ar CSS rules select it)
          "/fonts/Amiri-Regular.ttf",
          "/fonts/Amiri-Bold.ttf",
          // PT Serif — Cyrillic script (UI language "ru"; .lang-ru CSS rules select it)
          "/fonts/PTSerif-Regular.ttf",
          "/fonts/PTSerif-Bold.ttf");

  /** Brand families that should be registered after a successful load. */
  private static final List<String> EXPECTED_FAMILIES =
      List.of("Playfair Display", "Spectral", "Special Elite", "Amiri", "PT Serif");

  // Optional serif CJK face for the Chinese UI language ("zh"; .lang-zh CSS selects it). Bundling is
  // deferred (the OTF is large; see .scratch/gui-add-languages) — until then .lang-zh falls back to a
  // system CJK font via its family stack. Drop the OFL OTF(s) here and they register automatically.
  private static final List<String> OPTIONAL_CJK_RESOURCES =
      List.of("/fonts/NotoSerifCJKsc-Regular.otf", "/fonts/NotoSerifCJKsc-Bold.otf");

  private FontLoader() {}

  public static void loadAll() {
    int loaded = 0;
    int missingFiles = 0;
    int loadErrors = 0;

    for (String path : FONT_RESOURCES) {
      try (InputStream in = FontLoader.class.getResourceAsStream(path)) {
        if (in == null) {
          logger.warn("Brand font file missing on classpath: {}", path);
          missingFiles++;
          continue;
        }
        Font font = Font.loadFont(in, 12);
        if (font == null) {
          logger.error("Font.loadFont returned null for {}", path);
          loadErrors++;
        } else {
          loaded++;
          logger.debug("Registered font {} from {}", font.getFamily(), path);
        }
      } catch (Exception ex) {
        logger.error("Failed to load font {}", path, ex);
        loadErrors++;
      }
    }

    loadOptionalCjk();

    List<String> registered = Font.getFamilies();
    List<String> presentBrand = EXPECTED_FAMILIES.stream().filter(registered::contains).toList();
    List<String> missingBrand =
        EXPECTED_FAMILIES.stream().filter(f -> !registered.contains(f)).toList();

    logger.info(
        "Brand font load complete: {} files loaded, {} files missing, {} errors. "
            + "Registered brand families: {}. Missing brand families: {}.",
        loaded,
        missingFiles,
        loadErrors,
        presentBrand,
        missingBrand);

    if (!missingBrand.isEmpty()) {
      logger.warn(
          "DESIGN.md §3 typefaces are not all available. "
              + "Add the missing .ttf files under src/main/resources/fonts/ "
              + "(see fonts/README.md) before CSS migrates to brand-family names.");
    }
  }

  /**
   * Registers the bundled CJK serif face if present. Its absence is normal (bundling is deferred), so
   * a missing file logs at debug level — never a warning — and {@code .lang-zh} uses its system-CJK
   * fallback stack instead.
   */
  private static void loadOptionalCjk() {
    for (String path : OPTIONAL_CJK_RESOURCES) {
      try (InputStream in = FontLoader.class.getResourceAsStream(path)) {
        if (in == null) {
          logger.debug("Optional CJK font not bundled: {}", path);
          continue;
        }
        Font font = Font.loadFont(in, 12);
        if (font != null) {
          logger.info("Registered CJK font {} from {}", font.getFamily(), path);
        }
      } catch (Exception ex) {
        logger.debug("Could not load optional CJK font {}", path, ex);
      }
    }
  }
}

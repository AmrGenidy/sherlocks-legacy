package ui.i18n;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * UI localization seam (.scratch/ui-localization). All user-facing UI strings resolve through
 * {@code L10n.t(key)} against {@code i18n/messages_<code>.properties}, keyed by the same language
 * codes case content already uses ({@code en}, {@code ar} — CONTEXT.md "Language Code").
 *
 * <p>Case content (Statements, Invitations, Clue text…) is NOT resolved here — it ships localized
 * inside each Case JSON. This class covers chrome: menus, buttons, command feedback, Watson
 * framing, tutorial scaffolding.
 *
 * <p>Live switching: {@link #setLanguage} swaps the bundle and notifies registered listeners (the
 * shell re-applies chrome text and the per-language font style class; the visible screen
 * re-renders). The layout itself is always left-to-right in every language — a deliberate product
 * decision; Arabic text still renders correctly inside labels (JavaFX handles bidi text within a
 * node). Listeners are invoked on the caller's thread — in practice the FX Application Thread,
 * since the selector is a menu control.
 */
public final class L10n {

  public static final String ENGLISH = "en";
  public static final String ARABIC = "ar";
  public static final String RUSSIAN = "ru";
  public static final String CHINESE = "zh"; // Simplified
  public static final String TURKISH = "tr";
  public static final String GERMAN = "de";
  public static final String FRENCH = "fr";
  public static final String SPANISH = "es";

  // The supported UI languages, in selector display order. Each has an i18n/messages_<code>
  // bundle and a .lang-<code> font class; the layout stays left-to-right in every one.
  private static final List<String> SUPPORTED =
      List.of(ENGLISH, ARABIC, RUSSIAN, CHINESE, TURKISH, GERMAN, FRENCH, SPANISH);

  // Each language's name in its own script (endonym), shown in the selector regardless of the
  // active UI language so a player always recognises their language.
  private static final Map<String, String> ENDONYMS =
      Map.of(
          ENGLISH, "English",
          ARABIC, "العربية",
          RUSSIAN, "Русский",
          CHINESE, "中文",
          TURKISH, "Türkçe",
          GERMAN, "Deutsch",
          FRENCH, "Français",
          SPANISH, "Español");

  private static volatile String language = ENGLISH;
  private static volatile ResourceBundle bundle = load(ENGLISH);
  private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();

  private L10n() {}

  private static ResourceBundle load(String code) {
    return ResourceBundle.getBundle("i18n.messages", new Locale(code));
  }

  /** Resolves a key in the active language; missing keys render visibly as {@code !key!}. */
  public static String t(String key) {
    try {
      return bundle.getString(key);
    } catch (RuntimeException e) {
      return "!" + key + "!";
    }
  }

  /** Resolves a key and formats it with {@link MessageFormat} arguments. */
  public static String t(String key, Object... args) {
    return new MessageFormat(t(key), new Locale(language)).format(args);
  }

  /**
   * Localizes an engine message: returns the {@code key}'s value in the active language formatted
   * with {@code args}, or the supplied {@code fallback} (the message's English text) when the key is
   * absent. Used at the client ingestion seams so a language that has not yet translated a key shows
   * its English fallback instead of {@code !key!} (supports the Arabic-first rollout).
   */
  public static String tOr(String key, String fallback, Object... args) {
    if (key == null) {
      return fallback;
    }
    String raw;
    try {
      raw = bundle.getString(key);
    } catch (RuntimeException e) {
      return fallback;
    }
    return new MessageFormat(raw, new Locale(language)).format(args);
  }

  /** Like {@link #tOr} but returns {@code null} when the key is absent (lets callers detect a miss). */
  public static String tOrNull(String key, Object... args) {
    if (key == null) {
      return null;
    }
    String raw;
    try {
      raw = bundle.getString(key);
    } catch (RuntimeException e) {
      return null;
    }
    return new MessageFormat(raw, new Locale(language)).format(args);
  }

  public static String language() {
    return language;
  }

  /**
   * Whether the active UI language reads right-to-left. Layout stays LTR overall (a product
   * decision), but individual widgets that must mirror to read correctly — e.g. the Final Exam's
   * fill-in slots relative to an RTL question — use this to flip only themselves.
   */
  public static boolean isRtl() {
    return ARABIC.equals(language);
  }

  /** The supported UI languages, in selector display order. */
  public static List<String> uiLanguages() {
    return SUPPORTED;
  }

  /** Whether {@code code} is a UI language the interface can actually switch to. */
  public static boolean isUiLanguage(String code) {
    return SUPPORTED.contains(code);
  }

  /**
   * A language's name in its own script (endonym) — English / العربية / Русский / 中文 / Türkçe /
   * Deutsch / Français / Español — for showing a language in its own type regardless of the active
   * UI language. Unknown codes return the code.
   */
  public static String endonym(String code) {
    return ENDONYMS.getOrDefault(code, code);
  }

  public static void setLanguage(String code) {
    if (code == null || code.equals(language)) {
      return;
    }
    bundle = load(code);
    language = code;
    for (Runnable listener : listeners) {
      listener.run();
    }
  }

  /** Registers a callback fired after every language switch. */
  public static void onChange(Runnable listener) {
    listeners.add(listener);
  }
}

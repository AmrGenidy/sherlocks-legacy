package ui.casemaker.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A piece of authored text held in one or more languages (slice 5). The no-argument accessors
 * operate on the {@link #PRIMARY} language (the language slices 1–4 author in), so the existing
 * single-language editors keep using {@code get()}/{@code set(text)} unchanged; the Localization
 * tab uses the language-qualified {@code get(lang)}/{@code set(lang, text)} to translate every
 * field.
 *
 * <p>Blank values are stored as "absent" so the serializer and validator see a missing translation
 * rather than an empty string.
 */
public final class LocalizedText {

  /** The primary authoring language — what the non-localization editors read and write. */
  public static final String PRIMARY = "en";

  private final Map<String, String> byLang = new LinkedHashMap<>();

  public String get() {
    return get(PRIMARY);
  }

  public String get(String lang) {
    return byLang.get(lang);
  }

  public void set(String text) {
    set(PRIMARY, text);
  }

  public void set(String lang, String text) {
    if (text == null || text.isBlank()) {
      byLang.remove(lang);
    } else {
      byLang.put(lang, text);
    }
  }

  public boolean has(String lang) {
    return byLang.containsKey(lang);
  }

  public boolean isEmpty() {
    return byLang.isEmpty();
  }

  /** An unmodifiable snapshot of language → text (only languages with non-blank text). */
  public Map<String, String> asMap() {
    return Map.copyOf(byLang);
  }
}

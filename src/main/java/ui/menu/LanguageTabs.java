package ui.menu;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Pure rules behind the case-selection language tabs (GUI G5b): which language tabs the shelf
 * shows, which tab is selected by default, which cases a tab filters to, and whether to offer
 * switching the interface to a casebook's language.
 *
 * <p>DTO-agnostic on purpose — each item yields its language codes through {@code languagesOf}, so
 * the view passes {@code caseFile -> caseFile.getLocalizations().keySet()} while tests pass plain
 * lists. Presentation-only and side-effect free.
 */
public final class LanguageTabs {

  private LanguageTabs() {}

  /**
   * The sorted, de-duplicated <b>union</b> of every item's languages. An item that provides no
   * languages (null / empty) contributes no tab — so a language no case offers never gets a tab.
   */
  public static <T> List<String> available(
      List<T> items, Function<? super T, ? extends Collection<String>> languagesOf) {
    TreeSet<String> codes = new TreeSet<>();
    if (items != null) {
      for (T item : items) {
        Collection<String> langs = languagesOf.apply(item);
        if (langs != null) {
          for (String code : langs) {
            if (code != null && !code.isBlank()) {
              codes.add(code);
            }
          }
        }
      }
    }
    return new ArrayList<>(codes);
  }

  /**
   * The tab to select on entry: the UI language if it has cases, otherwise the first tab, or {@code
   * null} when there are no tabs at all.
   */
  public static String defaultTab(List<String> tabs, String uiLanguage) {
    if (tabs == null || tabs.isEmpty()) {
      return null;
    }
    return tabs.contains(uiLanguage) ? uiLanguage : tabs.get(0);
  }

  /** The items that provide {@code language}, in their original order. */
  public static <T> List<T> itemsIn(
      List<T> items,
      String language,
      Function<? super T, ? extends Collection<String>> languagesOf) {
    List<T> filtered = new ArrayList<>();
    if (items != null && language != null) {
      for (T item : items) {
        Collection<String> langs = languagesOf.apply(item);
        if (langs != null && langs.contains(language)) {
          filtered.add(item);
        }
      }
    }
    return filtered;
  }

  /**
   * Whether to offer switching the interface: only on a true mismatch between the viewed tab's
   * language and the current UI language (both present and different). Never forced — the view
   * turns this into a dismissible, in-world offer.
   */
  public static boolean shouldSuggestSwitch(String viewedLanguage, String uiLanguage) {
    return viewedLanguage != null && uiLanguage != null && !viewedLanguage.equals(uiLanguage);
  }
}

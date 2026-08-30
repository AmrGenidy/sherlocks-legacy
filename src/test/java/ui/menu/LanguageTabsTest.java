package ui.menu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.function.Function;
import org.junit.Test;

/**
 * The tested core of the case-selection language tabs (GUI G5b): which tabs exist, which one is
 * selected by default, which cases a tab shows, and whether the cross-language switch suggestion
 * applies. DTO-agnostic — a case is modelled here as its list of language codes — so the rules are
 * pinned without building {@code CaseFile}s or a JavaFX scene.
 */
public class LanguageTabsTest {

  // A "case" is just its available language codes; the extractor is identity.
  private static final Function<List<String>, List<String>> LANGS = Function.identity();

  @Test
  public void tabsAreTheSortedUnionOfAvailableCaseLanguages() {
    List<List<String>> cases = List.of(List.of("en", "ar"), List.of("en", "ru"), List.of("ar"));
    assertEquals(List.of("ar", "en", "ru"), LanguageTabs.available(cases, LANGS));
  }

  @Test
  public void aLanguageNoCaseProvidesGetsNoTab() {
    // No case offers "es" → no Spanish tab.
    List<List<String>> cases = List.of(List.of("en"), List.of("en", "ru"));
    assertFalse(LanguageTabs.available(cases, LANGS).contains("es"));
    assertEquals(List.of("en", "ru"), LanguageTabs.available(cases, LANGS));
  }

  @Test
  public void casesWithNoLanguagesProduceNoTab() {
    List<List<String>> cases = List.of(List.of(), List.of("en"), List.of());
    assertEquals(List.of("en"), LanguageTabs.available(cases, LANGS));
  }

  @Test
  public void noCasesMeansNoTabs() {
    assertTrue(LanguageTabs.available(List.<List<String>>of(), LANGS).isEmpty());
  }

  @Test
  public void defaultTabIsTheUiLanguageWhenItHasCases() {
    assertEquals("ar", LanguageTabs.defaultTab(List.of("ar", "en", "ru"), "ar"));
  }

  @Test
  public void defaultTabFallsBackToFirstWhenUiLanguageHasNoCases() {
    assertEquals("ar", LanguageTabs.defaultTab(List.of("ar", "en", "ru"), "fr"));
  }

  @Test
  public void defaultTabIsNullWhenThereAreNoTabs() {
    assertNull(LanguageTabs.defaultTab(List.of(), "en"));
  }

  @Test
  public void itemsInReturnsOnlyCasesProvidingTheLanguageInOrder() {
    List<String> a = List.of("en", "ar");
    List<String> b = List.of("ru");
    List<String> c = List.of("en");
    List<List<String>> cases = List.of(a, b, c);
    assertEquals(List.of(a, c), LanguageTabs.itemsIn(cases, "en", LANGS));
    assertEquals(List.of(b), LanguageTabs.itemsIn(cases, "ru", LANGS));
    assertTrue(LanguageTabs.itemsIn(cases, "es", LANGS).isEmpty());
  }

  @Test
  public void suggestSwitchOnlyOnATrueMismatch() {
    assertTrue(LanguageTabs.shouldSuggestSwitch("ar", "en"));
    assertFalse(LanguageTabs.shouldSuggestSwitch("en", "en"));
    assertFalse(LanguageTabs.shouldSuggestSwitch(null, "en"));
    assertFalse(LanguageTabs.shouldSuggestSwitch("ar", null));
  }
}

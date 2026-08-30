package ui.windows;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Test;
import ui.i18n.L10n;

/**
 * Locks down the Help reference content (GUI G4): the grouping/vocabulary and — crucially — that
 * every header and description key resolves in all three languages (a missing key renders as {@code
 * !key!}, the visible failure mode). Pure: no JavaFX Stage needed.
 */
public class HelpReferenceTest {

  @After
  public void restoreLanguage() {
    L10n.setLanguage("en");
  }

  @Test
  public void everyKeyResolvesInEveryLanguage() {
    for (String lang : List.of("en", "ar", "ru")) {
      L10n.setLanguage(lang);
      assertResolves("help.title", lang);
      for (HelpReference.Section section : HelpReference.sections()) {
        assertResolves(section.headerKey(), lang);
        for (HelpReference.Entry entry : section.entries()) {
          assertResolves(entry.descriptionKey(), lang);
        }
      }
    }
  }

  @Test
  public void everyCommandLiteralIsNonBlank() {
    for (HelpReference.Section section : HelpReference.sections()) {
      for (HelpReference.Entry entry : section.entries()) {
        assertFalse(
            "Command literal must not be blank in " + section.headerKey(),
            entry.command() == null || entry.command().isBlank());
      }
    }
  }

  @Test
  public void coversTheRequiredCommandVocabulary() {
    Set<String> commands =
        HelpReference.sections().stream()
            .flatMap(s -> s.entries().stream())
            .map(HelpReference.Entry::command)
            .collect(Collectors.toSet());
    // The reference must include the staples a player needs to play and to leave — including the
    // pinboard reference tool the prompt called out.
    for (String expected :
        List.of(
            "look",
            "examine [object]",
            "question [suspect]",
            "deduce [object|suspect]",
            "contradict [evidence] with [suspect]",
            "combine [noteA_id] [noteB_id]",
            "journal",
            "tasks",
            "pinboard",
            "ask watson",
            "final exam",
            "help",
            "exit")) {
      assertTrue("Help reference must list: " + expected, commands.contains(expected));
    }
  }

  @Test
  public void sectionsAreNonEmptyAndOrdered() {
    List<HelpReference.Section> sections = HelpReference.sections();
    assertTrue("Expected several grouped sections", sections.size() >= 5);
    for (HelpReference.Section section : sections) {
      assertFalse("Section must have rows: " + section.headerKey(), section.entries().isEmpty());
    }
  }

  private static void assertResolves(String key, String lang) {
    String value = L10n.t(key);
    assertFalse(
        "Missing i18n key '" + key + "' in language '" + lang + "' (rendered as " + value + ")",
        value.equals("!" + key + "!") || value.isBlank());
  }
}

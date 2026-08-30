package ui.menu.quotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;
import ui.menu.quotes.MenuQuotes.Quote;

/**
 * Loader contract for the rotating-epigraph data (.scratch/main-menu): it parses the bundled file,
 * honours {@code rotation.enabledGroups}, and resolves quote text by language with an {@code en}
 * fallback.
 */
public class MenuQuotesTest {

  @Test
  public void parsesBundledFile() {
    MenuQuotes quotes = MenuQuotes.load();

    MenuQuotes.Rotation rotation = quotes.rotation();
    assertEquals(60, rotation.intervalSeconds());
    assertTrue(rotation.changeOnReturnToMainMenu());
    assertEquals("shuffle", rotation.order());
    assertEquals(240, rotation.fadeMs());
    assertEquals(List.of("holmes", "adventure_original"), rotation.enabledGroups());

    // 16 holmes + 8 adventure_original quotes, flattened in enabledGroups order.
    assertEquals(24, quotes.quotes().size());
    assertEquals("afoot", quotes.quotes().get(0).id());
    assertEquals("The game is afoot.", quotes.quotes().get(0).text("en"));
  }

  @Test
  public void includesOnlyEnabledGroups() throws Exception {
    String json =
        "{\"rotation\":{\"intervalSeconds\":5,\"order\":\"sequence\",\"fadeMs\":100,"
            + "\"enabledGroups\":[\"holmes\"]},"
            + "\"groups\":{"
            + "\"holmes\":[{\"id\":\"a\",\"text\":{\"en\":\"A\"}}],"
            + "\"disabled\":[{\"id\":\"z\",\"text\":{\"en\":\"Z\"}}]}}";

    MenuQuotes quotes = MenuQuotes.parse(json);

    assertEquals(List.of("holmes"), quotes.rotation().enabledGroups());
    assertEquals(1, quotes.quotes().size());
    assertEquals("a", quotes.quotes().get(0).id());
    assertTrue(
        "quotes from disabled groups must not be loaded",
        quotes.quotes().stream().noneMatch(q -> "z".equals(q.id())));
  }

  @Test
  public void resolvesLanguageWithEnglishFallback() throws Exception {
    String json =
        "{\"rotation\":{\"enabledGroups\":[\"g\"]},"
            + "\"groups\":{\"g\":[{\"id\":\"q\",\"text\":{\"en\":\"hello\",\"ru\":\"привет\"}}]}}";

    Quote quote = MenuQuotes.parse(json).quotes().get(0);

    assertEquals("привет", quote.text("ru")); // exact language
    assertEquals("hello", quote.text("ar")); // missing language → en fallback
    assertEquals("hello", quote.text("en"));
    assertEquals("hello", quote.text(null)); // null language → en fallback
  }

  @Test
  public void preservesEnabledGroupOrderWhenFlattening() throws Exception {
    String json =
        "{\"rotation\":{\"enabledGroups\":[\"second\",\"first\"]},"
            + "\"groups\":{"
            + "\"first\":[{\"id\":\"f\",\"text\":{\"en\":\"F\"}}],"
            + "\"second\":[{\"id\":\"s\",\"text\":{\"en\":\"S\"}}]}}";

    MenuQuotes quotes = MenuQuotes.parse(json);

    assertEquals("s", quotes.quotes().get(0).id()); // 'second' group comes first
    assertEquals("f", quotes.quotes().get(1).id());
  }

  @Test
  public void loadNeverThrowsAndAlwaysHasAQuote() {
    MenuQuotes quotes = MenuQuotes.load();
    assertFalse(quotes.quotes().isEmpty());
  }
}

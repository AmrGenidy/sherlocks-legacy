package ui.menu.quotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.Test;
import ui.menu.quotes.MenuQuotes.Quote;

/** The rotator never returns the same quote twice in a row (MENU_DESIGN.md). */
public class QuoteRotatorTest {

  private static List<Quote> quotes(int n) {
    List<Quote> list = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      list.add(new Quote("q" + i, null, Map.of("en", "Q" + i)));
    }
    return list;
  }

  @Test
  public void shuffleNeverRepeatsImmediately() {
    QuoteRotator rotator = new QuoteRotator(quotes(5), "shuffle", new Random(1234));
    Quote previous = rotator.next();
    for (int i = 0; i < 500; i++) {
      Quote current = rotator.next();
      assertNotEquals("a quote must never repeat back-to-back", previous.id(), current.id());
      previous = current;
    }
  }

  @Test
  public void sequenceNeverRepeatsImmediatelyAndCyclesAll() {
    QuoteRotator rotator = new QuoteRotator(quotes(3), "sequence");
    String first = rotator.next().id();
    String second = rotator.next().id();
    String third = rotator.next().id();
    String wrapped = rotator.next().id();
    assertNotEquals(first, second);
    assertNotEquals(second, third);
    assertNotEquals(third, wrapped); // wraps without repeating across the boundary
    assertEquals(first, wrapped); // and cycles back to the start
  }

  @Test
  public void singleQuoteIsReturnedEvenThoughItRepeats() {
    QuoteRotator rotator = new QuoteRotator(quotes(1), "shuffle", new Random(1));
    assertEquals("q0", rotator.next().id());
    assertEquals("q0", rotator.next().id());
  }

  @Test
  public void emptyRotatorReturnsNull() {
    QuoteRotator rotator = new QuoteRotator(List.of(), "shuffle");
    assertTrue(rotator.isEmpty());
    assertNull(rotator.next());
  }
}

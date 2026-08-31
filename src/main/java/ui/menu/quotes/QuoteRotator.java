package ui.menu.quotes;

import java.util.List;
import java.util.Random;
import ui.menu.quotes.MenuQuotes.Quote;

/**
 * Picks the next epigraph for the caption ribbon, <b>never repeating the immediately previous
 * quote</b> (MENU_DESIGN.md). With {@code order == "shuffle"} it draws a random quote (other than
 * the last); otherwise it advances sequentially. A single-quote list necessarily repeats; an empty
 * list returns {@code null}.
 *
 * <p>The {@link Random} is injectable so the no-repeat invariant can be tested deterministically.
 */
public final class QuoteRotator {

  private final List<Quote> quotes;
  private final boolean shuffle;
  private final Random random;
  private int lastIndex = -1;

  public QuoteRotator(List<Quote> quotes, String order) {
    this(quotes, order, new Random());
  }

  public QuoteRotator(List<Quote> quotes, String order, Random random) {
    this.quotes = List.copyOf(quotes);
    this.shuffle = order == null || "shuffle".equalsIgnoreCase(order);
    this.random = random;
  }

  public boolean isEmpty() {
    return quotes.isEmpty();
  }

  public int size() {
    return quotes.size();
  }

  /** The next quote, never equal to the one returned just before (unless there is only one). */
  public Quote next() {
    if (quotes.isEmpty()) {
      return null;
    }
    if (quotes.size() == 1) {
      lastIndex = 0;
      return quotes.get(0);
    }
    int index;
    if (shuffle) {
      do {
        index = random.nextInt(quotes.size());
      } while (index == lastIndex);
    } else {
      index = (lastIndex + 1) % quotes.size();
    }
    lastIndex = index;
    return quotes.get(index);
  }
}

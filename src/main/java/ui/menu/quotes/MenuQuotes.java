package ui.menu.quotes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The rotating-epigraph data for the main-menu caption ribbon (MENU_DESIGN.md; .scratch/main-menu),
 * loaded from {@code /menu/quotes.json}.
 *
 * <p>Only the groups named in {@code rotation.enabledGroups} are loaded, flattened in that order;
 * each quote's text is localizable with an {@code en} fallback. Parsing is tree-based so unknown
 * keys (e.g. {@code _comment}) are ignored, and {@link #load()} never throws — a missing or
 * malformed file degrades to a single built-in quote so the menu always has something to show.
 */
public final class MenuQuotes {

  private static final Logger logger = LoggerFactory.getLogger(MenuQuotes.class);
  private static final String RESOURCE = "/menu/quotes.json";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * A single epigraph: a stable id, optional attribution, and localized text keyed by lang code.
   */
  public static final class Quote {
    private final String id;
    private final String source;
    private final Map<String, String> text;

    public Quote(String id, String source, Map<String, String> text) {
      this.id = id;
      this.source = source;
      this.text = text == null ? Map.of() : Map.copyOf(text);
    }

    public String id() {
      return id;
    }

    public String source() {
      return source;
    }

    /** The text in {@code lang}, falling back to {@code en}, then to an empty string. */
    public String text(String lang) {
      String value = lang == null ? null : text.get(lang);
      if (value != null && !value.isBlank()) {
        return value;
      }
      String en = text.get("en");
      return en != null ? en : "";
    }
  }

  /** The rotation policy from {@code rotation} in the file. */
  public static final class Rotation {
    private final int intervalSeconds;
    private final boolean changeOnReturnToMainMenu;
    private final String order;
    private final int fadeMs;
    private final List<String> enabledGroups;

    public Rotation(
        int intervalSeconds,
        boolean changeOnReturnToMainMenu,
        String order,
        int fadeMs,
        List<String> enabledGroups) {
      this.intervalSeconds = intervalSeconds;
      this.changeOnReturnToMainMenu = changeOnReturnToMainMenu;
      this.order = order;
      this.fadeMs = fadeMs;
      this.enabledGroups = List.copyOf(enabledGroups);
    }

    public int intervalSeconds() {
      return intervalSeconds;
    }

    public boolean changeOnReturnToMainMenu() {
      return changeOnReturnToMainMenu;
    }

    public String order() {
      return order;
    }

    public int fadeMs() {
      return fadeMs;
    }

    public List<String> enabledGroups() {
      return enabledGroups;
    }
  }

  private final Rotation rotation;
  private final List<Quote> quotes;

  public MenuQuotes(Rotation rotation, List<Quote> quotes) {
    this.rotation = rotation;
    this.quotes = List.copyOf(quotes);
  }

  public Rotation rotation() {
    return rotation;
  }

  /** The enabled quotes, flattened in {@code enabledGroups} order. */
  public List<Quote> quotes() {
    return quotes;
  }

  /** Loads the bundled {@code /menu/quotes.json}; never throws — degrades to a built-in quote. */
  public static MenuQuotes load() {
    try (InputStream in = MenuQuotes.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        logger.warn("Menu quotes resource missing on classpath: {}", RESOURCE);
        return fallback();
      }
      return parse(in);
    } catch (Exception e) {
      logger.error("Failed to load menu quotes from {}; using fallback.", RESOURCE, e);
      return fallback();
    }
  }

  public static MenuQuotes parse(String json) throws Exception {
    return parse(new java.io.ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
  }

  /**
   * Parses the quotes document. Only {@code rotation.enabledGroups} are included, in that order;
   * unknown keys are ignored.
   */
  public static MenuQuotes parse(InputStream in) throws Exception {
    JsonNode root = MAPPER.readTree(in);
    JsonNode rotationNode = root.path("rotation");

    List<String> enabledGroups = new ArrayList<>();
    for (JsonNode group : rotationNode.path("enabledGroups")) {
      enabledGroups.add(group.asText());
    }
    Rotation rotation =
        new Rotation(
            rotationNode.path("intervalSeconds").asInt(12),
            rotationNode.path("changeOnReturnToMainMenu").asBoolean(true),
            rotationNode.path("order").asText("shuffle"),
            rotationNode.path("fadeMs").asInt(240),
            enabledGroups);

    JsonNode groups = root.path("groups");
    List<Quote> quotes = new ArrayList<>();
    for (String group : enabledGroups) {
      JsonNode array = groups.path(group);
      if (!array.isArray()) {
        continue;
      }
      for (JsonNode quoteNode : array) {
        String id = quoteNode.path("id").asText(null);
        String source = quoteNode.hasNonNull("source") ? quoteNode.get("source").asText() : null;
        Map<String, String> text = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = quoteNode.path("text").fields();
        while (fields.hasNext()) {
          Map.Entry<String, JsonNode> entry = fields.next();
          text.put(entry.getKey(), entry.getValue().asText());
        }
        quotes.add(new Quote(id, source, text));
      }
    }
    return new MenuQuotes(rotation, quotes);
  }

  private static MenuQuotes fallback() {
    Rotation rotation = new Rotation(12, true, "shuffle", 240, Collections.emptyList());
    Quote quote = new Quote("afoot", "Sherlock Holmes", Map.of("en", "The game is afoot."));
    return new MenuQuotes(rotation, List.of(quote));
  }
}

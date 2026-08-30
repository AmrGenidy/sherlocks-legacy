package common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public class DialogueEventDTO implements Serializable {
  @Serial private static final long serialVersionUID = 2L;

  private final String title;
  private final String text;
  private final DialogueType type;

  /**
   * Optional UI-language localization keys for a generic engine response. When set, the client
   * renders {@code L10n.tOr(key, fallback, args)} in its own UI language; {@link #title}/{@link
   * #text} are the English fallbacks. Null for authored/already-localized content, shown verbatim.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final String textKey;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final String titleKey;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final List<String> args;

  private final long timestamp;

  public DialogueEventDTO(String title, String text, DialogueType type) {
    this(title, text, type, null, null, null, System.currentTimeMillis());
  }

  public DialogueEventDTO(String title, String text, DialogueType type, String textKey) {
    this(title, text, type, textKey, null, null, System.currentTimeMillis());
  }

  /** A fully localizable notice: title + body keys plus MessageFormat args. */
  public DialogueEventDTO(
      String title, String text, DialogueType type, String titleKey, String textKey, List<String> args) {
    this(title, text, type, textKey, titleKey, args, System.currentTimeMillis());
  }

  @JsonCreator
  public DialogueEventDTO(
      @JsonProperty("title") String title,
      @JsonProperty("text") String text,
      @JsonProperty("type") DialogueType type,
      @JsonProperty("textKey") String textKey,
      @JsonProperty("titleKey") String titleKey,
      @JsonProperty("args") List<String> args,
      @JsonProperty("timestamp") long timestamp) {
    this.title = title;
    this.text = text;
    this.type = type;
    this.textKey = textKey;
    this.titleKey = titleKey;
    this.args = args;
    this.timestamp = timestamp;
  }

  public String getTitle() {
    return title;
  }

  public String getText() {
    return text;
  }

  public DialogueType getType() {
    return type;
  }

  public String getTextKey() {
    return textKey;
  }

  public String getTitleKey() {
    return titleKey;
  }

  public List<String> getArgs() {
    return args;
  }

  public long getTimestamp() {
    return timestamp;
  }
}

package common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * A plain transcript line. {@link #text} is the always-present English fallback; when {@link
 * #messageKey} is set the client localizes it to its own UI language via {@code L10n.tOr(messageKey,
 * text, args)} at the ingestion seam (single-player {@code GuiGameOutputSink}, multiplayer {@code
 * GameClient}), so the same wire message reads correctly in each player's language. The constructor
 * is side-effect free (LAN wire allowlist).
 */
public class TextMessage implements Serializable {
  @Serial
  private static final long serialVersionUID = 2L;
  private final String text;
  private final boolean isError;
  // Optional localization key + MessageFormat args; null for a raw (already-final) line.
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final String messageKey;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final List<String> args;

  /** A raw line with no localization key (the text is shown as-is). */
  public TextMessage(String text, boolean isError) {
    this(text, isError, null, null);
  }

  /** A localizable line: {@code text} is the English fallback, {@code messageKey} the L10n key. */
  @JsonCreator
  public TextMessage(
          @JsonProperty("text") String text,
          @JsonProperty("error") boolean isError,
          @JsonProperty("messageKey") String messageKey,
          @JsonProperty("args") List<String> args) {
    this.text = text;
    this.isError = isError;
    this.messageKey = messageKey;
    this.args = args;
  }

  public String getText() {
    return text;
  }

  public boolean isError() {
    return isError;
  }

  /** The localization key, or null for a raw line. */
  public String getMessageKey() {
    return messageKey;
  }

  /** MessageFormat arguments for {@link #messageKey}, or null. */
  public List<String> getArgs() {
    return args;
  }

  @Override
  public String toString() {
    return (isError ? "[ERROR] " : "") + text;
  }
}

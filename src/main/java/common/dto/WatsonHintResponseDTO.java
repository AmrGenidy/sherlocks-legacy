package common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

public class WatsonHintResponseDTO implements Serializable {
  private static final long serialVersionUID = 1L;
  private final String message;
  private final boolean isActualHint;

  /**
   * Optional UI-language localization key for a generic, non-authored Watson response
   * (.scratch/gui-localized-watson-hints phase 2). When set, the client renders {@code L10n.t(key)}
   * and {@link #message} is only the English fallback. Null for authored hints, whose {@link
   * #message} is already the localized case-content text. Internal (engine -> command); does not
   * cross the wire.
   */
  private final String messageKey;

  public WatsonHintResponseDTO(String message, boolean isActualHint) {
    this(null, message, isActualHint);
  }

  @JsonCreator
  public WatsonHintResponseDTO(
      @JsonProperty("messageKey") String messageKey,
      @JsonProperty("message") String message,
      @JsonProperty("actualHint") boolean isActualHint) {
    this.messageKey = messageKey;
    this.message = message;
    this.isActualHint = isActualHint;
  }

  public String getMessage() {
    return message;
  }

  public String getMessageKey() {
    return messageKey;
  }

  public boolean isActualHint() {
    return isActualHint;
  }
}

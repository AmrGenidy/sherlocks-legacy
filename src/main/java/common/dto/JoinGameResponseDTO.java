package common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;

public class JoinGameResponseDTO implements Serializable {
  @Serial
  private static final long serialVersionUID = 1L;
  private final boolean success;
  private final String message;
  private final String sessionId;

  @JsonCreator
  public JoinGameResponseDTO(
          @JsonProperty("success") boolean success,
          @JsonProperty("message") String message,
          @JsonProperty("sessionId") String sessionId) {
    this.success = success;
    this.message = message;
    this.sessionId = sessionId;
  }

  public boolean isSuccess() {
    return success;
  }

  public String getMessage() {
    return message;
  }

  public String getSessionId() {
    return sessionId;
  }

  @Override
  public String toString() {
    return "JoinGameResponseDTO{" +
            "success=" + success +
            ", message='" + message + '\'' +
            ", sessionId='" + sessionId + '\'' +
            '}';
  }
}
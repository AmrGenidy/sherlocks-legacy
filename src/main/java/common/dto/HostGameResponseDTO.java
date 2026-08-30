package common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;

public class HostGameResponseDTO implements Serializable {
  @Serial
  private static final long serialVersionUID = 1L;
  private final boolean success;
  private final String message;
  private final String gameCode;
  private final String sessionId;

  @JsonCreator
  public HostGameResponseDTO(
          @JsonProperty("success") boolean success,
          @JsonProperty("message") String message,
          @JsonProperty("gameCode") String gameCode,
          @JsonProperty("sessionId") String sessionId) {
    this.success = success;
    this.message = message;
    this.gameCode = gameCode;
    this.sessionId = sessionId;
  }

  public boolean isSuccess() {
    return success;
  }

  public String getMessage() {
    return message;
  }

  public String getGameCode() {
    return gameCode;
  }

  public String getSessionId() {
    return sessionId;
  }

  @Override
  public String toString() {
    return "HostGameResponseDTO{" +
            "success=" + success +
            ", message='" + message + '\'' +
            ", gameCode='" + gameCode + '\'' +
            ", sessionId='" + sessionId + '\'' +
            '}';
  }
}
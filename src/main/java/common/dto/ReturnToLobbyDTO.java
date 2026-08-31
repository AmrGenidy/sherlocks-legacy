package common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;

public class ReturnToLobbyDTO implements Serializable {
  @Serial
  private static final long serialVersionUID = 1L;
  private final String message;

  @JsonCreator
  public ReturnToLobbyDTO(@JsonProperty("message") String message) {
    this.message = message;
  }

  public String getMessage() {
    return message;
  }
}
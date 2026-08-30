package common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public class JoinPrivateGameRequestDTO implements Serializable {
  @Serial
  private static final long serialVersionUID = 1L;
  private final String gameCode;

  @JsonCreator
  public JoinPrivateGameRequestDTO(@JsonProperty("gameCode") String gameCode) {
    this.gameCode =
        common.WireLimits.requireLength(
            Objects.requireNonNull(gameCode), common.WireLimits.MAX_ID_LENGTH, "gameCode");
  }

  public String getGameCode() {
    return gameCode;
  }
}
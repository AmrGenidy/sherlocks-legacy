package common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public class PlayerNameChangedDTO implements Serializable {
  @Serial
  private static final long serialVersionUID = 1L;
  private final String playerId;
  private final String oldDisplayName;
  private final String newDisplayName;

  @JsonCreator
  public PlayerNameChangedDTO(
          @JsonProperty("playerId") String playerId,
          @JsonProperty("oldDisplayName") String oldDisplayName,
          @JsonProperty("newDisplayName") String newDisplayName) {
    this.playerId = Objects.requireNonNull(playerId);
    this.oldDisplayName = Objects.requireNonNull(oldDisplayName);
    this.newDisplayName = Objects.requireNonNull(newDisplayName);
  }

  public String getPlayerId() {
    return playerId;
  }

  public String getOldDisplayName() {
    return oldDisplayName;
  }

  public String getNewDisplayName() {
    return newDisplayName;
  }

  @Override
  public String toString() {
    return oldDisplayName + " is now known as " + newDisplayName + ".";
  }
}
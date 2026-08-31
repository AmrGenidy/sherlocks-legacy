package common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Broadcast when a player's chosen avatar changes, so every client can re-render that player's seat
 * with their new portrait. Mirrors {@link PlayerNameChangedDTO}.
 */
public class PlayerAvatarChangedDTO implements Serializable {
  @Serial private static final long serialVersionUID = 1L;
  private final String playerId;
  private final String oldAvatarId;
  private final String newAvatarId;

  @JsonCreator
  public PlayerAvatarChangedDTO(
      @JsonProperty("playerId") String playerId,
      @JsonProperty("oldAvatarId") String oldAvatarId,
      @JsonProperty("newAvatarId") String newAvatarId) {
    this.playerId = Objects.requireNonNull(playerId);
    this.oldAvatarId = oldAvatarId;
    this.newAvatarId = Objects.requireNonNull(newAvatarId);
  }

  public String getPlayerId() {
    return playerId;
  }

  public String getOldAvatarId() {
    return oldAvatarId;
  }

  public String getNewAvatarId() {
    return newAvatarId;
  }

  @Override
  public String toString() {
    return playerId + " chose avatar " + newAvatarId + ".";
  }
}

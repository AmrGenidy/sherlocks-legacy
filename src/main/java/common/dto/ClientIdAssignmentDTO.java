package common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public class ClientIdAssignmentDTO implements Serializable {
  @Serial
  private static final long serialVersionUID = 1L;
  private final String playerId;
  private final String assignedDisplayId;

  @JsonCreator
  public ClientIdAssignmentDTO(
          @JsonProperty("playerId") String playerId,
          @JsonProperty("assignedDisplayId") String assignedDisplayId) {
    this.playerId = Objects.requireNonNull(playerId);
    this.assignedDisplayId = Objects.requireNonNull(assignedDisplayId);
  }

  public String getPlayerId() {
    return playerId;
  }

  public String getAssignedDisplayId() {
    return assignedDisplayId;
  }

  @Override
  public String toString() {
    return "ClientIdAssignmentDTO{" +
            "playerId='" + playerId + '\'' +
            ", assignedDisplayId='" + assignedDisplayId + '\'' +
            '}';
  }
}
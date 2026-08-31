package common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class LobbyUpdateDTO implements Serializable {
  @Serial private static final long serialVersionUID = 1L;
  private final String message;
  private final List<String> playerDisplayIdsInLobbyOrGame;
  private final List<String> playerActualIdsInSession;
  // Chosen avatar preset ids, positionally paired with playerDisplayIdsInLobbyOrGame (same index =
  // same player). An element may be null when that player has not yet announced an avatar.
  private final List<String> playerAvatarIds;
  private final String hostPlayerId;
  private final boolean gameStarting;
  private final String caseInvitation;
  private final List<String> tasks;

  @JsonCreator
  public LobbyUpdateDTO(
      @JsonProperty("message") String message,
      // *** CORRECTED: @JsonProperty must match the getter's property name ***
      @JsonProperty("playerDisplayIdsInLobbyOrGame") List<String> playerDisplayIds,
      @JsonProperty("playerIdsInSession")
          List<String> playerActualIds, // Corrected from "playerActualIdsInSession"
      @JsonProperty("playerAvatarIds") List<String> playerAvatarIds,
      @JsonProperty("hostPlayerId") String hostPlayerId,
      @JsonProperty("gameStarting") boolean gameStarting,
      @JsonProperty("caseInvitation") String caseInvitation,
      @JsonProperty("tasks") List<String> tasks) {
    this.message = message;
    this.playerDisplayIdsInLobbyOrGame =
        playerDisplayIds != null ? new ArrayList<>(playerDisplayIds) : new ArrayList<>();
    this.playerActualIdsInSession =
        playerActualIds != null ? new ArrayList<>(playerActualIds) : new ArrayList<>();
    this.playerAvatarIds =
        playerAvatarIds != null ? new ArrayList<>(playerAvatarIds) : new ArrayList<>();
    this.hostPlayerId = hostPlayerId;
    this.gameStarting = gameStarting;
    this.caseInvitation = caseInvitation;
    this.tasks = tasks != null ? new ArrayList<>(tasks) : new ArrayList<>();
  }

  public String getMessage() {
    return message;
  }

  // Getters return ArrayList copies (not unmodifiable views): Jackson serializes through these,
  // and Collections$Unmodifiable* wrappers are not on the polymorphic allowlist (issue 01).
  public List<String> getPlayerDisplayIdsInLobbyOrGame() {
    return new ArrayList<>(playerDisplayIdsInLobbyOrGame);
  }

  public List<String> getPlayerIdsInSession() {
    return new ArrayList<>(playerActualIdsInSession);
  }

  public List<String> getPlayerAvatarIds() {
    return new ArrayList<>(playerAvatarIds);
  }

  public String getHostPlayerId() {
    return hostPlayerId;
  }

  public boolean isGameStarting() {
    return gameStarting;
  }

  public String getCaseInvitation() {
    return caseInvitation;
  }

  public List<String> getTasks() {
    return new ArrayList<>(tasks);
  }

  @Override
  public String toString() {
    return "LobbyUpdateDTO{"
        + "message='"
        + message
        + '\''
        + ", players="
        + playerDisplayIdsInLobbyOrGame
        + ", hostId="
        + hostPlayerId
        + ", gameStarting="
        + gameStarting
        + '}';
  }
}

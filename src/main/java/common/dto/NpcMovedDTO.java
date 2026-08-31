package common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public class NpcMovedDTO implements Serializable {
  @Serial
  private static final long serialVersionUID = 1L;
  private final String npcName;
  private final String oldRoomName;
  private final String newRoomName;

  @JsonCreator
  public NpcMovedDTO(
          @JsonProperty("npcName") String npcName,
          @JsonProperty("oldRoomName") String oldRoomName,
          @JsonProperty("newRoomName") String newRoomName) {
    this.npcName = Objects.requireNonNull(npcName, "NPC name cannot be null.");
    this.oldRoomName = oldRoomName;
    this.newRoomName = Objects.requireNonNull(newRoomName, "New room name cannot be null.");
  }

  public String getNpcName() {
    return npcName;
  }

  public String getOldRoomName() {
    return oldRoomName;
  }

  public String getNewRoomName() {
    return newRoomName;
  }

  @Override
  public String toString() {
    if (oldRoomName != null && !oldRoomName.equalsIgnoreCase(newRoomName)) {
      return npcName + " has moved from " + oldRoomName + " to " + newRoomName + ".";
    } else if (oldRoomName == null) {
      return npcName + " has appeared in " + newRoomName + ".";
    } else {
      return npcName + " is now in " + newRoomName + ".";
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NpcMovedDTO that = (NpcMovedDTO) o;
    return Objects.equals(npcName, that.npcName) &&
            Objects.equals(oldRoomName, that.oldRoomName) &&
            Objects.equals(newRoomName, that.newRoomName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(npcName, oldRoomName, newRoomName);
  }
}
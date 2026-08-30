package common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * The payload of an {@link common.commands.UpdateAvatarCommand}: the player's chosen avatar preset
 * id (see {@link common.PlayerAvatars}). Mirrors {@link UpdateDisplayNameRequestDTO}.
 *
 * <p>The constructor enforces only the wire <em>length</em> bound; semantic validation that the id
 * is a known preset (the allowlist) is the server's job when it processes the command, so a hostile
 * peer cannot inject an arbitrary string/path.
 */
public class UpdateAvatarRequestDTO implements Serializable {
  @Serial private static final long serialVersionUID = 1L;
  private final String avatarId;

  @JsonCreator
  public UpdateAvatarRequestDTO(@JsonProperty("avatarId") String avatarId) {
    this.avatarId =
        common.WireLimits.requireLength(
            Objects.requireNonNull(avatarId), common.WireLimits.MAX_AVATAR_ID_LENGTH, "avatarId");
  }

  public String getAvatarId() {
    return avatarId;
  }
}

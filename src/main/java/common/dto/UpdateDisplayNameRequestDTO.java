package common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public class UpdateDisplayNameRequestDTO implements Serializable {
  @Serial
  private static final long serialVersionUID = 1L;
  private final String newDisplayName;

  @JsonCreator
  public UpdateDisplayNameRequestDTO(@JsonProperty("newDisplayName") String newDisplayName) {
    this.newDisplayName =
        common.WireLimits.requireLength(
            Objects.requireNonNull(newDisplayName),
            common.WireLimits.MAX_DISPLAY_NAME_LENGTH,
            "newDisplayName");
  }

  public String getNewDisplayName() {
    return newDisplayName;
  }
}
package common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;

/**
 * Typed signal that the single-player session is leaving the current Case for case selection.
 * Replaces the GUI's TextMessage content-match on "Returning to case selection." (Phase 2,
 * unify-gamecontexts). Single-player only today; shaped as a wire DTO so the reflective round-trip
 * suite covers it and the web client can reuse it.
 */
public class ReturnToCaseSelectionDTO implements Serializable {
  @Serial private static final long serialVersionUID = 1L;

  private final String message;

  @JsonCreator
  public ReturnToCaseSelectionDTO(@JsonProperty("message") String message) {
    this.message = message;
  }

  public ReturnToCaseSelectionDTO() {
    this("Returning to case selection.");
  }

  public String getMessage() {
    return message;
  }
}

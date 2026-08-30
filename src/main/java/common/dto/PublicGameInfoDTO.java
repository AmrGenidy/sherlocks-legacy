package common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public class PublicGameInfoDTO implements Serializable {
  @Serial
  private static final long serialVersionUID = 1L;
  private final String hostPlayerDisplayId;
  private final String caseTitle;
  private final String sessionId;

  @JsonCreator
  public PublicGameInfoDTO(
          @JsonProperty("hostPlayerDisplayId") String hostPlayerDisplayId,
          @JsonProperty("caseTitle") String caseTitle,
          @JsonProperty("sessionId") String sessionId) {
    this.hostPlayerDisplayId = Objects.requireNonNull(hostPlayerDisplayId);
    this.caseTitle = Objects.requireNonNull(caseTitle);
    this.sessionId = Objects.requireNonNull(sessionId);
  }

  public String getHostPlayerDisplayId() {
    return hostPlayerDisplayId;
  }

  public String getCaseTitle() {
    return caseTitle;
  }

  public String getSessionId() {
    return sessionId;
  }

  @Override
  public String toString() {
    return "PublicGameInfoDTO{" +
            "host='" + hostPlayerDisplayId + '\'' +
            ", case='" + caseTitle + '\'' +
            ", sessionId='" + sessionId + '\'' +
            '}';
  }
}
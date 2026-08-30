// PASTE THIS, REPLACING THE ENTIRE CONTENTS of src/main/java/common/dto/HostGameRequestDTO.java

package common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public class HostGameRequestDTO implements Serializable {
  @Serial
  private static final long serialVersionUID = 1L;

  // MODIFIED: This is now the universal_title, not the localized title.
  private final String caseUniversalTitle;
  private final boolean isPublic;
  // NEW: Added a field for the language code.
  private final String languageCode;

  @JsonCreator
  public HostGameRequestDTO(
          @JsonProperty("caseUniversalTitle") String caseUniversalTitle,
          @JsonProperty("public") boolean isPublic,
          // NEW: Added to constructor
          @JsonProperty("languageCode") String languageCode) {
    this.caseUniversalTitle =
        common.WireLimits.requireLength(
            Objects.requireNonNull(caseUniversalTitle),
            common.WireLimits.MAX_CASE_TITLE_LENGTH,
            "caseUniversalTitle");
    this.isPublic = isPublic;
    this.languageCode =
        common.WireLimits.requireLength(
            Objects.requireNonNull(languageCode), // Language must be specified.
            common.WireLimits.MAX_LANGUAGE_CODE_LENGTH,
            "languageCode");
  }

  // MODIFIED: Renamed getter for clarity.
  public String getCaseUniversalTitle() {
    return caseUniversalTitle;
  }

  public boolean isPublic() {
    return isPublic;
  }

  // NEW: Getter for the language code.
  public String getLanguageCode() {
    return languageCode;
  }

  @Override
  public String toString() {
    return "HostGameRequestDTO{" +
            "caseUniversalTitle='" + caseUniversalTitle + '\'' +
            ", isPublic=" + isPublic +
            ", languageCode='" + languageCode + '\'' +
            '}';
  }
}
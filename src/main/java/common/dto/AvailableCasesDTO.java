// PASTE THIS, REPLACING THE ENTIRE CONTENTS of src/main/java/common/dto/AvailableCasesDTO.java

package common.dto;

import JsonDTO.CaseFile; // MODIFIED: Import the full CaseFile DTO
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class AvailableCasesDTO implements Serializable {
  @Serial
  private static final long serialVersionUID = 1L;

  // MODIFIED: The DTO now carries a list of the full, multilingual CaseFile objects.
  private final List<CaseFile> cases;

  @JsonCreator
  public AvailableCasesDTO(@JsonProperty("cases") List<CaseFile> cases) {
    this.cases = cases != null ? new ArrayList<>(cases) : new ArrayList<>();
  }

  public List<CaseFile> getCases() {
    return new ArrayList<>(cases);
  }

  @Override
  public String toString() {
    return "AvailableCasesDTO{" + "cases_count=" + cases.size() + '}';
  }
}
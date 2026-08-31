package common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

public class FinalExamRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private String requesterId;
    private String requesterDisplayName;

    // No-arg constructor for Jackson
    public FinalExamRequestDTO() {
    }

    @JsonCreator
    public FinalExamRequestDTO(
            @JsonProperty("requesterId") String requesterId,
            @JsonProperty("requesterDisplayName") String requesterDisplayName) {
        this.requesterId = requesterId;
        this.requesterDisplayName = requesterDisplayName;
    }

    public String getRequesterId() {
        return requesterId;
    }

    public void setRequesterId(String requesterId) {
        this.requesterId = requesterId;
    }

    public String getRequesterDisplayName() {
        return requesterDisplayName;
    }

    public void setRequesterDisplayName(String requesterDisplayName) {
        this.requesterDisplayName = requesterDisplayName;
    }
}

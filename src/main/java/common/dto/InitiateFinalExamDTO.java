package common.dto;

import java.io.Serializable;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO sent from the server to all clients to signal the start of the final exam.
 * It contains all the necessary data for the exam.
 */
public class InitiateFinalExamDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private FinalExamDTO finalExam;

    // No-arg constructor for Jackson
    public InitiateFinalExamDTO() {
    }

    @JsonCreator
    public InitiateFinalExamDTO(@JsonProperty("finalExam") FinalExamDTO finalExam) {
        this.finalExam = Objects.requireNonNull(finalExam, "FinalExamDTO cannot be null");
    }

    public FinalExamDTO getFinalExam() {
        return finalExam;
    }

    public void setFinalExam(FinalExamDTO finalExam) {
        this.finalExam = finalExam;
    }
}

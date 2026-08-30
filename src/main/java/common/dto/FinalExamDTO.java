package common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

public class FinalExamDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("questions")
    private List<FinalExamQuestionDTO> questions;

    public FinalExamDTO() {
    }

    public FinalExamDTO(List<FinalExamQuestionDTO> questions) {
        this.questions = questions;
    }

    public List<FinalExamQuestionDTO> getQuestions() {
        return questions;
    }

    public void setQuestions(List<FinalExamQuestionDTO> questions) {
        this.questions = questions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FinalExamDTO that = (FinalExamDTO) o;
        return Objects.equals(questions, that.questions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(questions);
    }

    @Override
    public String toString() {
        return "FinalExamDTO{" +
                "questions=" + questions +
                '}';
    }
}

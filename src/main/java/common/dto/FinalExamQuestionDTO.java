package common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

public class FinalExamQuestionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("question_prompt")
    private String questionPrompt;

    @JsonProperty("slots")
    private Map<String, FinalExamSlotDTO> slots;

    @JsonProperty("correct_combination")
    private Map<String, String> correctCombination;

    public FinalExamQuestionDTO() {
    }

    public FinalExamQuestionDTO(String questionPrompt, Map<String, FinalExamSlotDTO> slots, Map<String, String> correctCombination) {
        this.questionPrompt = questionPrompt;
        this.slots = slots;
        this.correctCombination = correctCombination;
    }

    public String getQuestionPrompt() {
        return questionPrompt;
    }

    public void setQuestionPrompt(String questionPrompt) {
        this.questionPrompt = questionPrompt;
    }

    public Map<String, FinalExamSlotDTO> getSlots() {
        return slots;
    }

    public void setSlots(Map<String, FinalExamSlotDTO> slots) {
        this.slots = slots;
    }

    public Map<String, String> getCorrectCombination() {
        return correctCombination;
    }

    public void setCorrectCombination(Map<String, String> correctCombination) {
        this.correctCombination = correctCombination;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FinalExamQuestionDTO that = (FinalExamQuestionDTO) o;
        return Objects.equals(questionPrompt, that.questionPrompt) &&
                Objects.equals(slots, that.slots) &&
                Objects.equals(correctCombination, that.correctCombination);
    }

    @Override
    public int hashCode() {
        return Objects.hash(questionPrompt, slots, correctCombination);
    }

    @Override
    public String toString() {
        return "FinalExamQuestionDTO{" +
                "questionPrompt='" + questionPrompt + '\'' +
                ", slots=" + slots +
                ", correctCombination=" + correctCombination +
                '}';
    }
}

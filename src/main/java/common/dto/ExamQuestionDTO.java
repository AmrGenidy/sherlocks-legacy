package common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

public class ExamQuestionDTO implements Serializable {
    private static final long serialVersionUID = 2L;

    private int questionIndex;
    private int totalQuestions;
    private String questionPrompt;
    private Map<String, FinalExamSlotDTO> slots;
    private Map<String, String> selectedAnswers; // <slotId, choiceId>

    // No-arg constructor for Jackson
    public ExamQuestionDTO() {
    }

    @JsonCreator
    public ExamQuestionDTO(
            @JsonProperty("questionIndex") int questionIndex,
            @JsonProperty("totalQuestions") int totalQuestions,
            @JsonProperty("questionPrompt") String questionPrompt,
            @JsonProperty("slots") Map<String, FinalExamSlotDTO> slots,
            @JsonProperty("selectedAnswers") Map<String, String> selectedAnswers) {
        this.questionIndex = questionIndex;
        this.totalQuestions = totalQuestions;
        this.questionPrompt = Objects.requireNonNull(questionPrompt, "Question prompt cannot be null");
        this.slots = Objects.requireNonNull(slots, "Slots cannot be null");
        this.selectedAnswers = Objects.requireNonNull(selectedAnswers, "Selected answers map cannot be null");
    }

    public int getQuestionIndex() {
        return questionIndex;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public String getQuestionPrompt() {
        return questionPrompt;
    }

    public Map<String, FinalExamSlotDTO> getSlots() {
        return slots;
    }

    public Map<String, String> getSelectedAnswers() {
        return selectedAnswers;
    }

    // Setters for Jackson
    public void setQuestionIndex(int questionIndex) {
        this.questionIndex = questionIndex;
    }

    public void setTotalQuestions(int totalQuestions) {
        this.totalQuestions = totalQuestions;
    }

    public void setQuestionPrompt(String questionPrompt) {
        this.questionPrompt = questionPrompt;
    }

    public void setSlots(Map<String, FinalExamSlotDTO> slots) {
        this.slots = slots;
    }

    public void setSelectedAnswers(Map<String, String> selectedAnswers) {
        this.selectedAnswers = selectedAnswers;
    }

    @Override
    public String toString() {
        return "ExamQuestionDTO{" +
                "questionIndex=" + questionIndex +
                ", totalQuestions=" + totalQuestions +
                ", questionPrompt='" + questionPrompt + '\'' +
                ", slots=" + slots +
                ", selectedAnswers=" + selectedAnswers +
                '}';
    }
}

package common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Objects;

public class FinalExamChoiceDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("choice_id")
    private String choiceId;

    @JsonProperty("choice_text")
    private String choiceText;

    public FinalExamChoiceDTO() {
    }

    public FinalExamChoiceDTO(String choiceId, String choiceText) {
        this.choiceId = choiceId;
        this.choiceText = choiceText;
    }

    public String getChoiceId() {
        return choiceId;
    }

    public void setChoiceId(String choiceId) {
        this.choiceId = choiceId;
    }

    public String getChoiceText() {
        return choiceText;
    }

    public void setChoiceText(String choiceText) {
        this.choiceText = choiceText;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FinalExamChoiceDTO that = (FinalExamChoiceDTO) o;
        return Objects.equals(choiceId, that.choiceId) && Objects.equals(choiceText, that.choiceText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(choiceId, choiceText);
    }

    @Override
    public String toString() {
        return "FinalExamChoiceDTO{" +
                "choiceId='" + choiceId + '\'' +
                ", choiceText='" + choiceText + '\'' +
                '}';
    }
}

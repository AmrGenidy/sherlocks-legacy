package common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

public class FinalExamSlotDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("slot_id")
    private String slotId;

    @JsonProperty("choices")
    private List<FinalExamChoiceDTO> choices;

    public FinalExamSlotDTO() {
    }

    public FinalExamSlotDTO(String slotId, List<FinalExamChoiceDTO> choices) {
        this.slotId = slotId;
        this.choices = choices;
    }

    public String getSlotId() {
        return slotId;
    }

    public void setSlotId(String slotId) {
        this.slotId = slotId;
    }

    public List<FinalExamChoiceDTO> getChoices() {
        return choices;
    }

    public void setChoices(List<FinalExamChoiceDTO> choices) {
        this.choices = choices;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FinalExamSlotDTO that = (FinalExamSlotDTO) o;
        return Objects.equals(slotId, that.slotId) && Objects.equals(choices, that.choices);
    }

    @Override
    public int hashCode() {
        return Objects.hash(slotId, choices);
    }

    @Override
    public String toString() {
        return "FinalExamSlotDTO{" +
                "slotId='" + slotId + '\'' +
                ", choices=" + choices +
                '}';
    }
}

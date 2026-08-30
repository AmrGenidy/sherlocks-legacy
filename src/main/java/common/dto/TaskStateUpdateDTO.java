package common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;

public class TaskStateUpdateDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final int taskIndex;
    private final boolean isCompleted;

    @JsonCreator
    public TaskStateUpdateDTO(
            @JsonProperty("taskIndex") int taskIndex,
            @JsonProperty("isCompleted") boolean isCompleted) {
        this.taskIndex = taskIndex;
        this.isCompleted = isCompleted;
    }

    public int getTaskIndex() {
        return taskIndex;
    }

    public boolean getIsCompleted() {
        return isCompleted;
    }
}

package common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

public class CommandCooldownUpdateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String commandType; // "combine" or "contradict"
    private long cooldownUntil; // Epoch millis
    private long remainingSeconds; // Convenience field for display

    @JsonCreator
    public CommandCooldownUpdateDTO(
            @JsonProperty("commandType") String commandType,
            @JsonProperty("cooldownUntil") long cooldownUntil,
            @JsonProperty("remainingSeconds") long remainingSeconds) {
        this.commandType = commandType;
        this.cooldownUntil = cooldownUntil;
        this.remainingSeconds = remainingSeconds;
    }

    public String getCommandType() {
        return commandType;
    }

    public long getCooldownUntil() {
        return cooldownUntil;
    }

    public long getRemainingSeconds() {
        return remainingSeconds;
    }
}

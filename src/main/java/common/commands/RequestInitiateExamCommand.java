package common.commands;

import common.interfaces.GameActionContext;
import java.io.Serializable;

public class RequestInitiateExamCommand implements Command, Serializable {
    private static final long serialVersionUID = 1L;
    private String playerId;

    public RequestInitiateExamCommand() {
    }

    @Override
    public void execute(GameActionContext context) {
        context.processRequestInitiateExam(getPlayerId());
    }

    @Override
    public String getDescription() {
        return "Requests the host to start the final exam.";
    }

    @Override
    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    @Override
    public String getPlayerId() {
        return playerId;
    }
}

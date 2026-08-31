package common.commands.pinboard;

import common.commands.Command;
import common.interfaces.GameActionContext;

public class RequestPinboardStateCommand implements Command {
    private static final long serialVersionUID = 1L;
    private String playerId;

    public RequestPinboardStateCommand() {
    }

    @Override
    public void execute(GameActionContext context) {
        // Not used directly
    }

    @Override
    public String getDescription() {
        return "Requests full pinboard state";
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

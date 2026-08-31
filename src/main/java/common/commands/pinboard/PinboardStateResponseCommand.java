package common.commands.pinboard;

import common.commands.Command;
import common.dto.pinboard.PinboardStateDTO;
import common.interfaces.GameActionContext;

public class PinboardStateResponseCommand implements Command {
    private static final long serialVersionUID = 1L;

    private PinboardStateDTO state;
    private String playerId;

    // Default constructor for deserialization
    public PinboardStateResponseCommand() {
    }

    public PinboardStateResponseCommand(PinboardStateDTO state) {
        this.state = state;
    }

    public PinboardStateDTO getState() {
        return state;
    }

    @Override
    public void execute(GameActionContext context) {
        // Not used directly
    }

    @Override
    public String getDescription() {
        return "Response with pinboard state";
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

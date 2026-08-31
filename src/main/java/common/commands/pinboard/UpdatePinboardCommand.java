package common.commands.pinboard;

import common.commands.Command;
import common.dto.pinboard.PinboardUpdateDTO;
import common.interfaces.GameActionContext;

public class UpdatePinboardCommand implements Command {
    private static final long serialVersionUID = 1L;

    private PinboardUpdateDTO update;
    private String playerId;

    // Default constructor for deserialization
    public UpdatePinboardCommand() {
    }

    public UpdatePinboardCommand(PinboardUpdateDTO update) {
        this.update = update;
    }

    public PinboardUpdateDTO getUpdate() {
        return update;
    }

    @Override
    public void execute(GameActionContext context) {
        // Not used directly by GameContext in this architecture
    }

    @Override
    public String getDescription() {
        return "Updates the pinboard state";
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

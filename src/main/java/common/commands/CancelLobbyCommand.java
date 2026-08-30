package common.commands;

import common.interfaces.GameActionContext;
import java.io.Serial;

/**
 * A command specifically for a player to cancel their participation in a pre-game lobby.
 * For a host, this cancels the lobby. For a guest, this leaves the lobby.
 */
public class CancelLobbyCommand extends BaseCommand {
    @Serial
    private static final long serialVersionUID = 1L;

    public CancelLobbyCommand() {
        super(false); // Does not require the case to be started.
    }

    @Override
    protected void executeCommandLogic(GameActionContext context) {
        // The server-side logic will be handled by GameSession. This command
        // serves as a distinct trigger for that logic.
        context.handlePlayerCancelLobby(getPlayerId());
    }

    @Override
    public String getDescription() {
        return "Cancels or leaves a pre-game lobby, returning to the main menu.";
    }
}
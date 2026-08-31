package engine;

import Core.Room;
import Core.Suspect;
import common.commands.Command;
import common.dto.ExamResultDTO;
import common.interfaces.GameActionContext;
import java.io.Serializable;
import java.util.List;

/**
 * A uniform test view over one started {@link GameActionContext} implementation.
 *
 * <p>The engine test suite drives both {@code GameContextServer} and {@code
 * GameContextSinglePlayer} through this single surface so the same assertions form one behavioural
 * contract. Anything that genuinely differs between the two contexts is asserted per-implementation
 * and documented as a divergence (see {@code .scratch/engine-test-suite/issues}); it is never
 * silently smoothed over here.
 */
public abstract class ContextHarness {

  /** Human-readable implementation label, used in parameterized test names. */
  public abstract String label();

  public abstract GameActionContext context();

  /** The single (host / local) detective's player id. */
  public abstract String playerId();

  /** Current shared insight-token balance. */
  public abstract int tokens();

  /**
   * The most recent final-exam result the context produced, or {@code null} if no exam has been
   * scored yet. Single-player exposes this directly; the server only broadcasts it, so the server
   * harness reconstructs it from what was sent to the player.
   */
  public abstract ExamResultDTO lastExamResult();

  /** Everything the engine has emitted to this player so far, in order. */
  public abstract List<Serializable> playerResponses();

  // --- Shared convenience helpers -----------------------------------------

  /** Executes a command as the harness player against the context. */
  public final void execute(Command command) {
    command.setPlayerId(playerId());
    command.execute(context());
  }

  public final Room currentRoom() {
    return context().getCurrentRoomForPlayer(playerId());
  }

  public final Suspect suspect(String nameOrId) {
    return context().getAllSuspects().stream()
        .filter(s -> s.getName().equalsIgnoreCase(nameOrId) || s.getId().equalsIgnoreCase(nameOrId))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No suspect '" + nameOrId + "' loaded."));
  }

  /**
   * Suspects are placed in random rooms by the extractor (and the server avoids the starting room),
   * so co-locate the named suspect with the player to make interrogation tests deterministic.
   */
  public final void bringSuspectToPlayer(String nameOrId) {
    suspect(nameOrId).setCurrentRoom(currentRoom());
  }
}

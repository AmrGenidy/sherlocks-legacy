package engine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import singleplayer.GameContextSinglePlayer;

/**
 * A {@link GameContextSinglePlayer} that records every response the engine addresses to the player
 * instead of printing it to {@code System.out}.
 *
 * <p>This is the single-player analogue of {@link RecordingClientSession}. It exists chiefly so the
 * final {@code ExamResultDTO} is observable: the production single-player context nulls its cached
 * result immediately after scoring (only the stdout text survives), so {@code getLastResultDTO()}
 * cannot be read back after the exam concludes. Recording the emitted DTOs side-steps that and
 * keeps the test output quiet. {@code sendResponseToPlayer} is pure output in this context, so
 * suppressing the print changes no game state.
 */
public class RecordingSinglePlayerContext extends GameContextSinglePlayer {

  private final List<Serializable> sent = new ArrayList<>();

  @Override
  public void sendResponseToPlayer(String playerId, Serializable responseDto) {
    if (responseDto != null) {
      sent.add(responseDto);
    }
  }

  /** Everything the engine emitted to the player, in order. */
  public List<Serializable> sent() {
    return sent;
  }
}

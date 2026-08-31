package engine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import server.ClientSession;

/**
 * A {@link ClientSession} test double that records everything the server context tries to send to a
 * player instead of writing it to a (non-existent) socket.
 *
 * <p>{@code ClientSession}'s constructor never dereferences its channel/server arguments, so
 * passing {@code (null, null)} yields a usable session with a real generated {@code playerId}. We
 * override {@link #send(Serializable)} so the real network write path ({@code
 * server.registerForWrite}) is never touched.
 */
public class RecordingClientSession extends ClientSession {

  private final List<Serializable> sent = new ArrayList<>();

  public RecordingClientSession() {
    super(null, null);
  }

  @Override
  public void send(Serializable dto) {
    sent.add(dto);
  }

  /** All DTOs/messages the server context addressed to this player, in order. */
  public List<Serializable> sent() {
    return sent;
  }
}

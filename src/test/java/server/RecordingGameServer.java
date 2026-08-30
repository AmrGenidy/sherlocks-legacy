package server;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link GameServer} test double that captures everything {@link ClientSession} routes to {@link
 * #processClientMessage(ClientSession, Object)} and neutralises the selector-write callbacks (there
 * is no real selector or socket behind the framing tests).
 *
 * <p>The {@code GameServer(int)} constructor neither opens a selector nor binds a port, so it is
 * safe to instantiate directly; only {@code startServer()} touches the network.
 */
final class RecordingGameServer extends GameServer {

  private final List<Object> received = new ArrayList<>();

  RecordingGameServer() {
    super(0);
  }

  @Override
  public void processClientMessage(ClientSession sender, Object message) {
    received.add(message);
  }

  @Override
  public void registerForWrite(ClientSession client) {
    // no selector behind these tests
  }

  @Override
  public void unregisterForWrite(ClientSession client) {
    // no selector behind these tests
  }

  /** Every object the session fully decoded and handed up for routing, in order. */
  List<Object> received() {
    return received;
  }
}

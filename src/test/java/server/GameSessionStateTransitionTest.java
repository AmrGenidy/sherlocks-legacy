package server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import common.commands.MoveCommand;
import common.commands.pinboard.PinboardStateResponseCommand;
import common.dto.JoinGameResponseDTO;
import common.dto.TextMessage;
import engine.EngineFixtures;
import engine.RecordingClientSession;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * State-machine contract for {@link GameSession} (.scratch/server-tests/issues/01): the documented
 * lifecycle LOADING → WAITING_FOR_PLAYERS → IN_LOBBY_AWAITING_START → ACTIVE → ENDED_*, plus the
 * disconnect-driven transitions and the command-gating guard.
 *
 * <p>Sessions are built directly against the deterministic {@code testcases} sapphire fixture (see
 * {@link EngineFixtures}) with {@link RecordingClientSession} players, so the session's own state
 * transitions are observed without a socket, selector, or the real {@code cases/} directory.
 */
public class GameSessionStateTransitionTest {

  private GameServer server;
  private GameSessionManager manager;
  private final List<GameSession> sessions = new ArrayList<>();

  @Before
  public void setUp() {
    server = new GameServer(0);
    manager = server.sessionManager;
  }

  @After
  public void tearDown() {
    // handlePlayerDisconnect(host) always stops the LAN broadcaster daemon (idempotent).
    for (GameSession session : sessions) {
      try {
        if (session.getPlayer1() != null) {
          session.handlePlayerDisconnect(session.getPlayer1());
        }
      } catch (RuntimeException ignored) {
        // best-effort cleanup
      }
    }
  }

  private GameSession newPrivateSession(RecordingClientSession host) {
    GameSession session =
        new GameSession(EngineFixtures.sapphire(), host, false, "TEST1", manager, server);
    sessions.add(session);
    return session;
  }

  private static JoinGameResponseDTO lastJoinResponse(RecordingClientSession client) {
    JoinGameResponseDTO last = null;
    for (Serializable s : client.sent()) {
      if (s instanceof JoinGameResponseDTO dto) {
        last = dto;
      }
    }
    return last;
  }

  private static boolean sentErrorContaining(RecordingClientSession client, String fragment) {
    for (Serializable s : client.sent()) {
      if (s instanceof TextMessage tm && tm.isError() && tm.getText().contains(fragment)) {
        return true;
      }
    }
    return false;
  }

  private static boolean sent(RecordingClientSession client, Class<?> type) {
    for (Serializable s : client.sent()) {
      if (type.isInstance(s)) {
        return true;
      }
    }
    return false;
  }

  @Test
  public void freshSession_loadsIntoWaitingForPlayers() {
    RecordingClientSession host = new RecordingClientSession();

    GameSession session = newPrivateSession(host);

    assertEquals(GameSessionState.WAITING_FOR_PLAYERS, session.getState());
    assertEquals(session, host.getAssociatedGameSession());
    assertFalse(session.isFull());
  }

  @Test
  public void secondPlayerJoins_transitionsToInLobbyAwaitingStart() {
    GameSession session = newPrivateSession(new RecordingClientSession());
    RecordingClientSession guest = new RecordingClientSession();

    boolean joined = session.addPlayer(guest);

    assertTrue(joined);
    assertTrue(session.isFull());
    assertEquals(GameSessionState.IN_LOBBY_AWAITING_START, session.getState());
    assertEquals(session, guest.getAssociatedGameSession());
  }

  @Test
  public void thirdPlayer_rejectedOnceFull() {
    GameSession session = newPrivateSession(new RecordingClientSession());
    session.addPlayer(new RecordingClientSession());
    RecordingClientSession third = new RecordingClientSession();

    boolean joined = session.addPlayer(third);

    assertFalse(joined);
    JoinGameResponseDTO resp = lastJoinResponse(third);
    assertFalse("third player is told the session is full", resp.isSuccess());
  }

  @Test
  public void startingCase_movesToActiveAndSyncsPinboard() {
    GameSession session = newPrivateSession(new RecordingClientSession());
    RecordingClientSession guest = new RecordingClientSession();
    session.addPlayer(guest);

    session.setSessionState(GameSessionState.ACTIVE);

    assertEquals(GameSessionState.ACTIVE, session.getState());
    assertTrue(
        "entering ACTIVE broadcasts the pinboard so clients are synced",
        sent(guest, PinboardStateResponseCommand.class));
  }

  @Test
  public void setSessionState_canReachEndedNormal() {
    GameSession session = newPrivateSession(new RecordingClientSession());

    session.setSessionState(GameSessionState.ENDED_NORMAL);

    assertEquals(GameSessionState.ENDED_NORMAL, session.getState());
  }

  @Test
  public void endSession_marksEndedAbandoned() {
    GameSession session = newPrivateSession(new RecordingClientSession());

    session.endSession("player quit");

    assertEquals(GameSessionState.ENDED_ABANDONED, session.getState());
  }

  @Test
  public void guestDisconnectFromLobby_returnsSessionToWaiting() {
    GameSession session = newPrivateSession(new RecordingClientSession());
    RecordingClientSession guest = new RecordingClientSession();
    session.addPlayer(guest);

    session.handlePlayerDisconnect(guest);

    assertEquals(GameSessionState.WAITING_FOR_PLAYERS, session.getState());
    assertFalse(session.isFull());
    assertNull("disconnected guest is unbound from the session", guest.getAssociatedGameSession());
  }

  @Test
  public void hostDisconnect_unbindsHostAndTearsDown() {
    RecordingClientSession host = new RecordingClientSession();
    GameSession session = newPrivateSession(host);

    session.handlePlayerDisconnect(host);

    assertNull("host is unbound when it disconnects", host.getAssociatedGameSession());
  }

  @Test
  public void commandWhileWaiting_isGatedWithAnError() {
    RecordingClientSession host = new RecordingClientSession();
    GameSession session = newPrivateSession(host);

    // A movement command is not in the lobby's allow-list (only start/exit/cancel are).
    session.processCommand(new MoveCommand("north"), host.getPlayerId());

    assertTrue(
        "disallowed command in a pre-game state is rejected with an error message",
        sentErrorContaining(host, "not allowed"));
  }
}

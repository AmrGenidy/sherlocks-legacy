package server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import JsonDTO.CaseFile;
import common.dto.HostGameResponseDTO;
import common.dto.JoinGameResponseDTO;
import common.dto.PublicGameInfoDTO;
import engine.RecordingClientSession;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Lobby-lifecycle contract for {@link GameSessionManager} (.scratch/server-tests/issues/01 & 02):
 * host public / host private with a generated game code, join by code, join public, cancel, the
 * full-session rejection, and the single-session-per-server constraint.
 *
 * <p>Cases come from the real {@code "cases"} directory the manager loads in its constructor (the
 * peer-hosted desktop model). Clients are {@link RecordingClientSession} doubles, so no socket or
 * selector is touched. Sessions spin up a daemon {@link LanGameBroadcaster}; {@link #tearDown()}
 * cancels any lobby still open so those threads stop promptly.
 */
public class GameSessionManagerLifecycleTest {

  private GameServer server;
  private GameSessionManager manager;
  private String caseTitle;
  private final List<RecordingClientSession> hosts = new ArrayList<>();

  @Before
  public void setUp() {
    server = new GameServer(0); // constructor neither binds a port nor opens a selector
    manager = server.sessionManager;
    List<CaseFile> cases = manager.getAvailableCases();
    assertFalse("Test depends on at least one loadable case in 'cases/'", cases.isEmpty());
    caseTitle = cases.get(0).getUniversalTitle();
  }

  @After
  public void tearDown() {
    // Stop any lingering LAN broadcaster daemons by cancelling lobbies still attached to a host.
    for (RecordingClientSession host : hosts) {
      GameSession session = host.getAssociatedGameSession();
      if (session != null) {
        try {
          session.playerCancelsLobby(host.getPlayerId());
        } catch (RuntimeException ignored) {
          // best-effort cleanup
        }
      }
    }
  }

  private RecordingClientSession newClient() {
    RecordingClientSession c = new RecordingClientSession();
    hosts.add(c);
    return c;
  }

  private HostGameResponseDTO host(RecordingClientSession client, boolean isPublic) {
    return manager.createGame(client, caseTitle, isPublic, "en");
  }

  @Test
  public void hostPublicGame_createsWaitingPublicLobby() {
    RecordingClientSession host = newClient();

    HostGameResponseDTO resp = host(host, true);

    assertTrue("public host should succeed: " + resp.getMessage(), resp.isSuccess());
    assertNull("public games have no join code", resp.getGameCode());
    assertNotNull(resp.getSessionId());

    GameSession session = host.getAssociatedGameSession();
    assertNotNull("host is bound to its new session", session);
    assertEquals(GameSessionState.WAITING_FOR_PLAYERS, session.getState());

    List<PublicGameInfoDTO> lobbies = manager.getPublicLobbiesInfo();
    assertEquals(1, lobbies.size());
    assertEquals(resp.getSessionId(), lobbies.get(0).getSessionId());
    assertEquals(host.getDisplayId(), lobbies.get(0).getHostPlayerDisplayId());
  }

  @Test
  public void hostPrivateGame_generatesCodeAndIsNotPubliclyListed() {
    RecordingClientSession host = newClient();

    HostGameResponseDTO resp = host(host, false);

    assertTrue("private host should succeed: " + resp.getMessage(), resp.isSuccess());
    assertNotNull("private games get a join code", resp.getGameCode());
    assertEquals(5, resp.getGameCode().length());
    assertEquals(resp.getGameCode(), resp.getGameCode().toUpperCase());
    assertTrue(
        "private lobby must not appear in the public list",
        manager.getPublicLobbiesInfo().isEmpty());
  }

  @Test
  public void joinPrivateByCode_succeedsAndFillsSession() {
    RecordingClientSession host = newClient();
    String code = host(host, false).getGameCode();
    RecordingClientSession guest = newClient();

    JoinGameResponseDTO resp = manager.joinPrivateGame(guest, code);

    assertTrue("guest should join by code: " + resp.getMessage(), resp.isSuccess());
    GameSession session = host.getAssociatedGameSession();
    assertEquals(session.getSessionId(), resp.getSessionId());
    assertSame("guest is bound to the host's session", session, guest.getAssociatedGameSession());
    assertTrue(session.isFull());
    assertEquals(GameSessionState.IN_LOBBY_AWAITING_START, session.getState());
  }

  @Test
  public void joinPrivateByCode_isCaseInsensitive() {
    RecordingClientSession host = newClient();
    String code = host(host, false).getGameCode();
    RecordingClientSession guest = newClient();

    JoinGameResponseDTO resp = manager.joinPrivateGame(guest, code.toLowerCase());

    assertTrue("join code matching ignores case: " + resp.getMessage(), resp.isSuccess());
  }

  @Test
  public void joinPublicBySessionId_succeedsAndDelistsLobby() {
    RecordingClientSession host = newClient();
    String sessionId = host(host, true).getSessionId();
    RecordingClientSession guest = newClient();

    JoinGameResponseDTO resp = manager.joinPublicGame(guest, sessionId);

    assertTrue("guest should join public game: " + resp.getMessage(), resp.isSuccess());
    assertTrue(
        "a full lobby is removed from the public list", manager.getPublicLobbiesInfo().isEmpty());
    assertTrue(host.getAssociatedGameSession().isFull());
  }

  @Test
  public void cancelLobby_byHost_removesSessionAndUnbindsHost() {
    RecordingClientSession host = newClient();
    String sessionId = host(host, true).getSessionId();
    GameSession session = host.getAssociatedGameSession();

    session.playerCancelsLobby(host.getPlayerId());

    assertNull("host is unbound after cancelling", host.getAssociatedGameSession());
    assertTrue("cancelled lobby leaves the public list", manager.getPublicLobbiesInfo().isEmpty());

    // The session is gone from the manager: a late join attempt is rejected, not served.
    JoinGameResponseDTO late = manager.joinPublicGame(newClient(), sessionId);
    assertFalse(late.isSuccess());
  }

  @Test
  public void joinFullSession_isRejected() {
    RecordingClientSession host = newClient();
    String code = host(host, false).getGameCode();
    manager.joinPrivateGame(newClient(), code); // fills the session (now IN_LOBBY_AWAITING_START)

    JoinGameResponseDTO third = manager.joinPrivateGame(newClient(), code);

    assertFalse("a third player cannot join a full session", third.isSuccess());
    assertNotNull(third.getMessage());
  }

  @Test
  public void singleSessionConstraint_blocksASecondHostedGame() {
    host(newClient(), true);

    HostGameResponseDTO second = host(newClient(), true);

    assertFalse("only one game may be hosted per server instance", second.isSuccess());
    assertTrue(second.getMessage().toLowerCase().contains("already"));
  }

  @Test
  public void joinUnknownPrivateCode_isRejectedCleanly() {
    JoinGameResponseDTO resp = manager.joinPrivateGame(newClient(), "ZZZZZ");

    assertFalse(resp.isSuccess());
    assertTrue(resp.getMessage().contains("not found"));
  }

  @Test
  public void joinUnknownPublicSession_isRejectedCleanly() {
    JoinGameResponseDTO resp = manager.joinPublicGame(newClient(), "no-such-session-id");

    assertFalse(resp.isSuccess());
    assertNotNull(resp.getMessage());
  }

  /**
   * Roster-integrity under contention: many clients race to join the same private session. The
   * managerLock + sessionLock pairing must admit exactly one second player and leave the session in
   * a consistent (full, single-guest) state — no double-occupancy, no lost guest. (This pins the
   * locking as written; the latent lock-ordering inversion that needs a code change is tracked in
   * issue 04.)
   */
  @Test
  public void concurrentJoins_admitExactlyOneSecondPlayer() throws InterruptedException {
    RecordingClientSession host = newClient();
    String code = host(host, false).getGameCode();

    int contenders = 16;
    ExecutorService pool = Executors.newFixedThreadPool(contenders);
    CountDownLatch startGun = new CountDownLatch(1);
    AtomicInteger successes = new AtomicInteger();
    List<RecordingClientSession> guests = new ArrayList<>();
    try {
      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < contenders; i++) {
        RecordingClientSession guest = newClient();
        guests.add(guest);
        futures.add(
            pool.submit(
                () -> {
                  startGun.await();
                  if (manager.joinPrivateGame(guest, code).isSuccess()) {
                    successes.incrementAndGet();
                  }
                  return null;
                }));
      }
      startGun.countDown();
      for (Future<?> f : futures) {
        try {
          f.get();
        } catch (ExecutionException e) {
          throw new AssertionError("a join task threw", e.getCause());
        }
      }
    } finally {
      pool.shutdownNow();
    }

    assertEquals("exactly one contender wins the second seat", 1, successes.get());
    GameSession session = host.getAssociatedGameSession();
    assertTrue("the winning session is full", session.isFull());
    long bound = guests.stream().filter(g -> session.equals(g.getAssociatedGameSession())).count();
    assertEquals("exactly one guest is bound to the session", 1L, bound);
    assertEquals(GameSessionState.IN_LOBBY_AWAITING_START, session.getState());
  }
}

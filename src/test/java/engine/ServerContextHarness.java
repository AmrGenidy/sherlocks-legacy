package engine;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import JsonDTO.LocalizedCaseFile;
import common.dto.ExamResultDTO;
import common.interfaces.GameActionContext;
import extractors.BuildingExtractor;
import extractors.GameObjectExtractor;
import extractors.SuspectExtractor;
import java.io.Serializable;
import server.GameContextServer;
import server.GameSession;

/**
 * {@link ContextHarness} backed by the multiplayer server context, run as a single (host-only)
 * session.
 *
 * <p>{@code GameContextServer} talks to its players through a {@link GameSession}; we substitute a
 * Mockito mock that routes {@code getClientSessionById(hostId)} to a {@link RecordingClientSession}
 * and forwards {@code broadcast(...)} to that same recording session (honouring the exclude id), so
 * everything the session emits to the host is observable. The case is then loaded exactly as {@code
 * GameSession.loadCaseDataIntoContext} does.
 */
public class ServerContextHarness extends ContextHarness {

  private final GameContextServer context;
  private final GameSession session;
  private final RecordingClientSession host;
  private final String playerId;

  private ServerContextHarness(
      GameContextServer context,
      GameSession session,
      RecordingClientSession host,
      String playerId) {
    this.context = context;
    this.session = session;
    this.host = host;
    this.playerId = playerId;
  }

  public static ServerContextHarness start(LocalizedCaseFile caseFile) {
    ServerContextHarness h = load(caseFile);
    h.context.setCaseStarted(true);
    return h;
  }

  public static ServerContextHarness startUnstarted(LocalizedCaseFile caseFile) {
    return load(caseFile);
  }

  private static ServerContextHarness load(LocalizedCaseFile caseFile) {
    RecordingClientSession host = new RecordingClientSession();
    String hostId = host.getPlayerId();

    GameSession session = mock(GameSession.class);
    lenient().when(session.getClientSessionById(hostId)).thenReturn(host);
    // Forward session broadcasts to the host (respecting the exclude id) so broadcast-only
    // outputs such as the ExamResultDTO are observable in tests.
    lenient()
        .doAnswer(
            invocation -> {
              Serializable dto = invocation.getArgument(0);
              String exclude = invocation.getArgument(1);
              if (exclude == null || !exclude.equals(hostId)) {
                host.send(dto);
              }
              return null;
            })
        .when(session)
        .broadcast(any(), any());

    GameContextServer context = new GameContextServer(session, caseFile, hostId, null);
    context.resetForNewCaseLoad();
    try {
      if (!BuildingExtractor.loadBuilding(caseFile, context)) {
        throw new IllegalStateException("BuildingExtractor failed for " + caseFile.getTitle());
      }
      GameObjectExtractor.loadObjects(caseFile, context);
      SuspectExtractor.loadSuspects(caseFile, context);
    } catch (SuspectExtractor.NoValidRoomsException e) {
      throw new IllegalStateException("Suspect placement failed for " + caseFile.getTitle(), e);
    }
    context.initializePlayerStartingState();
    return new ServerContextHarness(context, session, host, hostId);
  }

  @Override
  public String label() {
    return "Server";
  }

  @Override
  public GameActionContext context() {
    return context;
  }

  @Override
  public String playerId() {
    return playerId;
  }

  @Override
  public int tokens() {
    return context.getSharedInsightTokens();
  }

  @Override
  public ExamResultDTO lastExamResult() {
    ExamResultDTO last = null;
    for (Serializable dto : host.sent()) {
      if (dto instanceof ExamResultDTO result) {
        last = result;
      }
    }
    return last;
  }

  @Override
  public java.util.List<Serializable> playerResponses() {
    return host.sent();
  }

  /** Exposes the mock session so server-specific tests can verify broadcasts. */
  public GameSession session() {
    return session;
  }

  /** Everything the context sent directly to the host player. */
  public RecordingClientSession host() {
    return host;
  }
}

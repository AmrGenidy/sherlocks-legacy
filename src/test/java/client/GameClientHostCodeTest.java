package client;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import common.dto.HostGameResponseDTO;
import java.lang.reflect.Method;
import org.junit.Test;

/**
 * Regression for the private-host join-code surfacing bug (.scratch/private-join-code). The server
 * generates and returns the code in {@link HostGameResponseDTO} (pinned by {@code
 * GameSessionManagerLifecycleTest}); this pins the CLIENT half — that the code reaches the lobby
 * listener (which drives the GUI {@code JoinCodePlate}) instead of being dropped as {@code null}.
 *
 * <p>Drives the real {@code processServerMessage} dispatch and the real {@code
 * displayMenuOrPromptForCurrentState} state-render, with no socket, and verifies what the listener
 * receives.
 */
public class GameClientHostCodeTest {

  private static GameClient client(GameClientStateListener listener) {
    GameClient c =
        new GameClient("localhost", 0, line -> {}, GameClient.LaunchMode.NORMAL, null);
    c.setListener(listener);
    return c;
  }

  private static void dispatch(GameClient c, Object message) throws Exception {
    Method m = GameClient.class.getDeclaredMethod("processServerMessage", Object.class);
    m.setAccessible(true);
    m.invoke(c, message);
  }

  private static void renderCurrentState(GameClient c) throws Exception {
    Method m = GameClient.class.getDeclaredMethod("displayMenuOrPromptForCurrentState");
    m.setAccessible(true);
    m.invoke(c);
  }

  @Test
  public void privateHostResponse_surfacesTheCodeToTheLobbyScreen() throws Exception {
    GameClientStateListener listener = mock(GameClientStateListener.class);
    GameClient c = client(listener);

    dispatch(c, new HostGameResponseDTO(true, "Game hosted. Waiting…", "AB7K9", "session-1"));
    renderCurrentState(c);

    // The host's lobby/waiting screen must receive the real join code, never null.
    verify(listener).onHostingLobby("AB7K9");
  }

  @Test
  public void publicHostResponse_surfacesNoCode() throws Exception {
    GameClientStateListener listener = mock(GameClientStateListener.class);
    GameClient c = client(listener);

    dispatch(c, new HostGameResponseDTO(true, "Game hosted. Waiting…", null, "session-2"));
    renderCurrentState(c);

    // Public games are joined from the public list — the lobby shows no code.
    verify(listener).onHostingLobby(null);
  }
}

package server;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import common.dto.ChatMessage;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * Chat sender identity is server state (security-pass issue 05): whatever display name a client
 * writes into a {@link ChatMessage}, the broadcast carries the sending connection's real display
 * name, stamped by the server.
 */
public class ChatMessageStampingTest {

  @Test
  public void spoofedSenderNameIsReplacedWithTheConnectionsRealName() throws Exception {
    GameServer server = new GameServer(0); // does not bind; only startServer() touches the network
    ClientSession sender =
        new ClientSession(ScriptedSocketChannel.greedy(new byte[0]), server);
    sender.setDisplayId("RealName");

    GameSession session = mock(GameSession.class);
    when(session.getState()).thenReturn(GameSessionState.ACTIVE);
    sender.setAssociatedGameSession(session);

    server.processClientMessage(sender, new ChatMessage("TheOtherPlayer", "hello", 12345L));

    ArgumentCaptor<ChatMessage> broadcastMessage = ArgumentCaptor.forClass(ChatMessage.class);
    verify(session).processChatMessage(broadcastMessage.capture());
    assertEquals(
        "the wire sender name must be replaced with the connection's identity",
        "RealName",
        broadcastMessage.getValue().getSenderDisplayId());
    assertEquals("hello", broadcastMessage.getValue().getText());
  }
}

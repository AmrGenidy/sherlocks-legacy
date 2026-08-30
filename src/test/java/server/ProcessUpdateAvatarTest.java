package server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import common.dto.PlayerAvatarChangedDTO;
import engine.ServerContextHarness;
import java.io.Serializable;
import org.junit.Test;

/**
 * {@code GameContextServer.processUpdateAvatar}: a valid preset id is stored on the player's
 * session and broadcast to peers; an id outside the {@link common.PlayerAvatars} allowlist is
 * rejected (dropped, no change, no broadcast) so a hostile peer cannot inject an arbitrary
 * string/path.
 */
public class ProcessUpdateAvatarTest {

  @Test
  public void validAvatarIsStoredAndBroadcast() {
    ServerContextHarness h = ServerContextHarness.start(engine.EngineFixtures.sapphire());

    h.context().processUpdateAvatar(h.playerId(), "char_suspect_03");

    assertEquals("char_suspect_03", h.host().getAvatarId());
    PlayerAvatarChangedDTO broadcast = lastAvatarChange(h);
    assertTrue("a PlayerAvatarChangedDTO should be broadcast", broadcast != null);
    assertEquals(h.playerId(), broadcast.getPlayerId());
    assertEquals("char_suspect_03", broadcast.getNewAvatarId());
  }

  @Test
  public void unknownAvatarIdIsRejectedWithNoChangeOrBroadcast() {
    ServerContextHarness h = ServerContextHarness.start(engine.EngineFixtures.sapphire());

    h.context().processUpdateAvatar(h.playerId(), "../../etc/passwd");

    assertNull("an unknown id must not be stored", h.host().getAvatarId());
    assertNull("an unknown id must not be broadcast", lastAvatarChange(h));
  }

  private static PlayerAvatarChangedDTO lastAvatarChange(ServerContextHarness h) {
    PlayerAvatarChangedDTO last = null;
    for (Serializable dto : h.host().sent()) {
      if (dto instanceof PlayerAvatarChangedDTO change) {
        last = change;
      }
    }
    return last;
  }
}

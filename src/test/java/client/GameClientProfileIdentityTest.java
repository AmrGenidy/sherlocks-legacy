package client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import common.commands.Command;
import common.commands.UpdateAvatarCommand;
import common.commands.UpdateDisplayNameCommand;
import common.dto.ClientIdAssignmentDTO;
import common.dto.LobbyUpdateDTO;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.Test;

/**
 * The local player profile feeds the multiplayer identity (player-profile feature): on registration
 * the client announces the profile's display name (no manual {@code /setname}) and chosen avatar,
 * and each client can read the OTHER player's announced name + avatar from a lobby update.
 */
public class GameClientProfileIdentityTest {

  private static GameClient client() {
    return new GameClient("localhost", 0, line -> {}, GameClient.LaunchMode.NORMAL, null);
  }

  private static void dispatch(GameClient c, Object message) throws Exception {
    Method m = GameClient.class.getDeclaredMethod("processServerMessage", Object.class);
    m.setAccessible(true);
    m.invoke(c, message);
  }

  @Test
  public void profileNameAndAvatarAreAnnouncedOnRegistration() {
    List<Command> commands = GameClient.identityAnnounceCommands("Irene Adler", "char_suspect_03");

    UpdateDisplayNameCommand nameCmd =
        commands.stream()
            .filter(UpdateDisplayNameCommand.class::isInstance)
            .map(UpdateDisplayNameCommand.class::cast)
            .findFirst()
            .orElse(null);
    assertTrue("a display-name announce should be sent", nameCmd != null);
    assertEquals("Irene Adler", nameCmd.getPayload().getNewDisplayName());

    UpdateAvatarCommand avatarCmd =
        commands.stream()
            .filter(UpdateAvatarCommand.class::isInstance)
            .map(UpdateAvatarCommand.class::cast)
            .findFirst()
            .orElse(null);
    assertTrue("an avatar announce should always be sent", avatarCmd != null);
    assertEquals("char_suspect_03", avatarCmd.getPayload().getAvatarId());
  }

  @Test
  public void blankProfileNameSkipsTheNameAnnounceSoTheRandomNameStands() {
    List<Command> commands = GameClient.identityAnnounceCommands("", "char_suspect_03");
    assertTrue(
        "no display-name announce for a blank profile name",
        commands.stream().noneMatch(UpdateDisplayNameCommand.class::isInstance));
    assertTrue(
        "the avatar is still announced",
        commands.stream().anyMatch(UpdateAvatarCommand.class::isInstance));
  }

  @Test
  public void unknownAvatarFallsBackToTheDefaultPreset() {
    UpdateAvatarCommand avatarCmd =
        (UpdateAvatarCommand)
            GameClient.identityAnnounceCommands(null, "../../etc/passwd").stream()
                .filter(UpdateAvatarCommand.class::isInstance)
                .findFirst()
                .orElseThrow(AssertionError::new);
    assertEquals(common.PlayerAvatars.DEFAULT_ID, avatarCmd.getPayload().getAvatarId());
  }

  @Test
  public void registrationOptimisticallyAdoptsTheProfileName() throws Exception {
    GameClient c = client();
    c.setInitialIdentity("Sherlock", "char_suspect_01");

    dispatch(c, new ClientIdAssignmentDTO("p-1", "Player4242"));

    // The profile name replaces the server-assigned random name locally, with no /setname typed.
    assertEquals("Sherlock", c.getOwnDisplayName());
    assertEquals("char_suspect_01", c.getOwnAvatarId());
  }

  @Test
  public void blankProfileLeavesTheServerAssignedNameOnRegistration() throws Exception {
    GameClient c = client();
    c.setInitialIdentity("", "char_partner");

    dispatch(c, new ClientIdAssignmentDTO("p-1", "Player4242"));

    assertEquals("Player4242", c.getOwnDisplayName());
  }

  @Test
  public void peerIdentityIsReadFromALobbyUpdate() throws Exception {
    GameClient c = client();
    c.setInitialIdentity("Sherlock", "char_suspect_01");
    dispatch(c, new ClientIdAssignmentDTO("host-1", "Player4242"));

    // A lobby update carrying both players: index-paired display names + avatar ids.
    dispatch(
        c,
        new LobbyUpdateDTO(
            "Both players are in the lobby.",
            List.of("Sherlock", "Irene Adler"),
            List.of("host-1", "guest-2"),
            java.util.Arrays.asList("char_suspect_01", "char_suspect_03"),
            "host-1",
            false,
            null,
            List.of()));

    assertEquals("Irene Adler", c.getPeerDisplayName());
    assertEquals("char_suspect_03", c.getPeerAvatarId());
  }

  @Test
  public void noPeerKnownYieldsNull() {
    GameClient c = client();
    assertNull(c.getPeerDisplayName());
    assertNull(c.getPeerAvatarId());
  }
}

package client.discovery;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import common.JoinCodes;
import common.WireLimits;
import common.dto.LanDiscoveryPacket;
import java.util.Optional;
import org.junit.Test;

/**
 * LAN discovery input handling (security-pass issues 07/08): packets are unauthenticated UDP from
 * anyone on the network, so every field is validated, a hostile packet can never kill the
 * listener's state handling, the tracked-games map is bounded, and join codes travel only as
 * digests.
 */
public class UdpDiscoveryValidationTest {

  private static LanDiscoveryPacket validPacket(String sessionId) {
    return new LanDiscoveryPacket(
        sessionId, "The Stolen Sapphire", "Holmes", false, JoinCodes.digest("AB123"), 8888);
  }

  // --- field validation ---------------------------------------------------------------------

  @Test
  public void validPacketIsAccepted() {
    Optional<DiscoveredGame> game =
        UdpLanGameDiscoveryService.toDiscoveredGame(validPacket("sess-1"), "192.168.1.10");

    assertTrue(game.isPresent());
    assertEquals("sess-1", game.get().getSessionId());
    assertEquals("192.168.1.10", game.get().getHostIp());
    assertEquals(8888, game.get().getPort());
  }

  @Test
  public void nullOrBlankSessionIdIsRejected() {
    assertFalse(
        UdpLanGameDiscoveryService.toDiscoveredGame(validPacket(null), "ip").isPresent());
    assertFalse(
        UdpLanGameDiscoveryService.toDiscoveredGame(validPacket("   "), "ip").isPresent());
  }

  @Test
  public void overlongFieldsAreRejected() {
    assertFalse(
        UdpLanGameDiscoveryService.toDiscoveredGame(
                validPacket("x".repeat(WireLimits.MAX_ID_LENGTH + 1)), "ip")
            .isPresent());

    LanDiscoveryPacket bloatedTitle = validPacket("sess-1");
    bloatedTitle.setCaseTitle("x".repeat(WireLimits.MAX_CASE_TITLE_LENGTH + 1));
    assertFalse(UdpLanGameDiscoveryService.toDiscoveredGame(bloatedTitle, "ip").isPresent());

    LanDiscoveryPacket bloatedHost = validPacket("sess-1");
    bloatedHost.setHostDisplayName("x".repeat(WireLimits.MAX_DISPLAY_NAME_LENGTH * 2 + 1));
    assertFalse(UdpLanGameDiscoveryService.toDiscoveredGame(bloatedHost, "ip").isPresent());
  }

  @Test
  public void outOfRangePortIsRejected() {
    LanDiscoveryPacket zeroPort = validPacket("sess-1");
    zeroPort.setTcpPort(0);
    assertFalse(UdpLanGameDiscoveryService.toDiscoveredGame(zeroPort, "ip").isPresent());

    LanDiscoveryPacket hugePort = validPacket("sess-1");
    hugePort.setTcpPort(65536);
    assertFalse(UdpLanGameDiscoveryService.toDiscoveredGame(hugePort, "ip").isPresent());
  }

  @Test
  public void completelyEmptyPacketIsRejectedNotThrown() {
    // The historical bug: a packet with a null sessionId threw NPE and permanently killed the
    // listener thread. Validation must reject it without throwing.
    assertFalse(
        UdpLanGameDiscoveryService.toDiscoveredGame(new LanDiscoveryPacket(), "ip").isPresent());
  }

  // --- map growth cap -----------------------------------------------------------------------

  @Test
  public void discoveredGamesMapIsBounded() {
    UdpLanGameDiscoveryService service = new UdpLanGameDiscoveryService();
    for (int i = 0; i < UdpLanGameDiscoveryService.MAX_DISCOVERED_GAMES + 10; i++) {
      service.accept(validPacket("sess-" + i), "10.0.0." + (i % 250));
    }

    assertEquals(
        UdpLanGameDiscoveryService.MAX_DISCOVERED_GAMES, service.getCurrentGames().size());

    // Updates to an already-known session still land when the map is full.
    LanDiscoveryPacket update = validPacket("sess-1");
    update.setHostDisplayName("NewName");
    service.accept(update, "10.0.0.1");
    assertTrue(
        service.getCurrentGames().stream()
            .anyMatch(g -> "NewName".equals(g.getHostDisplayName())));
  }

  // --- join-code digests --------------------------------------------------------------------

  @Test
  public void findByCodeMatchesViaDigestNotCleartext() {
    UdpLanGameDiscoveryService service = new UdpLanGameDiscoveryService();
    service.accept(validPacket("sess-1"), "192.168.1.10");

    Optional<DiscoveredGame> found = service.findByCode("ab123"); // case-insensitive entry
    assertTrue(found.isPresent());
    assertEquals("sess-1", found.get().getSessionId());
    assertFalse("wrong code must not match", service.findByCode("ZZZZZ").isPresent());

    // The stored value is a digest, not the code.
    assertEquals(JoinCodes.digest("AB123"), found.get().getGameCodeDigest());
    assertFalse("AB123".equals(found.get().getGameCodeDigest()));
  }

  @Test
  public void digestNormalizesCaseAndWhitespaceAndHidesTheCode() {
    String digest = JoinCodes.digest(" ab123 ");
    assertEquals(JoinCodes.digest("AB123"), digest);
    assertEquals(64, digest.length()); // SHA-256 hex
    assertFalse(digest.contains("AB123"));
    assertEquals(null, JoinCodes.digest(null));
    assertEquals(null, JoinCodes.digest("  "));
  }
}

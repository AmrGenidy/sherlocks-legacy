package client.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import common.JoinCodes;
import common.NetworkConstants;
import common.WireLimits;
import common.dto.LanDiscoveryPacket;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UdpLanGameDiscoveryService implements LanGameDiscoveryService {
    private static final Logger logger = LoggerFactory.getLogger(UdpLanGameDiscoveryService.class);

    /**
     * Cap on tracked games (security-pass issue 07): discovery packets are unauthenticated UDP, so
     * a spoofer must not be able to grow this map without bound. Updates to known sessions are
     * always accepted.
     */
    static final int MAX_DISCOVERED_GAMES = 64;

    private final ConcurrentHashMap<String, DiscoveredGame> discoveredGames = new ConcurrentHashMap<>();
    private volatile boolean running = false;
    private Thread listenerThread;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void start() {
        if (running) return;
        running = true;
        discoveredGames.clear();
        listenerThread = new Thread(this::listenLoop, "LanDiscoveryListener");
        listenerThread.setDaemon(true);
        listenerThread.start();
        logger.info("LAN Discovery listener started.");
    }

    public void stop() {
        running = false;
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
        logger.info("LAN Discovery listener stopped.");
    }

    private void listenLoop() {
        try (DatagramSocket socket = new DatagramSocket(NetworkConstants.DISCOVERY_PORT)) {
            socket.setSoTimeout(2000); // Unblock every 2 seconds to check the running flag
            while (running) {
                byte[] buf = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(packet);

                    LanDiscoveryPacket packetInfo = objectMapper.readValue(packet.getData(), 0, packet.getLength(), LanDiscoveryPacket.class);
                    accept(packetInfo, packet.getAddress().getHostAddress());

                } catch (SocketTimeoutException e) {
                    // This is expected, just loop again to check the 'running' flag
                } catch (Exception e) {
                    // Per-packet resilience (security-pass issue 07): ANY hostile or malformed
                    // datagram is logged and skipped — it must never terminate the listener.
                    if (running) {
                        logger.warn("Ignoring undecodable/invalid discovery packet: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            if (running) {
                logger.error("Failed to start LAN discovery listener on port {}", NetworkConstants.DISCOVERY_PORT, e);
            }
        }
    }

    /** Validates and records one received packet. Package-private for tests. */
    void accept(LanDiscoveryPacket packetInfo, String hostIp) {
        Optional<DiscoveredGame> validated = toDiscoveredGame(packetInfo, hostIp);
        if (validated.isEmpty()) {
            logger.warn("Rejected invalid discovery packet from {}", hostIp);
            return;
        }
        DiscoveredGame game = validated.get();
        if (!discoveredGames.containsKey(game.getSessionId())
                && discoveredGames.size() >= MAX_DISCOVERED_GAMES) {
            logger.warn("Discovery list full ({}); ignoring new session from {}", MAX_DISCOVERED_GAMES, hostIp);
            return;
        }
        logger.debug("Discovered LAN game: title='{}', host='{}', public={}",
                game.getGameName(), game.getHostDisplayName(), game.isPublicGame());
        discoveredGames.put(game.getSessionId(), game);
    }

    /**
     * Field-validates an unauthenticated discovery packet (security-pass issue 07): sessionId
     * present and bounded, display strings bounded, TCP port in the valid range. Anything else is
     * rejected before it can reach the lobby UI or the discovered-games map.
     */
    static Optional<DiscoveredGame> toDiscoveredGame(LanDiscoveryPacket packetInfo, String hostIp) {
        if (packetInfo == null || hostIp == null) {
            return Optional.empty();
        }
        String sessionId = packetInfo.getSessionId();
        if (sessionId == null || sessionId.trim().isEmpty()
                || sessionId.length() > WireLimits.MAX_ID_LENGTH) {
            return Optional.empty();
        }
        String caseTitle = packetInfo.getCaseTitle();
        if (caseTitle == null || caseTitle.length() > WireLimits.MAX_CASE_TITLE_LENGTH) {
            return Optional.empty();
        }
        String hostName = packetInfo.getHostDisplayName();
        if (hostName == null || hostName.length() > WireLimits.MAX_DISPLAY_NAME_LENGTH * 2) {
            return Optional.empty();
        }
        String joinCodeHash = packetInfo.getJoinCodeHash();
        if (joinCodeHash != null && joinCodeHash.length() > WireLimits.MAX_ID_LENGTH) {
            return Optional.empty();
        }
        int tcpPort = packetInfo.getTcpPort();
        if (tcpPort < 1 || tcpPort > 65535) {
            return Optional.empty();
        }

        return Optional.of(new DiscoveredGame(
                caseTitle,
                hostName,
                packetInfo.isPublicGame(),
                joinCodeHash,
                hostIp,
                tcpPort,
                1, // For now, we assume 1 player is in the lobby
                2,
                sessionId));
    }

    @Override
    public List<DiscoveredGame> getCurrentGames() {
        return new ArrayList<>(discoveredGames.values());
    }

    @Override
    public void refreshAsync() {
        // Clearing the list allows it to be repopulated with fresh broadcasts.
        discoveredGames.clear();
        logger.info("Cleared discovered games list for refresh.");
    }

    public Optional<DiscoveredGame> findByCode(String joinCode) {
        // Discovery packets carry only the digest of the join code (issue 08); digest the typed
        // code and match.
        String digest = JoinCodes.digest(joinCode);
        if (digest == null) {
            return Optional.empty();
        }
        return discoveredGames.values().stream()
                .filter(g -> digest.equals(g.getGameCodeDigest()))
                .findFirst();
    }
}

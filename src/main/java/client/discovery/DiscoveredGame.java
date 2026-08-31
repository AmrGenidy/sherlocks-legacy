package client.discovery;

public class DiscoveredGame {
    private final String gameName;
    private final String hostDisplayName;
    private final boolean publicGame;
    private final String gameCodeDigest; // SHA-256 digest of the join code (never cleartext)
    private final String hostIp;     // internal use ONLY
    private final int port;          // internal use ONLY
    private final int playerCount;
    private final int maxPlayers;
    private final String sessionId; // internal use ONLY

    public DiscoveredGame(String gameName, String hostDisplayName, boolean isPublic, String gameCodeDigest, String hostIp, int port, int playerCount, int maxPlayers, String sessionId) {
        this.gameName = gameName;
        this.hostDisplayName = hostDisplayName;
        this.publicGame = isPublic;
        this.gameCodeDigest = gameCodeDigest;
        this.hostIp = hostIp;
        this.port = port;
        this.playerCount = playerCount;
        this.maxPlayers = maxPlayers;
        this.sessionId = sessionId;
    }

    // Getters for all fields
    public String getGameName() {
        return gameName;
    }

    public String getHostDisplayName() {
        return hostDisplayName;
    }

    public boolean isPublicGame() {
        return publicGame;
    }

    public String getGameCodeDigest() {
        return gameCodeDigest;
    }

    public String getHostIp() {
        return hostIp;
    }

    public int getPort() {
        return port;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public String getSessionId() {
        return sessionId;
    }

    @Override
    public String toString() {
        return String.format("%s (Host: %s, Players: %d/%d)", gameName, hostDisplayName, playerCount, maxPlayers);
    }
}

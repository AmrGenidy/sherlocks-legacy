package common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public class LanDiscoveryPacket implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sessionId;
    private String caseTitle;
    private String hostDisplayName;
    private boolean publicGame; // Renamed from isPublic
    // SHA-256 digest of the private join code, never the code itself: these packets go to the
    // whole LAN (security-pass issue 08). Null for public games.
    private String joinCodeHash;
    private int tcpPort;

    // No-arg constructor for Jackson
    public LanDiscoveryPacket() {
    }

    public LanDiscoveryPacket(String sessionId, String caseTitle, String hostDisplayName, boolean isPublic, String joinCodeHash, int tcpPort) {
        this.sessionId = sessionId;
        this.caseTitle = caseTitle;
        this.hostDisplayName = hostDisplayName;
        this.publicGame = isPublic;
        this.joinCodeHash = joinCodeHash;
        this.tcpPort = tcpPort;
    }

    // Getters and Setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getCaseTitle() { return caseTitle; }
    public void setCaseTitle(String caseTitle) { this.caseTitle = caseTitle; }

    public String getHostDisplayName() { return hostDisplayName; }
    public void setHostDisplayName(String hostDisplayName) { this.hostDisplayName = hostDisplayName; }

    @JsonProperty("public")
    public boolean isPublicGame() { return publicGame; }

    @JsonProperty("public")
    public void setPublicGame(boolean publicGame) { this.publicGame = publicGame; }

    public String getJoinCodeHash() { return joinCodeHash; }
    public void setJoinCodeHash(String joinCodeHash) { this.joinCodeHash = joinCodeHash; }

    public int getTcpPort() { return tcpPort; }
    public void setTcpPort(int tcpPort) { this.tcpPort = tcpPort; }
}

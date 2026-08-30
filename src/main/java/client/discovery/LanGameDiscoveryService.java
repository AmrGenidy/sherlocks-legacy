package client.discovery;

import java.util.List;

public interface LanGameDiscoveryService {
    List<DiscoveredGame> getCurrentGames();
    void refreshAsync();
}

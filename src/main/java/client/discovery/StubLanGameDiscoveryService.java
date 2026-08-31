package client.discovery;

import java.util.ArrayList;
import java.util.List;
import common.NetworkConstants;

public class StubLanGameDiscoveryService implements LanGameDiscoveryService {

    private final List<DiscoveredGame> dummyGames = new ArrayList<>();

    public StubLanGameDiscoveryService() {
        // By default, the stub returns an empty list.
        // To add dummy data for testing, you can manually add games here.
    }

    @Override
    public List<DiscoveredGame> getCurrentGames() {
        // In a real implementation, this would return a cached list
        // that is updated by a background discovery thread.
        return new ArrayList<>(dummyGames);
    }

    @Override
    public void refreshAsync() {
        // In a real implementation, this would trigger a new UDP multicast.
        // For the stub, we can just print a message.
        System.out.println("[STUB DISCOVERY] Refresh triggered. No-op for stub service.");
    }
}

package server;

import com.fasterxml.jackson.databind.ObjectMapper;
import common.NetworkConstants;
import common.dto.LanDiscoveryPacket;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LanGameBroadcaster implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(LanGameBroadcaster.class);
    private final Supplier<LanDiscoveryPacket> packetSupplier;
    private volatile boolean running = true;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LanGameBroadcaster(Supplier<LanDiscoveryPacket> packetSupplier) {
        this.packetSupplier = packetSupplier;
    }

    public void stop() {
        this.running = false;
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            logger.info("LAN Broadcaster started on port {}.", socket.getLocalPort());
            while (running) {
                LanDiscoveryPacket packetInfo = packetSupplier.get();
                if (packetInfo == null) {
                    logger.warn("Packet supplier returned null, stopping broadcast.");
                    break;
                }

                byte[] data = objectMapper.writeValueAsBytes(packetInfo);

                DatagramPacket packet = new DatagramPacket(
                        data, data.length,
                        InetAddress.getByName("255.255.255.255"),
                        NetworkConstants.DISCOVERY_PORT
                );

                socket.send(packet);
                Thread.sleep(NetworkConstants.DISCOVERY_INTERVAL_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.info("LAN Broadcaster interrupted and shutting down.");
        } catch (Exception e) {
            logger.error("Error in LAN Broadcaster", e);
        } finally {
            logger.info("LAN Broadcaster stopped.");
        }
    }
}

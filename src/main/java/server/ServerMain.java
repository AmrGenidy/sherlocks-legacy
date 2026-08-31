package server;

import common.NetworkConstants;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class ServerMain {
  public static void main(String[] args) {
    int port = NetworkConstants.DEFAULT_PORT;
    if (args.length > 0) {
      try {
        port = Integer.parseInt(args[0]);
      } catch (NumberFormatException e) {
        System.err.println("Invalid port number provided. Using default port: " + port);
      }
    }

    GameServer server = new GameServer(port);
    Thread serverThread = null;
    Scanner consoleScanner;

    try {
      server.startServer(); // Initializes channels and selector
      serverThread = new Thread(server, "GameServerThread");
      serverThread.start(); // Starts the server's run() loop

      // Server console input loop
      consoleScanner = new Scanner(System.in); // Assign here
      server.log(
          "Server console ready. Type 'shutdown' to stop the server.");
      label:
      while (true) {
        String input;
        try {
          if (System.in.available() > 0
              || consoleScanner
                  .hasNextLine()) { // Check if input might be available or if stream is open
            input = consoleScanner.nextLine().trim().toLowerCase();
          } else {
            // Handle case where System.in might be closed externally, though unlikely here
            server.log("Server console input stream appears closed. Exiting console loop.");
            server.stopServer(); // Initiate shutdown if console closes
            break;
          }
        } catch (NoSuchElementException e) {
          server.log("Server console input stream closed. Exiting console loop.");
          server.stopServer(); // Initiate shutdown if console closes
          break;
        } catch (IllegalStateException e) {
          server.log("Server console Scanner closed. Exiting console loop.");
          server.stopServer();
          break;
        }

        switch (input) {
          case "shutdown":
            server.log("Shutdown command received from console.");
            server.stopServer(); // Signals the server thread to stop

            break label; // Exit console loop
          case "reloadcases":
            server.log("ReloadCases command received from console.");
            if (server.sessionManager != null) {
              server.sessionManager.reloadCases();
            } else {
              server.log("Session Manager not available to reload cases.");
            }
            break;
          default:
            server.log("Unknown server console command: " + input);
            break;
        }
      }
      // consoleScanner.close(); // <-- DO NOT CLOSE System.in SCANNER HERE

    } catch (IOException e) {
      server.logError("Fatal error starting server: " + e.getMessage(), e);
      System.exit(1);
    } finally {
      // Ensure server thread is joined if it was started
      if (serverThread != null) {
        try {
          server.stopServer(); // Signal again, harmless if already signaled
          serverThread.join(5000);
          if (serverThread.isAlive()) {
            server.log("Warning: Server thread did not terminate gracefully. Interrupting.");
            serverThread.interrupt();
          }
        } catch (InterruptedException e) {
          server.logError("Interrupted while waiting for server thread to shut down.", e);
          Thread.currentThread().interrupt();
        }
      }
      server.log("ServerMain finished.");
      // Do NOT close consoleScanner here either
    }
  }
}

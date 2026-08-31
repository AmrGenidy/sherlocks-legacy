package client;

import common.NetworkConstants;

public class ClientMain {

  /**
   * Entry point for the client application. Parses optional host/port args, creates a GameClient,
   * and directly runs its main logic loop.
   */
  public static void main(String[] args) {
    // Default connection settings.
    String host = NetworkConstants.DEFAULT_HOST;
    int port = NetworkConstants.DEFAULT_PORT;

    // Override defaults if command-line arguments are provided.
    if (args.length >= 1) {
      host = args[0];
    }
    if (args.length >= 2) {
      try {
        port = Integer.parseInt(args[1]);
      } catch (NumberFormatException e) {
        System.err.println(
                "ClientMain: Invalid port number provided: '"
                        + args[1]
                        + "'. Using default port "
                        + port
                        + ".");
      }
    }

    // Startup banner.
    System.out.println("\n========================================");
    System.out.println("  Starting Detective Game Client...");
    System.out.println("========================================");

    // Create the main client logic object.
    GameClient client = new GameClient(host, port, null, GameClient.LaunchMode.NORMAL);
    try {
      client.run();
    } catch (Exception e) {
      // Catch any unexpected, unhandled exceptions from GameClient.run()
      // to prevent ClientMain from crashing silently.
      System.err.println(
              "ClientMain: CRITICAL UNHANDLED ERROR from GameClient.run(): " + e.getMessage());
      e.printStackTrace();
    }

    // This line is reached only after GameClient.run() has completed.
    System.out.println("ClientMain: GameClient has finished its execution.");
  }
}

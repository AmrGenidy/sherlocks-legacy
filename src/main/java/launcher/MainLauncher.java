package launcher;

import ui.GameClientFX;

/**
 * Main entry point for the application.
 * This class now directly launches the JavaFX GUI, which serves as the primary
 * user interface for all game modes.
 */
public class MainLauncher {

  public static void main(String[] args) {
    // The application will now launch directly into the JavaFX GUI.
    // The GUI's MainController will handle all logic for starting single player,
    // multiplayer, or server modes.
    GameClientFX.main(args);
  }
}

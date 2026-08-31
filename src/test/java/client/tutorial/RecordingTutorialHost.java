package client.tutorial;

import common.dto.RoomDescriptionDTO;
import java.util.ArrayList;
import java.util.List;

/**
 * Test double for TutorialHost. Records every method call so tests can assert on overlay text,
 * terminal output, scene setup, and lifecycle transitions without needing JavaFX.
 */
public class RecordingTutorialHost implements TutorialHost {

  public final List<String> terminalText = new ArrayList<>();
  public final List<RoomDescriptionDTO> roomViews = new ArrayList<>();
  public final List<String> overlayMessages = new ArrayList<>();
  public final List<String> overlayTargets = new ArrayList<>();
  public final List<Boolean> overlayDismissible = new ArrayList<>();
  public int showGameViewCount = 0;
  public int hideOverlayCount = 0;
  public int showTutorialsMenuCount = 0;

  @Override
  public void appendTerminalText(String text) {
    terminalText.add(text);
  }

  @Override
  public void updateRoomView(RoomDescriptionDTO roomDescription) {
    roomViews.add(roomDescription);
  }

  @Override
  public void showGameView() {
    showGameViewCount++;
  }

  @Override
  public void showTutorialOverlay(String message, String arrowTarget, boolean dismissible) {
    overlayMessages.add(message);
    overlayTargets.add(arrowTarget);
    overlayDismissible.add(dismissible);
  }

  @Override
  public void hideTutorialOverlay() {
    hideOverlayCount++;
  }

  @Override
  public void showTutorialsMenu() {
    showTutorialsMenuCount++;
  }
}

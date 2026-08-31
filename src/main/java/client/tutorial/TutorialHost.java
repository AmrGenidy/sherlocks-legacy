package client.tutorial;

import common.dto.RoomDescriptionDTO;

/**
 * Surface the TutorialManager needs from its UI host. Lets the manager be exercised by a recording
 * fake in headless tests instead of dragging in MainController and JavaFX.
 */
public interface TutorialHost {

  void appendTerminalText(String text);

  void updateRoomView(RoomDescriptionDTO roomDescription);

  void showGameView();

  /**
   * Shows the tutorial overlay — a text guidance bubble plus an optional pointer arrow. Tutorial
   * steps carry no illustration.
   *
   * @param message resolved (already localized) overlay copy
   * @param arrowTarget which region the hint arrow points at
   *     (TERMINAL/RIGHT_PANEL/CENTER/TOP_BAR/NONE)
   * @param dismissible whether the bubble shows a close (×) the player may use to hide this
   *     message; the final completion ("type continue") bubble is not dismissible
   *     (.scratch/gui-tutorial-bubble-polish)
   */
  void showTutorialOverlay(String message, String arrowTarget, boolean dismissible);

  void hideTutorialOverlay();

  void showTutorialsMenu();
}

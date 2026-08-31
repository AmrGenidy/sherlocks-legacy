package ui.menu;

import java.net.URL;
import javafx.scene.image.Image;

/**
 * FX-side resolution of an avatar preset id (see {@link common.PlayerAvatars}) to its bundled
 * portrait {@code Image}, loaded from the classpath at {@code /images/presets/characters/<id>.png}.
 *
 * <p>Returns {@code null} for an unknown/blank id or a load failure, so every caller can fall back
 * gracefully to the engraved bust — an avatar is never a hard dependency.
 */
public final class AvatarImages {

  private AvatarImages() {}

  /**
   * Whether a bundled portrait actually exists for {@code avatarId} (a cheap classpath check, no
   * image decode). The gallery uses this to hide a preset whose PNG has been removed, so an empty
   * slot is never offered as a choice.
   */
  public static boolean exists(String avatarId) {
    return avatarId != null
        && !avatarId.isBlank()
        && AvatarImages.class.getResource("/images/presets/characters/" + avatarId + ".png")
            != null;
  }

  /** The portrait for {@code avatarId}, or {@code null} if it cannot be resolved/loaded. */
  public static Image image(String avatarId) {
    if (avatarId == null || avatarId.isBlank()) {
      return null;
    }
    URL url = AvatarImages.class.getResource("/images/presets/characters/" + avatarId + ".png");
    if (url == null) {
      return null;
    }
    try {
      Image image = new Image(url.toExternalForm());
      return image.isError() ? null : image;
    } catch (RuntimeException ex) {
      return null;
    }
  }
}

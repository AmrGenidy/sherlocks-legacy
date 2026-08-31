package ui.util;

import java.io.InputStream;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * The application window icon (the deerstalker), added to every top-level {@link Stage} so the title
 * bar / taskbar shows the brand mark. Loaded once from the classpath and reused; a missing resource
 * is tolerated (no icon rather than a crash).
 */
public final class AppIcon {

  private static final String ICON_PATH = "/images/Game_Icon.png";

  private static Image icon; // built once, reused across all windows

  private AppIcon() {}

  /** Adds the app icon to {@code stage} (no-op if the asset is missing or already present). */
  public static void applyTo(Stage stage) {
    Image loaded = icon();
    if (stage != null && loaded != null && !stage.getIcons().contains(loaded)) {
      stage.getIcons().add(loaded);
    }
  }

  private static Image icon() {
    if (icon == null) {
      InputStream in = AppIcon.class.getResourceAsStream(ICON_PATH);
      if (in != null) {
        Image loaded = new Image(in);
        if (!loaded.isError()) {
          icon = loaded;
        }
      }
    }
    return icon;
  }
}

package ui.util;

import javafx.scene.image.Image;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Resource loader for images with fallback to programmatically generated placeholders.
 */
public class ImageResourceLoader {

  private static final org.slf4j.Logger logger =
      org.slf4j.LoggerFactory.getLogger(ImageResourceLoader.class);

  private static final Map<String, Image> imageCache = new HashMap<>();

  /**
   * Loads a room image, generating a placeholder if not found.
   */
  public static Image loadRoomImage(String roomName) {
    String imagePath = "/images/rooms/" + roomName.toLowerCase().replace(" ", "_") + ".png";
    
    // Check cache first
    if (imageCache.containsKey(imagePath)) {
      return imageCache.get(imagePath);
    }

    // Try to load from resources
    try {
      InputStream stream = ImageResourceLoader.class.getResourceAsStream(imagePath);
      if (stream != null) {
        Image image = new Image(stream);
        imageCache.put(imagePath, image);
        return image;
      }
    } catch (Exception e) {
      logger.warn("Could not load room image: " + imagePath);
    }

    // Generate placeholder
    Image placeholder = PlaceholderImageGenerator.createRoomPlaceholder(roomName, 800, 600);
    imageCache.put(imagePath, placeholder);
    return placeholder;
  }

  /**
   * Loads a suspect image, generating a placeholder if not found.
   */
  public static Image loadSuspectImage(String suspectName) {
    String imagePath = "/images/suspects/" + suspectName.toLowerCase().replace(" ", "_") + ".png";
    
    // Check cache first
    if (imageCache.containsKey(imagePath)) {
      return imageCache.get(imagePath);
    }

    // Try to load from resources
    try {
      InputStream stream = ImageResourceLoader.class.getResourceAsStream(imagePath);
      if (stream != null) {
        Image image = new Image(stream);
        imageCache.put(imagePath, image);
        return image;
      }
    } catch (Exception e) {
      logger.warn("Could not load suspect image: " + imagePath);
    }

    // Generate placeholder
    Image placeholder = PlaceholderImageGenerator.createSuspectPlaceholder(suspectName, 80);
    imageCache.put(imagePath, placeholder);
    return placeholder;
  }

  /**
   * Loads an object image, generating a placeholder if not found.
   */
  public static Image loadObjectImage(String objectName) {
    String imagePath = "/images/objects/" + objectName.toLowerCase().replace(" ", "_") + ".png";
    
    // Check cache first
    if (imageCache.containsKey(imagePath)) {
      return imageCache.get(imagePath);
    }

    // Try to load from resources
    try {
      InputStream stream = ImageResourceLoader.class.getResourceAsStream(imagePath);
      if (stream != null) {
        Image image = new Image(stream);
        imageCache.put(imagePath, image);
        return image;
      }
    } catch (Exception e) {
      logger.warn("Could not load object image: " + imagePath);
    }

    // Generate placeholder
    Image placeholder = PlaceholderImageGenerator.createObjectPlaceholder(objectName, 80);
    imageCache.put(imagePath, placeholder);
    return placeholder;
  }

  /**
   * Clears the image cache.
   */
  public static void clearCache() {
    imageCache.clear();
  }
}

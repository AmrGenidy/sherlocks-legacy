package ui.menu;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import common.PlayerAvatars;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.image.Image;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Every catalog id resolves to a real bundled portrait; unknown/blank ids fall back to {@code
 * null}.
 */
public class AvatarImagesTest {

  @BeforeClass
  public static void initJFX() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    try {
      Platform.startup(latch::countDown);
    } catch (IllegalStateException alreadyStarted) {
      latch.countDown();
    }
    latch.await(5, TimeUnit.SECONDS);
  }

  @Test
  public void everyCatalogIdResolvesToARealImage() {
    for (String id : PlayerAvatars.IDS) {
      Image image = AvatarImages.image(id);
      assertNotNull("Catalog id must resolve to a bundled portrait: " + id, image);
    }
  }

  @Test
  public void unknownOrBlankIdResolvesToNullForGracefulFallback() {
    assertNull(AvatarImages.image("char_suspect_99"));
    assertNull(AvatarImages.image(""));
    assertNull(AvatarImages.image(null));
  }
}

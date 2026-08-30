package ui.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.junit.BeforeClass;
import org.junit.Test;

/** The application window icon ({@link AppIcon}). */
public class AppChromeTest {

  @BeforeClass
  public static void initJfx() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    try {
      Platform.startup(latch::countDown);
    } catch (IllegalStateException alreadyStarted) {
      latch.countDown();
    }
    latch.await(5, TimeUnit.SECONDS);
  }

  private static void onFx(Runnable body) throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    Throwable[] error = new Throwable[1];
    Platform.runLater(
        () -> {
          try {
            body.run();
          } catch (Throwable t) {
            error[0] = t;
          } finally {
            latch.countDown();
          }
        });
    assertTrue("FX task timed out", latch.await(10, TimeUnit.SECONDS));
    if (error[0] != null) {
      fail("Exception on FX thread: " + error[0]);
    }
  }

  @Test
  public void appIconIsAddedOnceAndIsIdempotent() throws InterruptedException {
    onFx(
        () -> {
          Stage stage = new Stage(); // constructed only — never shown (showing wedges the FX thread)
          AppIcon.applyTo(stage);
          assertEquals("icon added", 1, stage.getIcons().size());
          assertFalse("icon loaded ok", stage.getIcons().get(0).isError());
          AppIcon.applyTo(stage); // idempotent — no duplicate
          assertEquals(1, stage.getIcons().size());
        });
  }
}

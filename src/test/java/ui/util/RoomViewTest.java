package ui.util;

import static org.junit.Assert.*;

import common.dto.RoomDescriptionDTO;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import org.junit.BeforeClass;
import org.junit.Test;
import ui.MainController;

public class RoomViewTest {

  @BeforeClass
  public static void initJFX() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    try {
      Platform.startup(
          () -> {
            latch.countDown();
          });
    } catch (IllegalStateException e) {
      // Toolkit already initialized
      latch.countDown();
    }
    latch.await(5, TimeUnit.SECONDS);
  }

  @Test
  public void testLoadRoomSetsImage() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    final Throwable[] threadError = new Throwable[1];

    Platform.runLater(
        () -> {
          try {
            // Mock MainController
            MainController controller =
                new MainController() {
                  @Override
                  public ImageManager getImageManager() {
                    // Return an anonymous subclass of ImageManager that returns dummy images
                    return new ImageManager() {
                      @Override
                      public Image getRoomImage(String path, String roomName) {
                        // Return a 100x100 placeholder image
                        return new WritableImage(100, 100);
                      }
                    };
                  }

                  @Override
                  public Image getSuspectImage(String name) {
                    return new WritableImage(50, 50);
                  }

                  @Override
                  public Image getObjectImage(String name) {
                    return new WritableImage(50, 50);
                  }
                };

            RoomView roomView = new RoomView(controller);

            // Create DTO with any path (it's ignored by our mock ImageManager)
            RoomDescriptionDTO dto =
                new RoomDescriptionDTO(
                    "Ballroom",
                    "Desc",
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyMap(),
                    "images/ballroom.png");

            // Call loadRoom
            roomView.loadRoom(dto);

            // Verify using reflection
            Field bgField = RoomView.class.getDeclaredField("roomBackgroundImage");
            bgField.setAccessible(true);
            ImageView bgView = (ImageView) bgField.get(roomView);

            Image img = bgView.getImage();
            if (img != null) {
              System.out.println(
                  "TEST SUCCESS: Image loaded successfully (width=" + img.getWidth() + ")");
            } else {
              System.out.println("TEST FAILURE: Image is null");
            }

            assertNotNull("Background image should not be null", img);
            // DESIGN.md §4: the room canvas scales proportionally — preserve aspect ratio, never
            // stretch.
            assertTrue(
                "PreserveRatio should be true (proportional scaling per DESIGN §4)",
                bgView.isPreserveRatio());
            assertTrue("FitWidth property should be bound", bgView.fitWidthProperty().isBound());
            assertTrue("FitHeight property should be bound", bgView.fitHeightProperty().isBound());

          } catch (Throwable e) {
            e.printStackTrace();
            threadError[0] = e;
          } finally {
            latch.countDown();
          }
        });

    boolean completed = latch.await(5, TimeUnit.SECONDS);
    assertTrue("Test timed out", completed);
    if (threadError[0] != null) {
      fail("Exception in JavaFX thread: " + threadError[0].getMessage());
    }
  }

  /**
   * The centered-plate rule (DESIGN.md §5): when the pane is wider (or taller) than the rendered
   * artwork, the image rect centers with even letterbox margins, and sprites anchor to that same
   * rect — so they move with the centered image. Verified at 1024x720 and at a wide aspect.
   */
  @Test
  public void backgroundArtworkAndSpritesCenterInWidePane() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    final Throwable[] threadError = new Throwable[1];

    Platform.runLater(
        () -> {
          try {
            MainController controller =
                new MainController() {
                  @Override
                  public ImageManager getImageManager() {
                    return new ImageManager() {
                      @Override
                      public Image getRoomImage(String path, String roomName) {
                        return new WritableImage(1024, 720);
                      }
                    };
                  }

                  @Override
                  public Image getSuspectImage(String name) {
                    return new WritableImage(50, 50);
                  }

                  @Override
                  public Image getObjectImage(String name) {
                    return new WritableImage(50, 50);
                  }
                };

            RoomView roomView = new RoomView(controller);
            RoomDescriptionDTO dto =
                new RoomDescriptionDTO(
                    "Ballroom",
                    "Desc",
                    Collections.singletonList("Vase"), // falls back to norm (0.3, 0.7)
                    Collections.emptyList(),
                    Collections.emptyMap(),
                    "images/ballroom.png");
            roomView.loadRoom(dto);

            Field bgField = RoomView.class.getDeclaredField("roomBackgroundImage");
            bgField.setAccessible(true);
            ImageView bgView = (ImageView) bgField.get(roomView);
            assertFalse(
                "Background must be unmanaged (positioned from the artwork rect)",
                bgView.isManaged());

            Field objectsField = RoomView.class.getDeclaredField("objects");
            objectsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            javafx.scene.layout.StackPane sprite =
                ((java.util.Map<String, ? extends javafx.scene.layout.StackPane>)
                        objectsField.get(roomView))
                    .get("Vase");
            assertNotNull(sprite);

            // --- Wide aspect: 1600x720 pane, 1024x720 artwork -> offsetX = 288, equal margins.
            roomView.resize(1600, 720);
            RoomViewLayout.Rect wide = RoomViewLayout.renderedImageRect(1600, 720, 1024, 720);
            assertEquals(288.0, wide.x(), 0.001);
            assertEquals(wide.x(), bgView.getLayoutX(), 0.5);
            assertEquals(wide.y(), bgView.getLayoutY(), 0.5);
            double spriteH =
                RoomViewLayout.spriteHeight(wide.height(), RoomViewLayout.OBJECT_BASE_FACTOR, 1.0);
            assertEquals(
                "sprite anchors within the CENTERED artwork rect",
                RoomViewLayout.anchorX(wide, 0.3) - spriteH / 2.0,
                sprite.getLayoutX(),
                0.5);

            // --- Baseline window: 1024x720 pane, artwork fills it -> no margins.
            roomView.resize(1024, 720);
            assertEquals(0.0, bgView.getLayoutX(), 0.5);
            assertEquals(0.0, bgView.getLayoutY(), 0.5);
            RoomViewLayout.Rect snug = RoomViewLayout.renderedImageRect(1024, 720, 1024, 720);
            assertEquals(
                RoomViewLayout.anchorX(snug, 0.3) - spriteH / 2.0, sprite.getLayoutX(), 0.5);
          } catch (Throwable e) {
            e.printStackTrace();
            threadError[0] = e;
          } finally {
            latch.countDown();
          }
        });

    boolean completed = latch.await(5, TimeUnit.SECONDS);
    assertTrue("Test timed out", completed);
    if (threadError[0] != null) {
      fail("Exception in JavaFX thread: " + threadError[0]);
    }
  }

  /**
   * The ochre silhouette outline shows ONLY while a character/object is hovered — nothing is
   * outlined at rest; mouse-enter applies the outline effect to that sprite and mouse-exit removes
   * it.
   */
  @Test
  public void spriteOutlineAppearsOnlyOnHover() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    final Throwable[] threadError = new Throwable[1];

    Platform.runLater(
        () -> {
          try {
            MainController controller =
                new MainController() {
                  @Override
                  public ImageManager getImageManager() {
                    return new ImageManager() {
                      @Override
                      public Image getRoomImage(String path, String roomName) {
                        return new WritableImage(100, 100);
                      }
                    };
                  }

                  @Override
                  public Image getSuspectImage(String name) {
                    return new WritableImage(50, 50);
                  }

                  @Override
                  public Image getObjectImage(String name) {
                    return new WritableImage(50, 50);
                  }
                };

            RoomView roomView = new RoomView(controller);
            roomView.loadRoom(
                new RoomDescriptionDTO(
                    "Room",
                    "Desc",
                    Collections.singletonList("Vase"),
                    Collections.emptyList(),
                    Collections.emptyMap(),
                    "images/room.png"));

            Field objectsField = RoomView.class.getDeclaredField("objects");
            objectsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            javafx.scene.layout.StackPane sprite =
                ((java.util.Map<String, ? extends javafx.scene.layout.StackPane>)
                        objectsField.get(roomView))
                    .get("Vase");
            assertNotNull(sprite);
            ImageView iv = (ImageView) sprite.getChildren().get(0);

            // Per-pixel picking (.scratch/sprite-pixel-hit): hover/click respond to the opaque
            // figure only, so the interaction handlers live on the ImageView (pickOnBounds=false),
            // the StackPane is non-pickable on its empty margins, and the caption is inert.
            assertFalse("sprite ImageView picks by opaque pixels", iv.isPickOnBounds());
            assertFalse(
                "StackPane is non-pickable on its transparent area", sprite.isPickOnBounds());
            javafx.scene.control.Label caption =
                (javafx.scene.control.Label) sprite.getChildren().get(1);
            assertTrue("caption is not part of the hit area", caption.isMouseTransparent());

            assertNull("no outline at rest", iv.getEffect());
            iv.getOnMouseEntered().handle(null); // hover
            assertNotNull("the ochre outline appears on hover", iv.getEffect());
            iv.getOnMouseExited().handle(null); // mouse-out
            assertNull("the outline is removed on mouse-out", iv.getEffect());
          } catch (Throwable e) {
            e.printStackTrace();
            threadError[0] = e;
          } finally {
            latch.countDown();
          }
        });

    boolean completed = latch.await(5, TimeUnit.SECONDS);
    assertTrue("Test timed out", completed);
    if (threadError[0] != null) {
      fail("Exception in JavaFX thread: " + threadError[0]);
    }
  }
}

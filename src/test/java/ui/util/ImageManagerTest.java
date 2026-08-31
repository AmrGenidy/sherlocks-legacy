package ui.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.image.Image;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Verifies {@link ImageManager} resolution behaviour: classpath load, case-directory load (the
 * divergence the runtime loader previously ignored by passing a null case dir), and the engraving
 * {@link PresetArtResolver} fallback when a path is missing/blank/unresolvable (the procedural
 * {@link PlaceholderImageGenerator} is now only the last resort, behind the presets — see
 * docs/PRESET_ART_WIRING.md).
 */
public class ImageManagerTest {

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

  // Engraving preset dimensions (the missing-image fallback, ahead of the procedural placeholder):
  // rooms 1280x720, objects 400x400, characters 480x600. The procedural placeholder sizes (room
  // 800, suspect/object 256) now only appear if even the preset cannot resolve.
  private static final double ROOM_PRESET_W = 1280.0;
  private static final double OBJECT_PRESET_W = 400.0;
  private static final double SUSPECT_PRESET_W = 480.0;

  @Test
  public void missingImageFallsBackToEngravingPresetNotNull() {
    ImageManager im = new ImageManager();
    Image img = im.getObjectImage("images/definitely_missing_zzz.png");
    assertNotNull("Missing image must yield a preset, never null", img);
    // The engraving object preset is 400px — NOT the 256px procedural placeholder.
    assertEquals(OBJECT_PRESET_W, img.getWidth(), 0.0);
  }

  @Test
  public void blankPathResolvesToKindAppropriatePreset() {
    // A slot with a blank/cleared imagePath gets the deterministic engraving preset of the right
    // kind (authored art always wins when present). The procedural placeholder is now last resort.
    ImageManager im = new ImageManager();
    assertEquals(ROOM_PRESET_W, im.getRoomImage("").getWidth(), 0.0);
    assertEquals(SUSPECT_PRESET_W, im.getSuspectImage("  ").getWidth(), 0.0);
    assertEquals(OBJECT_PRESET_W, im.getObjectImage((String) null).getWidth(), 0.0);
  }

  @Test
  public void unresolvablePathFallsBackToPresetForEachKind() {
    // A present-but-broken path must fall back to the preset too (and only then).
    ImageManager im = new ImageManager();
    assertEquals(ROOM_PRESET_W, im.getRoomImage("images/no_such_room_zzz.jpg").getWidth(), 0.0);
    assertEquals(
        SUSPECT_PRESET_W, im.getSuspectImage("images/no_such_suspect_zzz.png").getWidth(), 0.0);
    assertEquals(
        OBJECT_PRESET_W, im.getObjectImage("images/no_such_object_zzz.png").getWidth(), 0.0);
  }

  @Test
  public void classpathImageLoadsRealAsset() {
    // Resolvable room path → the real asset, not the 800x600 placeholder.
    assertResolvesToRealAsset(
        new ImageManager().getRoomImage("cases/sapphire_case/images/ballroom.jpg"),
        "/cases/sapphire_case/images/ballroom.jpg");
  }

  @Test
  public void resolvableSuspectPathLoadsRealAsset() {
    // Resolvable suspect path → the real asset, not the 256 suspect placeholder.
    assertResolvesToRealAsset(
        new ImageManager().getSuspectImage("cases/sapphire_case/images/clara.png"),
        "/cases/sapphire_case/images/clara.png");
  }

  @Test
  public void resolvableObjectPathLoadsRealAsset() {
    // Resolvable object path → the real asset, not the 256 object placeholder.
    assertResolvesToRealAsset(
        new ImageManager().getObjectImage("cases/sapphire_case/images/terrace.png"),
        "/cases/sapphire_case/images/terrace.png");
  }

  /**
   * Asserts the loaded image is the real classpath asset (matching dimensions), not a placeholder.
   */
  private void assertResolvesToRealAsset(Image actual, String classpathResource) {
    assertNotNull(actual);
    try (InputStream is = getClass().getResourceAsStream(classpathResource)) {
      assertNotNull("Precondition: " + classpathResource + " on classpath", is);
      Image expected = new Image(is);
      assertEquals(
          "Should load the real classpath image, not a placeholder",
          expected.getWidth(),
          actual.getWidth(),
          0.0);
      assertEquals(expected.getHeight(), actual.getHeight(), 0.0);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void caseDirectoryImageResolvesAtRuntime() throws Exception {
    Path caseDir = Files.createTempDirectory("sl-im-case");
    Path imagesDir = Files.createDirectories(caseDir.resolve("images"));
    Path ext = imagesDir.resolve("ext_only_zzz.png");
    Files.copy(
        Paths.get("cases", "images", "ballroom.jpg"), ext, StandardCopyOption.REPLACE_EXISTING);

    ImageManager im = new ImageManager();
    im.setCaseDirectory(caseDir);
    Image actual = im.getObjectImage("images/ext_only_zzz.png");
    assertNotNull(actual);
    // Proves case-dir resolution (not the 256px object placeholder): real asset dimensions.
    assertTrue(
        "Should load the external case-dir image, not a placeholder", actual.getWidth() != 256.0);
  }
}

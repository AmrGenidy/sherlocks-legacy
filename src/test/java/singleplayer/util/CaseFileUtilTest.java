package singleplayer.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Import contract for {@link CaseFileUtil#addCaseFile}: a selected case is COPIED — its {@code
 * .json} and its sibling {@code images/} folder — into the external cases directory beside the app
 * as {@code cases/<slug>/…}, so imported cases live with (and travel with) the app's cases folder.
 * The external directory is redirected to a temp location via the {@code jpackage.app-path}
 * property (the same resolution the packaged app uses), so the test never touches the real {@code
 * cases/} folder.
 */
public class CaseFileUtilTest {

  @Test
  public void importCopiesJsonAndImagesIntoCasesSubfolder() throws Exception {
    Path appDir = Files.createTempDirectory("sl-app");
    Path fakeLauncher = appDir.resolve("Sherlocks Legacy.exe");
    String saved = System.getProperty("jpackage.app-path");
    System.setProperty("jpackage.app-path", fakeLauncher.toString());
    try {
      // A source case living elsewhere: <src>/My Case.json + <src>/images/pic.png.
      Path src = Files.createTempDirectory("sl-src");
      Files.writeString(src.resolve("My Case.json"), "{\"universal_title\":\"x\"}");
      Path imagesDir = Files.createDirectories(src.resolve("images"));
      Files.write(imagesDir.resolve("pic.png"), new byte[] {1, 2, 3});

      String result = CaseFileUtil.addCaseFile(src.resolve("My Case.json").toString());
      assertTrue(result, result.startsWith("Success"));

      Path slugDir = appDir.resolve("cases").resolve("My_Case");
      assertTrue(
          "json copied as <slug>.json", Files.isRegularFile(slugDir.resolve("My_Case.json")));
      assertTrue(
          "sibling images/ copied",
          Files.isRegularFile(slugDir.resolve("images").resolve("pic.png")));

      // A second import of the same slug must not clobber the first.
      String again = CaseFileUtil.addCaseFile(src.resolve("My Case.json").toString());
      assertFalse("re-import is refused, not a Success", again.startsWith("Success"));
    } finally {
      if (saved == null) {
        System.clearProperty("jpackage.app-path");
      } else {
        System.setProperty("jpackage.app-path", saved);
      }
    }
  }
}

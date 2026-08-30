package extractors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.Test;

/**
 * Resolution-order tests for {@link ResourceResolver} — the single source of truth shared by the
 * runtime image loader and the case validator. Covers the three documented sources: classpath, case
 * directory, and a genuinely-missing path.
 */
public class ResourceResolverTest {

  @Test
  public void resolvesBundledImageFromClasspath() {
    // Ships inside the self-contained sapphire case folder, on the classpath (DEC-3).
    Optional<URL> resolved =
        ResourceResolver.resolve("cases/sapphire_case/images/ballroom.jpg", null);
    assertTrue("Bundled classpath image should resolve", resolved.isPresent());
    assertTrue(ResourceResolver.resolves("cases/sapphire_case/images/ballroom.jpg", null));
  }

  @Test
  public void resolvesImageRelativeToExternalCaseDirectory() throws IOException {
    Path caseDir = Files.createTempDirectory("sl-case");
    Path imagesDir = Files.createDirectories(caseDir.resolve("images"));
    Path img = imagesDir.resolve("external_only_asset.png");
    Files.write(img, new byte[] {1, 2, 3, 4});

    // Not on the classpath; only findable via the case directory — the divergence the runtime
    // loader must honour (ImageManager.setCaseDirectory).
    assertFalse(
        "Precondition: not on classpath",
        ResourceResolver.resolves("images/external_only_asset.png", null));
    assertTrue(
        "Should resolve relative to the case directory",
        ResourceResolver.resolves("images/external_only_asset.png", caseDir));
  }

  @Test
  public void missingPathDoesNotResolve() {
    assertFalse(ResourceResolver.resolves("images/this_does_not_exist_zzz.png", null));
    assertEquals(
        Optional.empty(), ResourceResolver.resolve("images/this_does_not_exist_zzz.png", null));
  }

  @Test
  public void blankPathDoesNotResolve() {
    assertFalse(ResourceResolver.resolves(null, null));
    assertFalse(ResourceResolver.resolves("   ", null));
  }

  // --- Path sandboxing (SECURITY_PLAN A/P0-1) -------------------------------------------------
  // A case-provided path must never reach the filesystem outside its own case directory. UNC
  // paths, absolute paths, and ".." traversal are refused BEFORE any File.exists / SMB touch, so
  // a hostile shared case cannot egress (Windows UNC = outbound SMB/NTLM leak) or read arbitrary
  // files. A refused path resolves to empty; the caller's placeholder fallback then kicks in.

  @Test
  public void rejectsUncBackslashPath() {
    assertEquals(Optional.empty(), ResourceResolver.resolve("\\\\host\\share\\x.png", null));
    // Even with a case dir present, a UNC path must not be resolved against it.
    assertFalse(ResourceResolver.resolves("\\\\host\\share\\x.png", java.nio.file.Paths.get(".")));
  }

  @Test
  public void rejectsUncForwardSlashPath() {
    assertEquals(Optional.empty(), ResourceResolver.resolve("//host/share/x.png", null));
    assertFalse(ResourceResolver.resolves("//host/share/x.png", java.nio.file.Paths.get(".")));
  }

  @Test
  public void rejectsWindowsDriveAbsolutePath() {
    assertEquals(Optional.empty(), ResourceResolver.resolve("C:\\Windows\\x.png", null));
    assertFalse(ResourceResolver.resolves("C:\\Windows\\x.png", java.nio.file.Paths.get(".")));
  }

  @Test
  public void rejectsUnixAbsolutePathToRealFileWithoutFilesystemFallback() throws IOException {
    // A path pointing at a genuinely existing absolute file must STILL resolve to empty — proving
    // the unconditional new File(path) fallback (the egress vector) is gone. If the old fallback
    // were present this would resolve, so an empty result is the assertion that it was removed.
    Path real = Files.createTempFile("sl-abs-target", ".png");
    Files.write(real, new byte[] {1, 2, 3, 4});
    String absolute = real.toAbsolutePath().toString();

    assertEquals(Optional.empty(), ResourceResolver.resolve(absolute, null));
    // Also with a case dir: an absolute path is not a safe relative and must not be honoured.
    assertFalse(ResourceResolver.resolves(absolute, real.getParent()));
  }

  @Test
  public void rejectsParentTraversalEvenWhenTargetExists() throws IOException {
    // Lay out  <root>/secret.png  and  <root>/case/  as the case dir. "../secret.png" would escape
    // the case dir to a real file; it must resolve to empty (traversal blocked), not read it.
    Path root = Files.createTempDirectory("sl-traversal");
    Path secret = root.resolve("secret.png");
    Files.write(secret, new byte[] {9, 9, 9});
    Path caseDir = Files.createDirectories(root.resolve("case"));

    assertFalse(
        "Precondition: the target really exists, so a naive resolve would find it",
        !Files.exists(secret));
    assertEquals(Optional.empty(), ResourceResolver.resolve("../secret.png", caseDir));
    assertFalse(ResourceResolver.resolves("..\\secret.png", caseDir));
  }
}

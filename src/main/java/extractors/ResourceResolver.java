package extractors;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Single source of truth for how a case-referenced resource path (image, and later soundtrack)
 * resolves to a concrete location.
 *
 * <p>Resolution order, applied in turn:
 *
 * <ol>
 *   <li>Classpath — {@code /<path>} (works inside the packaged JAR). Trusted: {@code getResource}
 *       cannot reach the filesystem or open a network (SMB) connection.
 *   <li>Case directory — {@code <caseDir>/<path>} when a case directory is known (external cases
 *       loaded from {@code cases/}), but ONLY for a safe relative path that stays inside the case
 *       directory (see {@link #isSafeRelative}).
 * </ol>
 *
 * <p><b>Case-file paths are untrusted (SECURITY_PLAN A/P0-1).</b> A case is shared/imported data,
 * and resolution runs automatically at load/validate time (the {@code cases/} scan + {@link
 * CaseValidator}). There is deliberately <em>no</em> absolute/working-directory {@code new
 * File(path)} fallback: on Windows a UNC path such as {@code \\host\share\x.png} would make {@code
 * File.exists()} open an outbound SMB connection (a phone-home / NTLM-credential leak), and an
 * absolute path would read arbitrary files. Any path that is not a safe relative resolvable on the
 * classpath or strictly within the case directory returns {@link Optional#empty()} without touching
 * the filesystem, so the caller's fallback chain (authored image → preset → placeholder) takes
 * over.
 *
 * <p>Both the runtime image loader ({@code ui.util.ImageManager}) and the {@link CaseValidator}
 * resolve through this class so the validator never diverges from what actually loads at play time.
 */
public final class ResourceResolver {

  private ResourceResolver() {}

  /** Resolves {@code rawPath} to a URL, or empty if it cannot be safely found. */
  public static Optional<URL> resolve(String rawPath, Path caseDir) {
    if (rawPath == null || rawPath.isBlank()) {
      return Optional.empty();
    }
    String path = rawPath.trim();

    // 1. Classpath. Trusted lookup — getResource cannot reach the filesystem or SMB, so even a
    // hostile absolute/UNC string simply misses here (returns null) rather than egressing.
    String classpathName = path.startsWith("/") ? path : "/" + path;
    URL classpathUrl = ResourceResolver.class.getResource(classpathName);
    if (classpathUrl != null) {
      return Optional.of(classpathUrl);
    }

    // 2. Case directory — sandboxed. Only a safe relative path, and only if the normalized
    // candidate stays within the case directory (canonical containment). The isSafeRelative gate
    // means no File.exists / SMB touch ever happens for an absolute, UNC or traversing path.
    if (caseDir != null && isSafeRelative(path)) {
      Path base = caseDir.normalize();
      Path candidate = base.resolve(path).normalize();
      if (candidate.startsWith(base) && Files.exists(candidate)) {
        return toUrl(candidate.toFile());
      }
    }

    // 3. No filesystem fallback: an unsafe or unresolvable path egresses nowhere.
    return Optional.empty();
  }

  /**
   * True only for a plain relative path that cannot escape a case directory or reach the network.
   * Rejects (all purely by inspecting the string, before any filesystem access): null/blank,
   * absolute paths, a leading {@code /} or {@code \}, a Windows drive letter ({@code C:}), UNC
   * prefixes ({@code \\} or {@code //}), and any {@code ..} path segment.
   */
  private static boolean isSafeRelative(String path) {
    if (path == null || path.isBlank()) {
      return false;
    }
    String p = path.trim();
    // Leading separator (absolute-style) or UNC prefix.
    if (p.startsWith("/") || p.startsWith("\\") || p.startsWith("//") || p.startsWith("\\\\")) {
      return false;
    }
    // Windows drive letter, e.g. "C:" or "c:/...".
    if (p.length() >= 2 && Character.isLetter(p.charAt(0)) && p.charAt(1) == ':') {
      return false;
    }
    // Platform's own notion of absolute (belt-and-suspenders; also catches invalid path chars).
    try {
      if (Paths.get(p).isAbsolute()) {
        return false;
      }
    } catch (RuntimeException e) {
      return false;
    }
    // Any parent-traversal segment, on either separator.
    for (String segment : p.split("[/\\\\]")) {
      if (segment.equals("..")) {
        return false;
      }
    }
    return true;
  }

  /** Convenience predicate: does {@code rawPath} resolve to something that exists? */
  public static boolean resolves(String rawPath, Path caseDir) {
    return resolve(rawPath, caseDir).isPresent();
  }

  private static Optional<URL> toUrl(File file) {
    try {
      return Optional.of(file.toURI().toURL());
    } catch (MalformedURLException e) {
      return Optional.empty();
    }
  }
}

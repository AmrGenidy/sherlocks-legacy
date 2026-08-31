package wire;

import common.commands.Command;
import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Discovers, by scanning the compiled class tree, every concrete wire type that crosses the network
 * boundary: {@link Command} implementations under {@code common.commands} and {@link Serializable}
 * DTOs under {@code common.dto}.
 *
 * <p>The scan is the single source of truth for round-trip coverage. Because the contract tests
 * iterate over whatever this scanner returns, a newly added Command or DTO is automatically pulled
 * into the suite and <em>cannot</em> be forgotten — it either round-trips or fails the build.
 *
 * <p>Deliberately excluded from the standalone round-trip set:
 *
 * <ul>
 *   <li>interfaces and abstract classes (e.g. {@code Command}, {@code BaseCommand}) — never framed;
 *   <li>enums (e.g. {@code DialogueType}) — implicitly {@code final}, so Jackson default typing
 *       emits no {@code @class} tag and a bare enum cannot be resolved as a top-level Object. Enum
 *       protocol values are still exercised transitively as fields of their enclosing DTOs;
 *   <li>nested/member/anonymous classes — wire types are top-level.
 * </ul>
 */
final class WireProtocolClasses {

  private WireProtocolClasses() {}

  /** Concrete {@link Command} implementations under the {@code common.commands} package tree. */
  static List<Class<?>> commands() {
    List<Class<?>> result = new ArrayList<>();
    for (Class<?> c : scanPackage("common.commands")) {
      if (Command.class.isAssignableFrom(c) && isFramableWireType(c)) {
        result.add(c);
      }
    }
    result.sort(Comparator.comparing(Class::getName));
    return result;
  }

  /** Concrete {@link Serializable} DTOs under the {@code common.dto} package tree. */
  static List<Class<?>> dtos() {
    List<Class<?>> result = new ArrayList<>();
    for (Class<?> c : scanPackage("common.dto")) {
      if (Serializable.class.isAssignableFrom(c)
          && !Command.class.isAssignableFrom(c)
          && isFramableWireType(c)) {
        result.add(c);
      }
    }
    result.sort(Comparator.comparing(Class::getName));
    return result;
  }

  private static boolean isFramableWireType(Class<?> c) {
    return !c.isInterface()
        && !c.isEnum()
        && !c.isAnnotation()
        && !Modifier.isAbstract(c.getModifiers())
        && !c.isMemberClass()
        && !c.isLocalClass()
        && !c.isAnonymousClass()
        && !c.isSynthetic();
  }

  /** Recursively loads every class whose binary name starts with {@code packageName}. */
  private static List<Class<?>> scanPackage(String packageName) {
    Path root = classesRoot();
    Path pkgDir = root.resolve(packageName.replace('.', '/'));
    if (!Files.isDirectory(pkgDir)) {
      throw new IllegalStateException("Compiled package directory not found: " + pkgDir);
    }
    ClassLoader loader = WireProtocolClasses.class.getClassLoader();
    List<Class<?>> classes = new ArrayList<>();
    try (Stream<Path> walk = Files.walk(pkgDir)) {
      walk.filter(p -> p.toString().endsWith(".class"))
          .forEach(
              p -> {
                String relative = root.relativize(p).toString();
                String binaryName =
                    relative
                        .substring(0, relative.length() - ".class".length())
                        .replace('/', '.')
                        .replace('\\', '.');
                try {
                  classes.add(Class.forName(binaryName, false, loader));
                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                  throw new IllegalStateException("Could not load scanned class " + binaryName, e);
                }
              });
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to scan package " + packageName, e);
    }
    return classes;
  }

  /**
   * Locates {@code target/classes} via a known class file, independent of the working directory.
   */
  private static Path classesRoot() {
    String marker = "common/SerializationUtils.class";
    URL url = WireProtocolClasses.class.getClassLoader().getResource(marker);
    if (url == null || !"file".equals(url.getProtocol())) {
      throw new IllegalStateException("Cannot locate compiled classes root via " + marker);
    }
    try {
      // .../target/classes/common/SerializationUtils.class -> .../target/classes
      return Paths.get(url.toURI()).getParent().getParent();
    } catch (URISyntaxException e) {
      throw new IllegalStateException("Bad classes root URL: " + url, e);
    }
  }
}

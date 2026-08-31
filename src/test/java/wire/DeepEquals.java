package wire;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Structural, field-by-field deep comparison used to assert that a value survives a serialize →
 * deserialize round trip with every field intact.
 *
 * <p>It intentionally ignores any {@code equals()} a wire class may declare and instead walks the
 * full field graph (collections, maps, arrays, nested DTOs), so the assertion is "every field is
 * equal" in the literal sense the wire contract requires. Numbers are compared by value so a
 * declared {@code int}/{@code long}/{@code Double} compares cleanly after JSON narrowing.
 */
final class DeepEquals {

  private DeepEquals() {}

  /**
   * Returns {@code null} when {@code a} and {@code b} are deeply equal, else a path-qualified diff.
   */
  static String diff(Object a, Object b) {
    return diff(a, b, "<root>");
  }

  private static String diff(Object a, Object b, String path) {
    if (a == b) {
      return null;
    }
    if (a == null || b == null) {
      return path + ": one side null (a=" + a + ", b=" + b + ")";
    }

    if (a instanceof Number && b instanceof Number) {
      double da = ((Number) a).doubleValue();
      double db = ((Number) b).doubleValue();
      return da == db ? null : path + ": " + a + " != " + b;
    }
    if (isScalar(a) || isScalar(b)) {
      return a.equals(b) ? null : path + ": " + a + " != " + b;
    }

    if (a instanceof Map<?, ?> || b instanceof Map<?, ?>) {
      return diffMap(asMap(a, path), asMap(b, path), path);
    }
    if (a instanceof Collection<?> || a.getClass().isArray()) {
      return diffCollection(toList(a), toList(b), path);
    }

    if (!a.getClass().equals(b.getClass())) {
      return path + ": type mismatch " + a.getClass().getName() + " != " + b.getClass().getName();
    }
    return diffFields(a, b, path);
  }

  private static String diffMap(Map<?, ?> a, Map<?, ?> b, String path) {
    if (a.size() != b.size()) {
      return path + ": map size " + a.size() + " != " + b.size();
    }
    for (Map.Entry<?, ?> e : a.entrySet()) {
      if (!b.containsKey(e.getKey())) {
        return path + ": missing key " + e.getKey();
      }
      String d = diff(e.getValue(), b.get(e.getKey()), path + "[" + e.getKey() + "]");
      if (d != null) {
        return d;
      }
    }
    return null;
  }

  private static String diffCollection(List<Object> a, List<Object> b, String path) {
    if (a.size() != b.size()) {
      return path + ": size " + a.size() + " != " + b.size();
    }
    for (int i = 0; i < a.size(); i++) {
      String d = diff(a.get(i), b.get(i), path + "[" + i + "]");
      if (d != null) {
        return d;
      }
    }
    return null;
  }

  private static String diffFields(Object a, Object b, String path) {
    for (Class<?> c = a.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
      for (Field f : c.getDeclaredFields()) {
        if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) {
          continue;
        }
        f.setAccessible(true);
        Object va;
        Object vb;
        try {
          va = f.get(a);
          vb = f.get(b);
        } catch (IllegalAccessException e) {
          throw new IllegalStateException("Cannot read field " + f, e);
        }
        String d = diff(va, vb, path + "." + f.getName());
        if (d != null) {
          return d;
        }
      }
    }
    return null;
  }

  private static boolean isScalar(Object o) {
    return o instanceof CharSequence
        || o instanceof Boolean
        || o instanceof Character
        || o instanceof Number
        || o instanceof Enum<?>;
  }

  private static Map<?, ?> asMap(Object o, String path) {
    if (o instanceof Map<?, ?>) {
      return (Map<?, ?>) o;
    }
    throw new IllegalStateException(path + ": expected Map but got " + o.getClass());
  }

  private static List<Object> toList(Object o) {
    if (o instanceof Collection<?>) {
      return new ArrayList<>((Collection<?>) o);
    }
    if (o.getClass().isArray()) {
      int n = Array.getLength(o);
      List<Object> list = new ArrayList<>(n);
      for (int i = 0; i < n; i++) {
        list.add(Array.get(o, i));
      }
      return list;
    }
    throw new IllegalStateException("Not a collection/array: " + o.getClass());
  }
}

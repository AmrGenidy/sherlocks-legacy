package wire;

import com.fasterxml.jackson.annotation.JsonCreator;
import common.commands.Command;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a fully-populated, representative instance of a wire type using only reflection, so the
 * round-trip suite needs no hand-maintained registry of sample objects (which would drift as new
 * classes are added).
 *
 * <p>An instance is populated the way the application's <em>sender</em> would build it: via the
 * richest available data constructor (preferring a {@link JsonCreator}-annotated one), then every
 * public setter. The round trip therefore tests whether everything a sender can express actually
 * arrives at the receiver — a getter-exposed field with no deserialization path shows up as a real
 * fidelity gap, not a false pass.
 *
 * <p>Field values are distinct and non-blank so cross-wiring and drop-on-deserialize bugs surface.
 */
final class WireSampleFactory {

  private int counter = 1;

  /** Builds a populated instance of {@code type}, or {@code null} if it cannot be constructed. */
  Object build(Class<?> type) {
    return instantiate(type, new ArrayDeque<>());
  }

  private Object instantiate(Class<?> type, Deque<Class<?>> stack) {
    if (stack.contains(type)) {
      return null; // break recursive type cycles; null round-trips fine
    }
    Constructor<?> ctor = pickConstructor(type);
    if (ctor == null) {
      return null;
    }
    stack.push(type);
    try {
      Object[] args = new Object[ctor.getParameterCount()];
      Type[] paramTypes = ctor.getGenericParameterTypes();
      for (int i = 0; i < args.length; i++) {
        args[i] = valueForType(paramTypes[i], stack);
      }
      ctor.setAccessible(true);
      Object instance = ctor.newInstance(args);
      populateViaSetters(instance, stack);
      if (instance instanceof Command) {
        ((Command) instance).setPlayerId("player-" + (counter++));
      }
      return instance;
    } catch (ReflectiveOperationException | IllegalArgumentException e) {
      return null;
    } finally {
      stack.pop();
    }
  }

  /**
   * Picks the constructor a sender would use: the {@link JsonCreator}-annotated one if present,
   * otherwise the one with the most parameters (the real "data" constructor; a no-arg constructor
   * exists only for Jackson and populates nothing).
   */
  private Constructor<?> pickConstructor(Class<?> type) {
    Constructor<?>[] ctors = type.getDeclaredConstructors();
    Constructor<?> jsonCreator = null;
    Constructor<?> widest = null;
    for (Constructor<?> c : ctors) {
      if (Modifier.isPrivate(c.getModifiers())) {
        continue;
      }
      if (c.isAnnotationPresent(JsonCreator.class)) {
        jsonCreator = c;
      }
      if (widest == null || c.getParameterCount() > widest.getParameterCount()) {
        widest = c;
      }
    }
    return jsonCreator != null ? jsonCreator : widest;
  }

  private void populateViaSetters(Object instance, Deque<Class<?>> stack) {
    List<Method> setters = new ArrayList<>();
    for (Method m : instance.getClass().getMethods()) {
      if (m.getName().startsWith("set")
          && m.getName().length() > 3
          && m.getParameterCount() == 1
          && !m.isBridge()
          && !m.isSynthetic()
          && m.getDeclaringClass() != Object.class) {
        setters.add(m);
      }
    }
    // deterministic order so generated values are stable across runs
    setters.sort(Comparator.comparing(Method::getName));
    for (Method m : setters) {
      try {
        m.invoke(instance, valueForType(m.getGenericParameterTypes()[0], stack));
      } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
        // setter rejected the value (validation); leave the constructor-set value in place
      }
    }
  }

  private Object valueForType(Type type, Deque<Class<?>> stack) {
    if (type instanceof ParameterizedType) {
      ParameterizedType pt = (ParameterizedType) type;
      Class<?> raw = (Class<?>) pt.getRawType();
      Type[] args = pt.getActualTypeArguments();
      if (Map.class.isAssignableFrom(raw)) {
        Map<Object, Object> map = new LinkedHashMap<>();
        Object k = valueForType(args[0], stack);
        if (k != null) {
          map.put(k, valueForType(args[1], stack));
        }
        return map;
      }
      if (Collection.class.isAssignableFrom(raw)) {
        Object e = valueForType(args[0], stack);
        List<Object> list = new ArrayList<>();
        if (e != null) {
          list.add(e);
        }
        return Set.class.isAssignableFrom(raw) ? new LinkedHashSet<>(list) : list;
      }
      return valueForClass(raw, stack);
    }
    if (type instanceof GenericArrayType) {
      return null; // not used by the current protocol; skip rather than guess
    }
    if (type instanceof Class<?>) {
      return valueForClass((Class<?>) type, stack);
    }
    return null; // type variables / wildcards
  }

  private Object valueForClass(Class<?> c, Deque<Class<?>> stack) {
    if (c == String.class || c == CharSequence.class) {
      return "v" + (counter++);
    }
    if (c == boolean.class || c == Boolean.class) {
      return Boolean.TRUE;
    }
    if (c == char.class || c == Character.class) {
      return 'a';
    }
    if (c == int.class || c == Integer.class) {
      return counter++;
    }
    if (c == long.class || c == Long.class) {
      return (long) (counter++);
    }
    if (c == short.class || c == Short.class) {
      return (short) (counter++);
    }
    if (c == byte.class || c == Byte.class) {
      return (byte) (counter++);
    }
    if (c == double.class || c == Double.class) {
      return (counter++) + 0.5d;
    }
    if (c == float.class || c == Float.class) {
      return (counter++) + 0.5f;
    }
    if (c.isEnum()) {
      Object[] constants = c.getEnumConstants();
      return constants.length == 0 ? null : constants[0];
    }
    if (c.isArray()) {
      Object element = valueForClass(c.getComponentType(), stack);
      Object array = Array.newInstance(c.getComponentType(), element == null ? 0 : 1);
      if (element != null) {
        Array.set(array, 0, element);
      }
      return array;
    }
    if (Map.class.isAssignableFrom(c)) {
      Map<Object, Object> map = new LinkedHashMap<>();
      map.put("k" + (counter++), "v" + (counter++));
      return map;
    }
    if (Set.class.isAssignableFrom(c)) {
      Set<Object> set = new LinkedHashSet<>();
      set.add("v" + (counter++));
      return set;
    }
    if (Collection.class.isAssignableFrom(c)) {
      List<Object> list = new ArrayList<>();
      list.add("v" + (counter++));
      return list;
    }
    if (c == Object.class || c == Serializable.class) {
      return "v" + (counter++);
    }
    // A nested wire/domain type: build it recursively.
    return instantiate(c, stack);
  }
}

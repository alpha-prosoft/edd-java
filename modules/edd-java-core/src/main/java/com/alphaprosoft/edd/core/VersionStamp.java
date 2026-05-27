package com.alphaprosoft.edd.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;

/**
 * Writes the library-managed {@code version} onto an aggregate record after events are folded, so
 * apply handlers never set it themselves. The aggregate's {@code version} record component is
 * replaced via the canonical constructor; the rebuild plan is cached per record class.
 */
final class VersionStamp {

  private record Rebuilder(Constructor<?> canonical, Method[] accessors, int versionIndex) {

    Object rebuild(Object record, long version) {
      try {
        Object[] args = new Object[accessors.length];
        for (int i = 0; i < accessors.length; i++) {
          args[i] = i == versionIndex ? version : accessors[i].invoke(record);
        }
        return canonical.newInstance(args);
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("Failed to set version on " + record.getClass(), e);
      }
    }
  }

  private static final ClassValue<Rebuilder> REBUILDERS =
      new ClassValue<>() {
        @Override
        protected Rebuilder computeValue(Class<?> type) {
          if (!type.isRecord()) {
            return null;
          }
          RecordComponent[] components = type.getRecordComponents();
          Class<?>[] paramTypes = new Class<?>[components.length];
          Method[] accessors = new Method[components.length];
          int versionIndex = -1;
          for (int i = 0; i < components.length; i++) {
            paramTypes[i] = components[i].getType();
            accessors[i] = components[i].getAccessor();
            accessors[i].setAccessible(true);
            if (components[i].getName().equals("version")) {
              versionIndex = i;
            }
          }
          if (versionIndex < 0) {
            return null;
          }
          try {
            Constructor<?> canonical = type.getDeclaredConstructor(paramTypes);
            canonical.setAccessible(true);
            return new Rebuilder(canonical, accessors, versionIndex);
          } catch (NoSuchMethodException e) {
            throw new IllegalStateException("No canonical constructor for record " + type, e);
          }
        }
      };

  @SuppressWarnings("unchecked")
  static <A extends Aggregate> A withVersion(A aggregate, long version) {
    if (aggregate == null || aggregate.version() == version) {
      return aggregate;
    }
    Rebuilder rebuilder = REBUILDERS.get(aggregate.getClass());
    if (rebuilder == null) {
      return aggregate;
    }
    return (A) rebuilder.rebuild(aggregate, version);
  }

  private VersionStamp() {}
}

package com.alphaprosoft.edd.core;

import com.alphaprosoft.edd.command.Identity;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The Kubernetes-style type discriminator: {@code (kind, name)} ⇄ {@code Class}. {@code kind} is
 * the category ({@link #EVENT}, {@link #AGGREGATE}, {@link #COMMAND}, {@link #QUERY}, {@link
 * #IDENTITY}); {@code name} is the registered id (e.g. {@code "user-created"}). Populated
 * automatically as {@code EventId}/{@code CommandId}/{@code QueryId} are created and as modules
 * register aggregates, so the storage codec can resolve a stored {@code {kind,name,…}} envelope
 * back to its record with no Jackson annotations on the domain types.
 */
public final class TypeRegistry {

  public static final String EVENT = "Event";
  public static final String AGGREGATE = "Aggregate";
  public static final String COMMAND = "Command";
  public static final String QUERY = "Query";
  public static final String IDENTITY = "Identity";

  private static final ConcurrentMap<String, Class<?>> BY_KIND_NAME = new ConcurrentHashMap<>();
  private static final ConcurrentMap<Class<?>, String[]> BY_CLASS = new ConcurrentHashMap<>();

  static {
    register(IDENTITY, "identity", Identity.class);
  }

  private TypeRegistry() {}

  public static void register(String kind, String name, Class<?> type) {
    String key = kind + "|" + name;
    Class<?> existing = BY_KIND_NAME.putIfAbsent(key, type);
    if (existing != null && !existing.equals(type)) {
      throw new IllegalStateException(
          "kind/name '"
              + key
              + "' already registered to "
              + existing.getName()
              + ", not "
              + type.getName());
    }
    BY_CLASS.putIfAbsent(type, new String[] {kind, name});
  }

  public static Class<?> resolve(String kind, String name) {
    Class<?> type = BY_KIND_NAME.get(kind + "|" + name);
    if (type == null) {
      throw new IllegalStateException(
          "No type registered for kind '" + kind + "' name '" + name + "'");
    }
    return type;
  }

  public static String kindOf(Class<?> type) {
    return entry(type)[0];
  }

  public static String nameOf(Class<?> type) {
    return entry(type)[1];
  }

  private static String[] entry(Class<?> type) {
    String[] e = BY_CLASS.get(type);
    if (e == null) {
      throw new IllegalStateException(
          "Type not registered: "
              + type.getName()
              + " (register its EventId/CommandId/QueryId, or the aggregate via module(...))");
    }
    return e;
  }
}

package com.alphaprosoft.edd.query;

import com.alphaprosoft.edd.core.TypeRegistry;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class QueryId<Q extends Query, R> {

  private static final Map<String, QueryId<?, ?>> BY_ID = new ConcurrentHashMap<>();
  private static final Map<Class<?>, QueryId<?, ?>> BY_TYPE = new ConcurrentHashMap<>();

  private final String id;
  private final Class<Q> queryType;
  private final Class<R> responseType;

  private QueryId(String id, Class<Q> queryType, Class<R> responseType) {
    this.id = id;
    this.queryType = queryType;
    this.responseType = responseType;
  }

  public static <Q extends Query, R> QueryId<Q, R> of(
      String id, Class<Q> queryType, Class<R> responseType) {
    QueryId<?, ?> existing = BY_ID.get(id);
    if (existing != null) {
      if (!existing.queryType.equals(queryType) || !existing.responseType.equals(responseType)) {
        throw new IllegalStateException(
            "QueryId '" + id + "' already registered with different types");
      }
      @SuppressWarnings("unchecked")
      QueryId<Q, R> cast = (QueryId<Q, R>) existing;
      return cast;
    }
    QueryId<Q, R> created = new QueryId<>(id, queryType, responseType);
    BY_ID.put(id, created);
    BY_TYPE.put(queryType, created);
    TypeRegistry.register(TypeRegistry.QUERY, id, queryType);
    return created;
  }

  public String id() {
    return id;
  }

  public Class<Q> queryType() {
    return queryType;
  }

  public Class<R> responseType() {
    return responseType;
  }

  public static Optional<QueryId<?, ?>> lookup(String id) {
    return Optional.ofNullable(BY_ID.get(id));
  }

  public static <Q extends Query> QueryId<Q, ?> forType(Class<Q> type) {
    QueryId<?, ?> found = BY_TYPE.get(type);
    if (found == null) {
      throw new IllegalArgumentException("No QueryId registered for " + type.getName());
    }
    @SuppressWarnings("unchecked")
    QueryId<Q, ?> cast = (QueryId<Q, ?>) found;
    return cast;
  }

  public static Collection<QueryId<?, ?>> values() {
    return BY_ID.values();
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof QueryId<?, ?> other && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "QueryId[" + id + "]";
  }
}

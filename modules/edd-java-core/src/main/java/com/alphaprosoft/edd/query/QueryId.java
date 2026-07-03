package com.alphaprosoft.edd.query;

import com.alphaprosoft.edd.core.IdRegistry;
import com.alphaprosoft.edd.core.TypeRegistry;
import java.util.Collection;
import java.util.Optional;

public final class QueryId<Q extends Query, R> {

  private static final IdRegistry<QueryId<?, ?>> REGISTRY = new IdRegistry<>(TypeRegistry.QUERY);

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
    QueryId<?, ?> existing =
        REGISTRY.register(id, queryType, () -> new QueryId<>(id, queryType, responseType));
    if (!existing.queryType.equals(queryType) || !existing.responseType.equals(responseType)) {
      throw new IllegalStateException(
          "QueryId '" + id + "' already registered with different types");
    }
    return new QueryId<>(id, queryType, responseType);
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
    return Optional.ofNullable(REGISTRY.byId(id));
  }

  public static <Q extends Query> QueryId<Q, ?> forType(Class<Q> type) {
    QueryId<?, ?> found = REGISTRY.byType(type);
    if (found == null) {
      throw new IllegalArgumentException("No QueryId registered for " + type.getName());
    }
    return new QueryId<>(found.id, type, found.responseType);
  }

  public static Collection<QueryId<?, ?>> values() {
    return REGISTRY.values();
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

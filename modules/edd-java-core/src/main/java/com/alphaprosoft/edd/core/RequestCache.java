package com.alphaprosoft.edd.core;

import com.alphaprosoft.edd.query.Query;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-request scratch shared by all commands of one dispatch (edd-core {@code request_cache}).
 * Carries each aggregate's in-memory state across commands (so a later command sees an earlier
 * one's result), the next event-seq base per aggregate, and resolved dependency results (so a dep
 * requested twice in one request is fetched once). Not thread-safe — one instance per dispatch.
 */
final class RequestCache {

  /** A dependency-resolution result is keyed by its target service (null = local) and the query. */
  record DepKey(String service, Query query) {}

  private final Map<String, Aggregate> aggregates = new HashMap<>();
  private final Map<String, Long> baseSeqs = new HashMap<>();
  private final Map<DepKey, Object> deps = new HashMap<>();

  private static String key(String realm, UUID id) {
    return realm + "|" + id;
  }

  boolean hasAggregate(String realm, UUID id) {
    return aggregates.containsKey(key(realm, id));
  }

  Aggregate aggregate(String realm, UUID id) {
    return aggregates.get(key(realm, id));
  }

  void putAggregate(String realm, UUID id, Aggregate aggregate) {
    aggregates.put(key(realm, id), aggregate);
  }

  boolean hasBaseSeq(String realm, UUID id) {
    return baseSeqs.containsKey(key(realm, id));
  }

  long baseSeq(String realm, UUID id) {
    return baseSeqs.get(key(realm, id));
  }

  void putBaseSeq(String realm, UUID id, long seq) {
    baseSeqs.put(key(realm, id), seq);
  }

  boolean hasDep(DepKey key) {
    return deps.containsKey(key);
  }

  Object dep(DepKey key) {
    return deps.get(key);
  }

  void putDep(DepKey key, Object value) {
    deps.put(key, value);
  }
}

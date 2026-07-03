package com.alphaprosoft.edd.core;

import com.alphaprosoft.edd.command.CommandContext;
import com.alphaprosoft.edd.query.Dep;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class ContextImpl<A extends Aggregate> implements CommandContext<A> {

  private final Map<String, Object> deps;
  private final RequestMeta meta;
  private final A aggregate;
  private final ViewStore viewStore;
  private final Class<A> aggregateType;

  ContextImpl(
      Map<String, Object> deps,
      RequestMeta meta,
      A aggregate,
      ViewStore viewStore,
      Class<A> aggregateType) {
    this.deps = deps;
    this.meta = meta;
    this.aggregate = aggregate;
    this.viewStore = viewStore;
    this.aggregateType = aggregateType;
  }

  @Override
  public <T> T getDeps(Dep<?, T> key) {
    if (!deps.containsKey(key.name())) {
      throw new IllegalStateException("Dep not resolved: " + key.name());
    }
    return key.queryId().responseType().cast(deps.get(key.name()));
  }

  @Override
  public UUID requestId() {
    return meta.requestId();
  }

  @Override
  public UUID interactionId() {
    return meta.interactionId();
  }

  @Override
  public String realm() {
    return meta.realm();
  }

  @Override
  public User user() {
    return meta.user();
  }

  @Override
  public Map<String, String> annotations() {
    return meta.annotations();
  }

  @Override
  public A aggregate() {
    return aggregate;
  }

  @Override
  public Optional<A> getAggregate(UUID aggregateId) {
    return getAggregate(aggregateId, aggregateType);
  }

  @Override
  public Optional<A> getAggregate(UUID aggregateId, long version) {
    return getAggregate(aggregateId, version, aggregateType);
  }

  @Override
  public <T extends Aggregate> Optional<T> getAggregate(UUID aggregateId, Class<T> type) {
    return viewStore == null
        ? Optional.empty()
        : viewStore.getSnapshot(meta.realm(), aggregateId, type);
  }

  @Override
  public <T extends Aggregate> Optional<T> getAggregate(
      UUID aggregateId, long version, Class<T> type) {
    return viewStore == null
        ? Optional.empty()
        : viewStore.getSnapshot(meta.realm(), aggregateId, version, type);
  }
}

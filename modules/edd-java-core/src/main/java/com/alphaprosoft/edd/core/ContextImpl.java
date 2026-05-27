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

  ContextImpl(Map<String, Object> deps, RequestMeta meta, A aggregate, ViewStore viewStore) {
    this.deps = deps;
    this.meta = meta;
    this.aggregate = aggregate;
    this.viewStore = viewStore;
  }

  @Override
  public <T> T getDeps(Dep<?, T> key) {
    if (!deps.containsKey(key.name())) {
      throw new IllegalStateException("Dep not resolved: " + key.name());
    }
    @SuppressWarnings("unchecked")
    T value = (T) deps.get(key.name());
    return value;
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
  public <T extends Aggregate> Optional<T> getAggregate(UUID aggregateId) {
    return viewStore == null ? Optional.empty() : viewStore.getSnapshot(meta.realm(), aggregateId);
  }
}

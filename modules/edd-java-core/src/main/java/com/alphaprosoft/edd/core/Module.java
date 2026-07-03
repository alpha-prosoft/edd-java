package com.alphaprosoft.edd.core;

import com.alphaprosoft.edd.command.Command;
import com.alphaprosoft.edd.command.CommandId;
import com.alphaprosoft.edd.command.CommandSpec;
import com.alphaprosoft.edd.command.Event;
import com.alphaprosoft.edd.command.EventFxHandler;
import com.alphaprosoft.edd.command.EventHandler;
import com.alphaprosoft.edd.command.EventId;
import com.alphaprosoft.edd.query.AggregateQueryHandler;
import com.alphaprosoft.edd.query.Dep;
import com.alphaprosoft.edd.query.Query;
import com.alphaprosoft.edd.query.QueryContext;
import com.alphaprosoft.edd.query.QueryId;
import com.alphaprosoft.edd.query.QuerySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A self-contained, application-independent description of one aggregate's registrations. Built
 * with {@link #builder(Class)} and absorbed by {@code Application.Builder.module(...)} — the
 * aggregate type is declared once, here, and never repeated at the call site.
 *
 * <p>There is no initial-aggregate factory: replay folds from {@code null}, so an aggregate does
 * not exist until its creation event. A creation apply builds a fresh instance from the event;
 * every other apply receives the prior state. Apply handlers that can run first must handle a
 * {@code null} prior state explicitly.
 */
public final class Module<A extends Aggregate> {

  private final Class<A> aggregateType;
  private final List<Consumer<Application.Builder>> registrations;

  private Module(Class<A> aggregateType, List<Consumer<Application.Builder>> registrations) {
    this.aggregateType = aggregateType;
    this.registrations = registrations;
  }

  public static <A extends Aggregate> Builder<A> builder(Class<A> aggregateType) {
    return new Builder<>(aggregateType);
  }

  Class<A> aggregateType() {
    return aggregateType;
  }

  void applyTo(Application.Builder app) {
    TypeRegistry.register(TypeRegistry.AGGREGATE, aggregateType.getSimpleName(), aggregateType);
    registrations.forEach(reg -> reg.accept(app));
  }

  public static final class Builder<A extends Aggregate> {

    private final Class<A> aggregateType;
    private final List<Consumer<Application.Builder>> registrations = new ArrayList<>();

    private Builder(Class<A> aggregateType) {
      this.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType");
    }

    public <C extends Command> Builder<A> regCmd(
        CommandId<C> id, Function<CommandSpec.Init<C, A>, CommandSpec<C, A>> configure) {
      registrations.add(app -> app.regCmd(configure.apply(CommandSpec.builder(id, aggregateType))));
      return this;
    }

    public <E extends Event> Builder<A> regApply(EventId<E> id, EventHandler<E, A> handler) {
      registrations.add(app -> app.regApply(id, aggregateType, handler));
      return this;
    }

    public <E extends Event> Builder<A> regFx(EventId<E> id, EventFxHandler<E> handler) {
      registrations.add(app -> app.regFx(id, handler));
      return this;
    }

    /** Validate this aggregate's state after each command's events are applied. */
    public Builder<A> validate(Schema<? super A> schema) {
      registrations.add(app -> app.registerAggregateSchema(aggregateType, schema));
      return this;
    }

    /** The handler's context is bound to this module's aggregate — no type token needed. */
    public <Q extends Query, R> Builder<A> regQuery(
        QueryId<Q, R> id, AggregateQueryHandler<Q, R, A> handler) {
      Class<A> type = aggregateType;
      registrations.add(
          app -> app.regQuery(id, (ctx, q) -> handler.handle(new BoundContext<>(ctx, type), q)));
      return this;
    }

    public <Q extends Query, R> Builder<A> regQuery(
        QueryId<Q, R> id, Function<QuerySpec.Init<Q, R>, QuerySpec<Q, R>> configure) {
      registrations.add(app -> app.regQuery(configure.apply(QuerySpec.builder(id))));
      return this;
    }

    public Module<A> build() {
      return new Module<>(aggregateType, List.copyOf(registrations));
    }
  }

  private record BoundContext<A extends Aggregate>(Context ctx, Class<A> type)
      implements QueryContext<A> {

    @Override
    public <T> T getDeps(Dep<?, T> key) {
      return ctx.getDeps(key);
    }

    @Override
    public UUID requestId() {
      return ctx.requestId();
    }

    @Override
    public UUID interactionId() {
      return ctx.interactionId();
    }

    @Override
    public String realm() {
      return ctx.realm();
    }

    @Override
    public User user() {
      return ctx.user();
    }

    @Override
    public Map<String, String> annotations() {
      return ctx.annotations();
    }

    @Override
    public Optional<A> getAggregate(UUID aggregateId) {
      return ctx.getAggregate(aggregateId, type);
    }

    @Override
    public Optional<A> getAggregate(UUID aggregateId, long version) {
      return ctx.getAggregate(aggregateId, version, type);
    }

    @Override
    public <T extends Aggregate> Optional<T> getAggregate(UUID aggregateId, Class<T> other) {
      return ctx.getAggregate(aggregateId, other);
    }

    @Override
    public <T extends Aggregate> Optional<T> getAggregate(
        UUID aggregateId, long version, Class<T> other) {
      return ctx.getAggregate(aggregateId, version, other);
    }
  }
}

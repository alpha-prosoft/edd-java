package com.alphaprosoft.edd.core;

import com.alphaprosoft.edd.core.config.Config;

/**
 * Builds an {@link EventStore} once the owning {@link Application} is otherwise complete, so the
 * store can read app identity (e.g. {@link Application#serviceName()}) instead of being told it
 * separately. Invoked by {@code Application.Builder.build()}; the typical implementation is a
 * store's {@code fromConfig} method reference, e.g. {@code
 * .eventStore(DynamoDbEventStore::fromConfig)}.
 *
 * <p>The {@code app} passed in is fully constructed except for its stores (which are being
 * resolved): a factory may read identity and registrations but must not call {@link
 * Application#eventStore()} / {@link Application#viewStore()}.
 */
@FunctionalInterface
public interface EventStoreFactory {
  EventStore create(Application app, Config config);
}

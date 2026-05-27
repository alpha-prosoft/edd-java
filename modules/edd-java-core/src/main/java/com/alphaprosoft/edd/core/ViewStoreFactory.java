package com.alphaprosoft.edd.core;

import com.alphaprosoft.edd.core.config.Config;

/**
 * Builds a {@link ViewStore} once the owning {@link Application} is otherwise complete, so the
 * store can derive its service partition from {@link Application#serviceName()} instead of being
 * told it separately — removing the foot-gun where a forgotten service makes two services collide
 * on a shared backend. Invoked by {@code Application.Builder.build()}; the typical implementation
 * is a store's {@code fromConfig} method reference, e.g. {@code
 * .viewStore(S3ViewStore::fromConfig)}.
 *
 * <p>The {@code app} passed in is fully constructed except for its stores (which are being
 * resolved): a factory may read identity and registrations but must not call {@link
 * Application#eventStore()} / {@link Application#viewStore()}.
 */
@FunctionalInterface
public interface ViewStoreFactory {
  ViewStore create(Application app, Config config);
}

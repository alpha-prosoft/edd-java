package com.alphaprosoft.edd.core;

import com.alphaprosoft.edd.core.config.Config;

/**
 * Builds a {@link Module} at {@code Application.Builder.build()} time, given the owning {@link
 * Application} and its {@link Config}. The deferred, app-aware sibling of {@code
 * .module(SomeModule::register)} — use it when a module's registrations need app identity (e.g.
 * {@link Application#serviceName()}) or configuration: {@code .module(SomeModule::register)} where
 * {@code register(Application, Config)}.
 *
 * <p>Invoked before the app's registration maps are frozen, so the module registers into them as
 * usual. The {@code app} is identity-complete only: a factory may read {@link
 * Application#serviceName()} but must not read registrations or stores (which are still being
 * assembled).
 */
@FunctionalInterface
public interface ModuleFactory {
  Module<?> create(Application app, Config config);
}

# edd-java — gap TODO

Remaining work to reach parity with the Clojure [edd-core](https://github.com/alpha-prosoft/edd-core).
The core model is done (dispatch, events/replay, optimistic concurrency, idempotency/dedup, identities,
effects→router, multiple-rejection collection, batching, schema *validation*, local+remote deps,
top-level query routing, 4 live store backends, CRaC/SnapStart Lambda runtime, HTTP/2 transport). This
file lists only what is **still missing**, grounded in a file-by-file read of edd-core.

**Rules for every item below:** implement + tests, `mvn install` green, Spotless-clean. Compliance
suites **fail hard, never skip** when a backend is down — do not reintroduce `assumeTrue`. Don't
unilaterally skip/disable a test. The full build requires Postgres up *and* live AWS creds bridged
(`eval "$(aws configure export-credentials --format env)"`, `aws login` first).

**Conventions (established):**
- **Configuration** is unified — see [CONFIG.md](CONFIG.md). One `Config` (defaults → file → `edd.*`
  system props → `EDD_*` env, Spring-style relaxed binding). Any new decision (issuer, claim names,
  table prefix, …) is read from `Config`, never hard-coded; each module reads its own `config.sub(...)`
  namespace. New code adds its keys there.
- **Construction is via fluent builders only** (no public constructors): `InMemoryViewStore.builder().build()`,
  `PostgresEventStore.builder().dataSource(ds).build()`, `JwtAuthFilter.builder()…`/`.fromConfig(config)`, etc.
  Stores expose a `(Application, Config)` `fromConfig` factory used as `.eventStore(PostgresEventStore::fromConfig)`,
  so the store reads config **and** derives its service partition from `Application.serviceName()`. New
  stores/filters follow the same shape.

Legend: `[ ]` todo · `[~]` partial · `[x]` done-this-pass · 🚫 decided not to port.

---

## P0 — Security (do first)

- [ ] **JWT claim validation (`exp` / `aud` / `iss`).** `JwtAuthFilter` currently verifies only the
  signature (HS256 + RS256/JWKS). edd-core (`lambda/jwt.clj`) also rejects expired tokens, wrong
  audience (`:client-id`), and wrong issuer (`https://cognito-idp.{region}.amazonaws.com/{pool-id}`).
  **As-is, edd-java accepts a structurally-valid but expired token.**
  - Touch: `modules/runtime/edd-java-undertow/.../http/JwtAuthFilter.java` (add config for expected
    `aud`/`iss` + a clock; validate `exp`/`nbf`/`aud`/`iss` after signature).
  - Accept: tests for expired-token rejection, wrong-aud rejection, wrong-iss rejection, valid passes;
    clock injectable so expiry is deterministic.

---

## P1 — Behavioral parity (small, high value)

- [ ] **Query `:consumes`.** `QuerySpec` has `produces` only; edd-core validates query *input* too.
  - Touch: `query/QuerySpec.java` (add `consumes` field + builder method), `core/Application.runQuery`
    (validate before handler ⇒ throw/`invalid-query`).
  - Accept: a query with a failing `consumes` schema is rejected before the handler runs.

- [ ] **Concurrent-modification retry.** edd-java returns the failure immediately; edd-core retries the
  command up to 3× with backoff (clearing the request cache between attempts).
  - Touch: `core/Application.dispatch` (wrap `dispatchTyped`; retry only on `concurrent-modification`,
    bounded, with backoff). Coordinate with the request-cache item (clear cache per retry).
  - Accept: a handler that sees a stale version on the first attempt and succeeds on replay returns
    `Success` without the caller retrying; non-retryable failures are not retried.

- [ ] **Schema coercion / defaulting.** `Schema<T>` only returns violations. edd-core's malli **decodes**:
  applies default values and strips unknown keys before/after validation.
  - Touch: `core/Schema.java` (add an optional `T coerce(T)` or a `decode` step) and the dispatch/query
    points that currently only call `violations`.
  - Accept: a command/aggregate/query value is normalized (defaults filled, unknown keys dropped) when a
    coercing schema is registered; pure-validation schemas keep working unchanged.

- [ ] **Aggregate history / annotations.** edd-core annotates the aggregate with
  `created-on/created-by/updated-on/updated-by/invocation-id/interaction-id` and returns a `:history`
  (snapshot-before + annotated-after) per command. edd-java carries this on *events* (`EventMeta`) but
  never projects it onto the aggregate or returns history.
  - Touch: `core/Application.dispatchTyped` (compute annotations from first/last event meta, attach to
    the projected aggregate before `viewStore.update`), `CommandResponse.Success` (optional history).
  - Accept: a freshly-created vs updated aggregate carries correct created/updated provenance; response
    exposes before/after.

- [ ] **`reg-service-schema` + OpenAPI/Swagger generation.** edd-core registers a whole-service schema
  and can emit an OpenAPI 3 doc (`edd/schema/swagger.clj`).
  - Touch: new `core` registration (`regServiceSchema`) + a generator (own module or `edd-java-undertow`)
    that walks registered command/query schemas → OpenAPI JSON; optionally serve it.
  - Accept: generating the doc for the demo services produces valid OpenAPI listing commands/queries.

---

## P2 — Request-scoped cache

- [ ] **Per-request cache** (edd-core `request_cache.clj`). Cache, for the lifetime of one request:
  resolved aggregates, identity→id lookups, and dependency-query results — so a dep referenced twice in
  one command isn't fetched twice and replay within a request is consistent. Also the hook the
  concurrent-mod retry clears between attempts.
  - Touch: a request-scoped holder threaded through `ContextImpl` / `resolveDeps` / aggregate loads in
    `core/Application`; cleared per dispatch and per retry.
  - Accept: a command whose two deps issue the same query resolves it once (assert via a counting
    `RemoteServiceClient`/query handler); retry clears it.

---

## P3 — HTTP surface (`EddServer` is POST-only `/api/command`,`/api/query`, HTTP/2)

edd-core's `from-api`/`to-api` cover these; edd-java's server is minimal.

- [ ] **Health check** endpoint (`GET /health` style) for load balancers.
- [ ] **CORS** headers (`Access-Control-Allow-*`) + OPTIONS preflight handling.
- [ ] **gzip** response encoding when the client sends `Accept-Encoding: gzip`.
- [ ] **GET + query-string** query form (incl. edd-core's `key[idx]=val` array syntax) in addition to POST.
  - Touch: `modules/runtime/edd-java-undertow/.../http/EddServer.java`.
  - Accept: a test per item (health 200, CORS headers present + OPTIONS, gzip round-trip, GET query works).

---

## P4 — Operational

- [ ] **Metrics.** edd-core emits GC/EMF metrics. edd-java has structured `Telemetry` events but no
  metrics sink. Add an EMF (CloudWatch embedded-metric) or counter/timer sink, ideally fed from the
  existing `Telemetry` lifecycle events.
- [ ] **X-Ray trace-header propagation.** Capture the Lambda `lambda-runtime-trace-id` /
  `X-Amzn-Trace-Id` and propagate it through `RequestMeta` + the HTTP client. (edd-core
  `store-trace-headers!`.)

---

## P5 — Infra / richer integrations

- [ ] **Cognito specifics.** `token_use` dispatch (`id` / `access` / **`m2m`**), `custom:x-*` attribute
  extraction, `selected-role` override, group-prefix mapping (`realm-*`, `non-interactive`, `lime-*`).
  edd-java does basic `realm`/`sub`/`email`/`cognito:groups`/`role` only.
  - Touch: `JwtAuthFilter` claim mapping.
- [ ] **`:ref` (RTF) binary-content deps.** edd-core fetches referenced content over HTTP (a separate
  dep form). edd-java treats this as subsumed by remote deps — decide whether a true `Dep.ref(...)` that
  fetches binary/large content is needed; if so, add it to the deps resolver + `RemoteServiceClient`.
- [ ] **Secrets Manager** integration (`aws/secretsmanager.clj`) for config/secret loading at init.
- [ ] **JWKS auto-refresh** from the OIDC `/.well-known/jwks.json` endpoint (currently `JwksKeys` is a
  static document passed in).
- [ ] **Batch / CSV ingestion** helper (`batch/csv.clj`) — parse a CSV stream to records for bulk
  command ingestion. (Niche; only if a use case needs it.)

---

## 🚫 Decided NOT to port (owner calls)

- 🚫 **Aggregate filters** (`reg-agg-filter`) — not needed.
- 🚫 **View-store search DSL** (`simple-search`/`advanced-search`) — "this never worked"; `ViewStore`
  stays id + version snapshots.
- 🚫 **S3 response cache** + **S3-over-SQS large messages** — dedup replays the stored `CommandResponse`
  from the event store instead; a filter can add large-message handling if ever needed.
- 🚫 **Builder codegen** for command/query records — a Java-specific convenience, not edd-core parity.

---

## Project layout (reference)

```
edd-java/                              parent pom (aggregator + shared config)
  modules/
    edd-java-core/                     framework, no server deps
      com.alphaprosoft.edd.command     Command/Event, ids, handlers, CommandSpec, CommandResponse,
                                        CommandEmission (sealed: Event | Identity | Rejection)
      com.alphaprosoft.edd.query       Query, QueryId, QueryHandler, QuerySpec, Dep
      com.alphaprosoft.edd.core        Application, Module, Context, RequestMeta, User, Schema,
                                        Telemetry, EventStore, ViewStore, StoredEvent, EventMeta,
                                        RemoteServiceClient, InProcessServiceRouter
    runtime/
      edd-java-undertow/               Undertow HTTP/2 server + HTTP/2 client, JwtAuthFilter, JwksKeys
      edd-java-aws/                    CRaC/SnapStart Lambda runtime; SqsFilter/ApiFilter/S3Filter/
                                        DirectFilter ingestion; effects → Router/SqsRouter
    edd-java-json/                     storage JSON codec ({kind,name,meta,spec} envelope)
    edd-java-testkit/                  InProcessSaga — synchronous command→effects saga (tests)
    store/
      edd-store-compliance/            reusable JUnit suites every store impl must pass (fail-hard)
      edd-event-store-{memory,postgres,dynamodb}/   EventStore impls
      edd-view-store-{memory,postgres,s3}/          ViewStore impls
    demo/
      edd-demo-customer-svc/           publishes get-customer contract
      edd-demo-greeter-svc/            greet-customer (remote dep) + get-greeting + top-level routing
  Dockerfile  docker-compose.yaml      build any module's shaded jar; run the two services
```

### Storage envelope (reference)
Serializing stores persist `{ kind, name, meta, spec }`: `kind` = category (Event/Aggregate/Command/
Query/Identity), `name` = registered id, `meta` = provenance (`EventMeta`), `spec` = the plain record
(no Jackson annotations on domain types). A global `TypeRegistry` (`(kind,name) ⇄ Class`) resolves the
type; `EddJson` reads `kind`+`name` and deserializes `spec`. Meta lives in `ctx`, never on entities.

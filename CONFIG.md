# edd-java configuration

All tunable decisions — JWT issuer, which claim holds roles, a store's connection, a table prefix —
are read through one `com.alphaprosoft.edd.core.config.Config` so they can be set without code changes.
`Config` is dependency-free and lives in core; every module/filter reads its own namespace the same way.

## Sources and precedence

Lowest to highest (a later source overrides an earlier one, key by key):

1. **Defaults** — supplied in code (`Config.builder().defaults(map)`, or the fallback argument of each
   getter, e.g. `config.get("token.iss", "https://default")`).
2. **Config file** — a `.properties` file (see *File location*). Lines are dotted keys: `token.iss=…`.
3. **Java system properties** — any `-Dedd.<key>=…` (the `edd.` prefix is stripped).
4. **Environment variables** — any `EDD_<KEY>=…` (the `EDD_` prefix is stripped).

`Config.load()` assembles the file → system-properties → environment chain. Add programmatic defaults
with the builder when you need them:

```java
Config config = Config.builder()
        .defaults(Map.of("store.region", "eu-west-1"))
        .fromFile()              // edd.properties (path overridable)
        .fromSystemProperties()  // -Dedd.*
        .fromEnvironment()       // EDD_*
        .build();
```

## Relaxed binding (Spring-style)

Keys are **case-insensitive** and **dashes are ignored**. In an env var, each `_` is a path separator.
So these all set the same key, read as `config.get("token.iss")`:

| form | example |
|------|---------|
| config file | `token.iss=https://cognito-idp…` |
| system property | `-Dedd.token.iss=https://cognito-idp…` |
| environment | `EDD_TOKEN_ISS=https://cognito-idp…` |

A kebab-case key like `token.roles-claims` is matched by `EDD_TOKEN_ROLESCLAIMS` (dashes removed),
`-Dedd.token.roles-claims=…`, or `token.roles-claims=…` in the file. Because `_` is the path
separator, a multi-word field is written run-together in env form (`ROLESCLAIMS`, not `ROLES_CLAIMS`).

List values are comma-separated: `EDD_TOKEN_ROLESCLAIMS=cognito:groups,roles`.

## File location

Resolved (in order) from `EDD_CONFIG_FILE` env, `-Dedd.config.file`, or the default `edd.properties`.
The path is checked on the filesystem first, then on the classpath. If absent, the file source is
simply skipped (no error).

## Namespaces

Each module reads a sub-view so keys can't collide:

```java
JwtAuthFilter.fromConfig(config);          // reads the token.* namespace
config.sub("store");                        // the store.* namespace, read by the store factories
Config token = config.sub("token");        // token.iss -> token.get("iss")
```

A new module/filter brings its own keys and binds identically — nothing special to register.

## Known keys

### `token.*` — `JwtAuthFilter`
| key | meaning | default |
|-----|---------|---------|
| `token.iss` | required issuer; mismatch rejected | (unchecked if unset) |
| `token.aud` | accepted audience(s), comma list | (unchecked if unset) |
| `token.hs256-secret` | HS256 shared secret (enables HS256 verify) | — |
| `token.jwks` | JWKS JSON document (enables RS256 verify) | — |
| `token.realm-claim` | claim mapped to realm | `realm` |
| `token.user-id-claims` | ordered claims for user id | `sub,email` |
| `token.roles-claims` | ordered claims for roles | `cognito:groups,roles` |
| `token.role-claim` | claim for the active role | `role` |
| `token.email-claim` | claim for email | `email` |
| `token.leeway-seconds` | clock skew allowance | `0` |
| `token.require-expiry` | reject tokens without `exp` | `false` |

### `store.*` — persistent stores
| key | used by | default |
|-----|---------|---------|
| `store.url` / `store.user` / `store.password` | `PostgresEventStore` / `PostgresViewStore` | `jdbc:postgresql://localhost:5432/edd` / `edd` / `edd` |
| `store.region` | DynamoDB / S3 | `eu-west-1` |
| `store.table-prefix` | `DynamoDbEventStore` | `edd` |
| `store.bucket` | `S3ViewStore` | — |
| `store.service` | view-store key partition (overridden by `Application.serviceName()` when built via the `fromConfig` factory) | `default` |

## Construction is uniform

Every edd store/filter is built with a fluent builder (no public constructors):

```java
InMemoryEventStore.builder().build();
InMemoryViewStore.builder().build();
PostgresEventStore.builder().dataSource(ds).build();
DynamoDbEventStore.builder().client(db).tablePrefix("edd").build();
S3ViewStore.builder().client(s3).bucket("my-bucket").service("order-svc").build();
JwtAuthFilter.builder().hs256(secret).issuer("…").build();
JwtAuthFilter.fromConfig(config);
```

A store's `fromConfig` is a `(Application, Config)` factory, used at assembly time so the store reads
both the config and the app identity:

```java
Application.builder("order-svc")
        .config(config)
        .eventStore(PostgresEventStore::fromConfig)   // store.* from config
        .viewStore(S3ViewStore::fromConfig);          // store.* from config + service from serviceName()
```

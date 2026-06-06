# Service template

`sample-svc/` is a **real, compilable** edd-java service used as the scaffolding template. There is
no code generation — open it, build it, edit it like any project. `scaffold.sh` produces a new
service by copying it and string-replacing the dummy names (`com.example` / `sample` / `Sample`).

## Layout

`sample-svc` is a multi-module Maven project:

| Module | Contents |
|---|---|
| `sample-domain` | The backend-agnostic domain: command, event, aggregate, query, handler, `SampleIds`, and the `SampleModule` that wires them. Offline in-memory test. |
| `sample-postgres` | Registers `SampleModule`; wires **Postgres** event + view stores; **Undertow** HTTP/2 server (`SampleServer`). |
| `sample-aws` | Registers `SampleModule`; wires **DynamoDB** event store + **S3** view store; **AWS Lambda** (`SampleLambda`). |

The dummy names are chosen so plain string replacement stays correct: the noun is `Sample`/`sample`
and the tense lives on the fixed verb (`CreateSampleCommand`, `SampleCreatedEvent`), so
`Sample` → `Order` yields `CreateOrderCommand` / `OrderCreatedEvent`, never a mangled past tense.

## Prerequisite

Install the edd-java artifacts into your local Maven repo once (the template depends on
`com.alphaprosoft:edd-java-*`):

```bash
mvn -q -DskipTests install      # from the edd-java repo root
```

## Generate

```bash
template/scaffold.sh --group com.acme --name order --db postgres --out /tmp/out
```

- `--group` — Maven groupId **and** base Java package (classes land under `<group>.<name>`).
- `--name` — service id; drives the artifactId (`<name>-svc`), the module names, the class prefix
  (`Order…`), the edd service name, and the `create-<name>` / `<name>-created` / `get-<name>` ids.
- `--db` — `postgres` (Postgres stores + Undertow server) or `aws` (DynamoDB/S3 + Lambda). Default
  `postgres`. The scaffolder copies `sample-domain` plus the one matching backend module.
- `--out` — parent directory for the new project (default `.`).
- `--edd-version` — edd-java version to depend on (default `0.1.0-SNAPSHOT`).

## What scaffold.sh does

1. Copies the parent pom, `sample-domain`, and the chosen `sample-<db>` module.
2. Drops the other backend from the parent `<modules>`.
3. Moves `com/example/sample` to `<group path>/<name>` and renames the dummy-named files/dirs.
4. String-replaces `com.example` → group, `Sample` → class prefix, `sample` → name, and the edd
   dependency version.

## Build & run the generated service

```bash
cd <out>/<name>-svc
mvn package                                  # builds all modules, runs the in-memory tests

# --db postgres:
java -jar <name>-postgres/target/<name>-server.jar      # https://localhost:8443
# --db aws:
# deploy <name>-aws/target/<name>-lambda.jar  (java25 runtime, handler <pkg>.<Class>Lambda::handleRequest)
```

Editing the template? `cd template/sample-svc && mvn package` builds all three modules and runs the
tests, so the template never drifts out of a compiling state.

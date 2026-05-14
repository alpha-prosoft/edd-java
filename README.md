# edd-java

> Event-sourced CQRS for Java, with end-to-end compile-time type safety.

A Java port of the [edd-core](https://github.com/alpha-prosoft/edd-core) Clojure library. Where edd-core leans on Clojure's runtime data dispatch, edd-java pushes those guarantees into the type system: every command, event, aggregate, query, and dependency is a record, and the registration of each is checked by the compiler.

```mermaid
flowchart LR
    Cmd([Command<br/>record])
    H[Handler<br/>+ resolved deps]
    Evt([Events])
    Agg[Aggregate<br/>replay]
    FX[Effects<br/>regEventFx]
    Cmd2([More commands])
    Other[Other service]

    Cmd --> H
    H --> Evt
    Evt --> Agg
    Evt --> FX
    FX --> Cmd2
    Cmd2 -. same service .-> Cmd
    Cmd2 -. cross-service .-> Other

    classDef record fill:#e6f2ff,stroke:#1f6feb,color:#000
    classDef logic fill:#f6f8fa,stroke:#57606a,color:#000
    classDef ext fill:#fff8e6,stroke:#bf8700,color:#000
    class Cmd,Evt,Cmd2 record
    class H,Agg,FX logic
    class Other ext
```

> [!NOTE]
> This is an **API proposal**. The dispatch pipeline runs end-to-end and is statically verified by 8 tests, but persistence, snapshotting, schema validation, and wire serialization are not yet implemented. See [Status](#status).

---

## Why typed IDs?

The whole library hinges on one idea: **the registration key for a command is a typed singleton, not a string**.

```java
public static final CommandId<PlaceOrderCommand> PLACE_ORDER =
    CommandId.of("place-order", PlaceOrderCommand.class);
```

`PLACE_ORDER` is a `CommandId<PlaceOrderCommand>` — its type parameter knows which record this id is for. Every other piece of the framework inherits that knowledge:

```java
m.regCmd(OrderIds.PLACE_ORDER, spec -> spec
    .handler(new ShipOrderHandler())     // ❌ compile error: not CommandHandler<PlaceOrderCommand, ?>
    .build());
```

A real `enum` can't be generic, so `CommandId<C>` is built with the classic typesafe-enum pattern: a final class with static constants. Same call site, real generics.

The same trick is applied to `EventId<E>`, `QueryId<Q, R>` (response type is part of the key), and `Dep<Q, T>` (heterogeneous map keys, where `Q` ties to a `QueryId` and `T` is the result type stored in the context).

---

## Concepts

### Five building blocks

| | What it is | Naming | Implementation |
|---|---|---|---|
| **Command** | A request to do something | Imperative + `Command` suffix: `PlaceOrderCommand` | `record … implements Command` |
| **Event** | A fact that happened | Past tense + `Event` suffix: `OrderPlacedEvent` | `record … implements Event` |
| **Aggregate** | Folded state of one entity | Noun + `Aggregate` suffix: `OrderAggregate` | `record … implements Aggregate` |
| **Query** | A read request | `Get*Query` / `List*Query` / `Find*Query` | `record … implements Query` |
| **Effect** | Follow-up commands after events | `<Event>Effect` | `class … implements EventFxHandler<E>` |

### Command lifecycle

```mermaid
sequenceDiagram
    participant Client
    participant App as Application
    participant Spec as CommandSpec
    participant QH as Query handlers
    participant CH as Command handler
    participant FX as Event fx handlers

    Client->>App: dispatch(cmd)
    App->>Spec: look up by CommandId<C>
    loop for each Dep
        App->>QH: resolve dep<br/>(local query or remote service)
        QH-->>App: typed result
    end
    App->>App: apply idFn → aggregate id
    App->>CH: handle(ctx, cmd)
    CH-->>App: HandlerResult<A><br/>(Events | Error)
    App->>FX: fx(ctx, event) for each event
    FX-->>App: List<CommandEnvelope<?>>
    App-->>Client: CommandResponse<br/>(Success | Failure)
```

### Heterogeneous, typed `Context`

The context flowing through handlers is a heterogeneous map keyed by `Dep<?, T>`:

```java
public interface Context {
    <T> T get(Dep<?, T> key);   // <T> flows from the Dep, no cast in user code
}
```

```java
Customer customer = ctx.get(OrderDeps.CUSTOMER);   // returns Customer
Product   product = ctx.get(OrderDeps.PRODUCT);    // returns Product
```

### Modules group everything per aggregate

A module fixes the aggregate type once, so commands and events inside don't repeat `OrderAggregate.class`:

```java
Application app = Application.builder("order-svc")
    .module(OrderAggregate.class, OrderModule::register)
    .build();
```

Inside the module, every `regCmd` and `regEvent` is implicitly bound to `OrderAggregate`. One module per aggregate.

### Staged `CommandSpec` builder

`CommandSpec.builder(...)` returns an `Init` stage that *only* exposes `.handler(...)`. After the handler is set, you get a `Builder` with `.dep(...)`, `.idFn(...)`, and `.build()` — so the compiler forbids building a spec without a handler, and the command type `C` is fixed by the handler so the dep lambdas need **no type witness**.

```java
m.regCmd(OrderIds.PLACE_ORDER, spec -> spec
    .handler(new PlaceOrderHandler())                                   // Init → Builder<PlaceOrderCommand, OrderAggregate>
    .dep(OrderDeps.CUSTOMER, (_, cmd) -> new GetCustomerQuery(cmd.customerId()))   // cmd typed!
    .dep(OrderDeps.PRODUCT,  (_, cmd) -> new GetProductQuery(cmd.productId()))
    .idFn((_, cmd) -> cmd.orderId())
    .build());
```

Each call to `.dep(key, fn)` adds one dependency. Inside the lambda, `cmd` is the specific record type (`PlaceOrderCommand` here) because the handler instance pinned `C` for the rest of the chain.

---

## The Order example

A realistic — if simplified — order processing service that exercises every concept end-to-end. Lives in `src/test/java/com/alphaprosoft/edd/order/`.

### Package layout

```
order/
├── command/        # records that drive writes + their handlers
│   ├── PlaceOrderCommand.java         ─┐
│   ├── PlaceOrderHandler.java         ─┘ command + handler co-located
│   ├── ConfirmPaymentCommand.java     ─┐
│   ├── ConfirmPaymentHandler.java     ─┘
│   ├── CancelOrderCommand.java
│   ├── CancelOrderHandler.java
│   ├── ShipOrderCommand.java
│   ├── ShipOrderHandler.java
│   └── NotifyCustomerCommand.java     (foreign cmd targeted by an effect)
│
├── event/          # sealed event hierarchy
│   ├── OrderEvent.java                (sealed interface)
│   ├── OrderPlacedEvent.java
│   ├── PaymentConfirmedEvent.java
│   ├── OrderCancelledEvent.java
│   └── OrderShippedEvent.java
│
├── query/          # read requests
│   ├── GetOrderQuery.java             (local — answered by this service)
│   ├── GetCustomerQuery.java          (remote — answered by customer-svc)
│   └── GetProductQuery.java           (remote — answered by catalog-svc)
│
├── effect/         # one class per event → side-effect mapping
│   ├── OrderPlacedEffect.java         (→ notification)
│   ├── PaymentConfirmedEffect.java    (→ chain ShipOrderCommand locally)
│   └── OrderShippedEffect.java        (→ notification)
│
├── Customer.java   Product.java   Money.java   OrderStatus.java
├── OrderAggregate.java
├── OrderIds.java   OrderDeps.java   Services.java
└── OrderModule.java                   (configures Module<OrderAggregate>)
```

### Commands as plain records

Each command is a record implementing `Command`. The `Command` suffix keeps intent visible at every call site.

```java
public record PlaceOrderCommand(UUID id, UUID customerId, UUID productId, int quantity) implements Command {}
public record ConfirmPaymentCommand(UUID id, UUID orderId, Money amount)                  implements Command {}
public record CancelOrderCommand(UUID id, UUID orderId, String reason)                    implements Command {}
public record ShipOrderCommand(UUID id, UUID orderId, String trackingNumber)              implements Command {}
```

### Events form a sealed hierarchy

```java
public sealed interface OrderEvent extends Event
        permits OrderPlacedEvent, PaymentConfirmedEvent, OrderCancelledEvent, OrderShippedEvent {}
```

This is the second key win for the type system: the aggregate's `applyEvent` becomes a `switch` with **no `default`** — adding a new event variant turns into a compile error everywhere it needs to be handled.

```java
public OrderAggregate applyEvent(OrderEvent event) {
    return switch (event) {
        case OrderPlacedEvent e        -> new OrderAggregate(e.id(), version + 1, OrderStatus.PLACED, …);
        case PaymentConfirmedEvent _   -> new OrderAggregate(id, version + 1, OrderStatus.PAID,    …);
        case OrderCancelledEvent _     -> new OrderAggregate(id, version + 1, OrderStatus.CANCELLED, …);
        case OrderShippedEvent e       -> new OrderAggregate(id, version + 1, OrderStatus.SHIPPED, …, e.trackingNumber());
        // no default needed — sealed type, exhaustive switch
    };
}
```

`case PaymentConfirmedEvent _` uses unnamed patterns (Java 22+) when the variant's fields aren't needed.

The aggregate's state machine — implicit in the `switch` above — is:

```mermaid
stateDiagram-v2
    [*] --> NEW
    NEW --> PLACED: OrderPlacedEvent
    PLACED --> PAID: PaymentConfirmedEvent
    PLACED --> CANCELLED: OrderCancelledEvent
    PAID --> SHIPPED: OrderShippedEvent
    PAID --> CANCELLED: OrderCancelledEvent
    SHIPPED --> [*]
    CANCELLED --> [*]
```

### Dependencies are split into **keys** and **wiring**

A `Dep<Q, T>` is *just a typed key* — a name, a `QueryId`, and (for remote deps) which service to call. No query lambda baked in:

```java
public final class OrderDeps {

    public static final Dep<GetCustomerQuery, Customer> CUSTOMER =
        Dep.remote("customer", Services.CUSTOMER_SVC, OrderIds.GET_CUSTOMER);

    public static final Dep<GetProductQuery, Product> PRODUCT =
        Dep.remote("product", Services.CATALOG_SVC, OrderIds.GET_PRODUCT);

    public static final Dep<GetOrderQuery, OrderAggregate> CURRENT_ORDER =
        Dep.local("order", OrderIds.GET_ORDER);
}
```

The `<Q, T>` type parameters say: this dep produces a query of type `Q` and yields a value of type `T` (read off the `QueryId<Q, T>`).

The **how-to-build-the-query** part is provided at registration time, chained right onto the spec builder:

```java
spec.handler(new PlaceOrderHandler())
    .dep(OrderDeps.CUSTOMER, (_, cmd) -> new GetCustomerQuery(cmd.customerId()))
    .dep(OrderDeps.PRODUCT,  (_, cmd) -> new GetProductQuery(cmd.productId()))
    .build()
```

Three guarantees fall out of this split:

1. **The lambda is locked to the command type — automatically.** `new PlaceOrderHandler()` is `CommandHandler<PlaceOrderCommand, OrderAggregate>`, so after `.handler(...)` the builder is `Builder<PlaceOrderCommand, OrderAggregate>`. Every subsequent `.dep(KEY, fn)` types `cmd` as `PlaceOrderCommand` — no type witness needed.
2. **The query produced is type-checked against the dep's `Q`.** `OrderDeps.CUSTOMER` is `Dep<GetCustomerQuery, Customer>`, so `.dep(CUSTOMER, fn)` only accepts a lambda returning `GetCustomerQuery`.
3. **The result stored in `ctx` carries `T`.** `ctx.get(OrderDeps.CUSTOMER)` returns `Customer` with no cast.

Reusing the same dep across commands with different shapes is just a different lambda:

```java
// PlaceOrderCommand has a customerId field directly
.dep(OrderDeps.CUSTOMER, (_, cmd) -> new GetCustomerQuery(cmd.customerId()))

// SendInvoiceCommand has a billingCustomerId field
.dep(OrderDeps.CUSTOMER, (_, cmd) -> new GetCustomerQuery(cmd.billingCustomerId()))
```

### Handlers are tiny, typed classes

```java
public final class PlaceOrderHandler implements CommandHandler<PlaceOrderCommand, OrderAggregate> {
    @Override
    public HandlerResult<OrderAggregate> handle(Context ctx, PlaceOrderCommand cmd) {
        Customer customer = ctx.get(OrderDeps.CUSTOMER);   // typed!
        Product  product  = ctx.get(OrderDeps.PRODUCT);    // typed!

        if (product.stock() < cmd.quantity()) {
            return HandlerResult.error("insufficient-stock");
        }

        Money total = product.price().times(cmd.quantity());
        return HandlerResult.of(new OrderPlacedEvent(cmd.id(), customer.id(), product.id(), cmd.quantity(), total));
    }
}
```

### Effects produce more commands

```java
public final class PaymentConfirmedEffect implements EventFxHandler<PaymentConfirmedEvent> {
    @Override
    public List<CommandEnvelope<?>> fx(Context ctx, PaymentConfirmedEvent event) {
        return List.of(CommandEnvelope.local(
            new ShipOrderCommand(UUID.randomUUID(), event.id(), "TRACK-" + event.id())));
    }
}

public final class OrderPlacedEffect implements EventFxHandler<OrderPlacedEvent> {
    @Override
    public List<CommandEnvelope<?>> fx(Context ctx, OrderPlacedEvent event) {
        return List.of(CommandEnvelope.on(
            Services.NOTIFICATION_SVC,
            new NotifyCustomerCommand(UUID.randomUUID(), event.customerId(), "Order placed: " + event.id())));
    }
}
```

`CommandEnvelope.local(cmd)` chains within this service; `CommandEnvelope.on(svc, cmd)` targets a remote service. Both flow through `Application.dispatch` later — the framework reads them off `CommandResponse.Success.effects()`.

The full set of flows for an order, including remote deps and effect fan-out:

```mermaid
sequenceDiagram
    actor Client
    participant Order as order-svc
    participant Customer as customer-svc
    participant Catalog as catalog-svc
    participant Notif as notification-svc

    Client->>Order: PlaceOrderCommand
    Order->>+Customer: GetCustomerQuery (remote dep)
    Customer-->>-Order: Customer
    Order->>+Catalog: GetProductQuery (remote dep)
    Catalog-->>-Order: Product
    Note right of Order: emits OrderPlacedEvent
    Order--)Notif: fx → NotifyCustomerCommand

    Client->>Order: ConfirmPaymentCommand
    Note right of Order: GetOrderQuery (local dep)<br/>emits PaymentConfirmedEvent
    Order--)Order: fx → ShipOrderCommand

    Note right of Order: emits OrderShippedEvent
    Order--)Notif: fx → NotifyCustomerCommand
```

### Wiring it all up: `OrderModule`

The module's `register` function takes a `Module<OrderAggregate>` and returns it after adding everything to it. No `OrderAggregate.class` is repeated — the module already knows.

```java
public final class OrderModule {

    public static Module<OrderAggregate> register(Module<OrderAggregate> m) {
        return m
            .regCmd(OrderIds.PLACE_ORDER, spec -> spec
                    .handler(new PlaceOrderHandler())
                    .dep(OrderDeps.CUSTOMER, (_, cmd) -> new GetCustomerQuery(cmd.customerId()))
                    .dep(OrderDeps.PRODUCT,  (_, cmd) -> new GetProductQuery(cmd.productId()))
                    .build())
            .regCmd(OrderIds.CONFIRM_PAYMENT, spec -> spec
                    .handler(new ConfirmPaymentHandler())
                    .dep(OrderDeps.CURRENT_ORDER, (_, cmd) -> new GetOrderQuery(cmd.orderId()))
                    .idFn((_, cmd) -> cmd.orderId())                  // command id ≠ aggregate id
                    .build())
            .regCmd(/* CancelOrderCommand */)
            .regCmd(/* ShipOrderCommand */)
            .regEvent(OrderIds.ORDER_PLACED,       OrderModule::apply)
            .regEvent(OrderIds.PAYMENT_CONFIRMED,  OrderModule::apply)
            .regEvent(OrderIds.ORDER_CANCELLED,    OrderModule::apply)
            .regEvent(OrderIds.ORDER_SHIPPED,      OrderModule::apply)
            .regEventFx(OrderIds.ORDER_PLACED,      new OrderPlacedEffect())
            .regEventFx(OrderIds.PAYMENT_CONFIRMED, new PaymentConfirmedEffect())
            .regEventFx(OrderIds.ORDER_SHIPPED,     new OrderShippedEffect());
    }

    private static OrderAggregate apply(OrderAggregate agg, OrderEvent event) {
        OrderAggregate base = agg == null ? OrderAggregate.initial(event.id()) : agg;
        return base.applyEvent(event);
    }
}
```

Plugged into the app:

```java
Application app = Application.builder("order-svc")
    .module(OrderAggregate.class, OrderModule::register)
    .regQuery(OrderIds.GET_ORDER, getOrderHandler)
    .remoteResolver(remoteResolver)
    .build();
```

### Tests show typing in action

```java
@Test
void confirmPaymentUsesIdFnAndProducesShipFx() {
    UUID orderId = UUID.randomUUID();
    OrderAggregate placed = new OrderAggregate(
        orderId, 1, OrderStatus.PLACED, …, Money.usd(2000), null);

    Application app = build(
        /* GetOrder local handler returns the placed order */ (_, _) -> placed,
        /* no remote calls expected                        */ (_, _) -> { throw new IllegalStateException(); });

    CommandResponse resp = app.dispatch(
        new ConfirmPaymentCommand(UUID.randomUUID(), orderId, Money.usd(2000)),
        RequestMeta.newRequest());

    var success   = assertInstanceOf(CommandResponse.Success.class, resp);
    var confirmed = assertInstanceOf(PaymentConfirmedEvent.class, success.events().get(0));
    assertEquals(orderId, confirmed.id());

    var fx   = success.effects().getFirst();
    var ship = assertInstanceOf(ShipOrderCommand.class, fx.command());     // typed!
    assertEquals(orderId, ship.orderId());
}
```

---

## Java 25 features used

| Feature | Where in the code |
|---|---|
| Records | every `Command`, `Event`, `Aggregate`, `Query`, domain type |
| Sealed interfaces | `OrderEvent`, `HandlerResult`, `CommandResponse` |
| Pattern matching for switch | `Application.dispatchTyped`, `OrderAggregate.applyEvent`, `CancelOrderHandler` |
| Unnamed variable `_` | every dep lambda (`(_, cmd) -> …`), unused test stubs (`(_, _) -> …`) |
| Unnamed pattern `case T _` | `OrderAggregate.applyEvent` for events whose fields aren't read |
| Exhaustive `switch` on sealed types | aggregate apply (no `default` needed) |

---

## Mapping to edd-core (Clojure)

| edd-core | edd-java |
|---|---|
| `(edd/reg-cmd ctx :create-user handler :deps {…} :id-fn …)` | `m.regCmd(CREATE_USER, spec -> spec.handler(…).dep(KEY, fn).idFn(…).build())` |
| `(edd/reg-event ctx :user-created (fn [agg evt] …))` | `m.regEvent(USER_CREATED, (agg, evt) -> …)` |
| `(edd/reg-event-fx ctx :user-created (fn [ctx evt] …))` | `m.regEventFx(USER_CREATED, new UserCreatedEffect())` |
| `(edd/reg-query ctx :get-user handler)` | `m.regQuery(GET_USER, handler)` |
| Module composition: `(comp process-order/register payment/register)` | `Application.builder(...).module(A.class, MyModule::register)` |
| Map `{:cmd-id :create-user :id … :attrs {…}}` | `record CreateUserCommand(UUID id, …) implements Command` |
| Keyword `:create-user` | `CommandId<CreateUserCommand> CREATE_USER` |
| `(:user ctx)` after `:deps [:user dep-fn]` | `ctx.get(USER_DEP)` returning typed `User` |
| Malli schema `[:map [:cmd-id [:= :…]]]` | The record's component types |

The biggest semantic shift: edd-core dispatches on the `:cmd-id` keyword inside a map; edd-java dispatches on the record's class. The wire format remains compatible (the `cmd-id` string from `CommandId.id()` discriminates JSON payloads — when serialization is added).

---

## Status

### Done

- [x] Typed `CommandId<C>`, `EventId<E>`, `QueryId<Q, R>` registries
- [x] `Dep<Q, T>` typed keys with `Dep.local(...)` and `Dep.remote(...)` factories
- [x] `.dep(KEY, fn)` chained on the spec builder — `cmd` type is inferred from the handler instance, no witness needed
- [x] `Context.get(Dep)` heterogeneous typed map
- [x] **Staged `CommandSpec.builder`** — `.handler(...)` is required before `.deps(...)`/`.idFn(...)`/`.build()` are visible
- [x] **`Application.Builder.module(Class<A>, ...)`** — aggregate type pinned per module, one module per aggregate
- [x] Sealed `HandlerResult` (`Events` | `Error`) and `CommandResponse` (`Success` | `Failure`)
- [x] `regEventFx` + `CommandEnvelope` for cross-service effects
- [x] `RemoteResolver` seam for remote query resolution
- [x] Build-time validation: missing local query handler ⇒ `Application.build()` fails

### Not yet

- [ ] Aggregate replay from event store (snapshots + events)
- [ ] Postgres event store
- [ ] OpenSearch / Postgres view store
- [ ] Wire serialization (Jackson, format-compatible with edd-core)
- [ ] Schema validation (probably JSR-380 or a record-component walker)
- [ ] Recursive fx dispatch (currently fx are returned in the response, not chained)
- [ ] Optimistic concurrency control (`event-seq`)
- [ ] AWS Lambda runtime integration
- [ ] Test fixture analogue of `edd.test.fixture.dal`

---

## Build and run

Requires **Java 25** (latest LTS) and **Maven 3.9+**. With [direnv](https://direnv.net/) the `.envrc` puts Corretto 25 on `PATH`; otherwise set `JAVA_HOME` manually.

```bash
mvn verify            # compile, run all tests, check Spotless formatting
mvn spotless:apply    # auto-format
mvn test              # tests only
```

### Tooling

- **Java 25** — records, sealed types, pattern matching, unnamed variables/patterns
- **JUnit 5** for tests
- **Spotless** (`palantir-java-format` 2.90.0) for formatting

---

## Project layout

```
edd-java/
├── pom.xml                                  Java 25, Spotless, JUnit
├── README.md
├── .envrc                                   direnv: put Corretto 25 on PATH
└── src/
    ├── main/java/com/alphaprosoft/edd/
    │   ├── Command.java   Event.java   Aggregate.java   Query.java
    │   ├── CommandId.java   EventId.java   QueryId.java   Dep.java
    │   ├── CommandHandler.java   EventHandler.java   QueryHandler.java
    │   ├── EventFxHandler.java
    │   ├── HandlerResult.java                 (sealed)
    │   ├── CommandResponse.java               (sealed)
    │   ├── CommandSpec.java                   (staged builder: Init → Builder)
    │   ├── Module.java                        (aggregate-scoped registrations)
    │   ├── Context.java   ContextImpl.java
    │   ├── CommandEnvelope.java
    │   ├── RequestMeta.java   Service.java   RemoteResolver.java
    │   └── Application.java
    │
    └── test/java/com/alphaprosoft/edd/order/
        ├── command/   event/   query/   effect/
        ├── Customer.java   Product.java   Money.java   OrderStatus.java
        ├── OrderAggregate.java
        ├── Services.java   OrderIds.java   OrderDeps.java
        ├── OrderModule.java                (configures Module<OrderAggregate>)
        └── OrderModuleTest.java             (8 tests)
```

---

## Credits

Designed after Robert Pofuk's [edd-core](https://github.com/alpha-prosoft/edd-core) (Clojure). All concepts — commands, events, aggregates, deps, id-fn, event-fx — come from that project. The Java type-system choices (typed-enum IDs, sealed events, `Dep<Q, T>` heterogeneous map keys, `Module<A>` for aggregate scoping, staged `CommandSpec` builder with type-inferred `.dep(...)`) are what make a port worthwhile in a language without runtime data-driven dispatch.

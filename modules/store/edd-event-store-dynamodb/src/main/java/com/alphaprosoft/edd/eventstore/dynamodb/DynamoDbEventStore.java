package com.alphaprosoft.edd.eventstore.dynamodb;

import com.alphaprosoft.edd.command.CommandResponse;
import com.alphaprosoft.edd.command.Event;
import com.alphaprosoft.edd.command.Identity;
import com.alphaprosoft.edd.core.Application;
import com.alphaprosoft.edd.core.EventMeta;
import com.alphaprosoft.edd.core.EventStore;
import com.alphaprosoft.edd.core.IdentityConflictException;
import com.alphaprosoft.edd.core.OptimisticLockException;
import com.alphaprosoft.edd.core.StoredEvent;
import com.alphaprosoft.edd.core.config.Config;
import com.alphaprosoft.edd.json.EddJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.Select;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

/**
 * DynamoDB-backed {@link EventStore}. Three tables (created on demand, PAY_PER_REQUEST): {@code
 * <prefix>-events} (PK realm#aggregate, SK seq), {@code <prefix>-identities}, {@code
 * <prefix>-command-log} (PK realm#request#breadcrumbs, SK kind). Data is realm-scoped in the
 * partition key. {@code eventsByInteraction} uses a scan (fine at edd's per-interaction volumes; a
 * GSI is the production optimization).
 */
public final class DynamoDbEventStore implements EventStore {

  private final DynamoDbClient db;
  private final String events;
  private final String identities;
  private final String commandLog;

  private DynamoDbEventStore(DynamoDbClient db, String prefix) {
    this.db = db;
    this.events = prefix + "-events";
    this.identities = prefix + "-identities";
    this.commandLog = prefix + "-command-log";
    ensureTables();
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Build from the app + config: {@code store.region} (default eu-west-1), {@code
   * store.table-prefix}. {@code app} is unused — event-store isolation is deployment-level (a
   * per-service table prefix), not encoded in the key — but the signature matches {@code
   * EventStoreFactory} for uniform wiring: {@code .eventStore(DynamoDbEventStore::fromConfig)}.
   */
  public static DynamoDbEventStore fromConfig(Application app, Config config) {
    return builder().config(config).build();
  }

  public static final class Builder {
    private DynamoDbClient client;
    private String tablePrefix = "edd";

    private Builder() {}

    public Builder client(DynamoDbClient client) {
      this.client = client;
      return this;
    }

    public Builder tablePrefix(String tablePrefix) {
      this.tablePrefix = tablePrefix;
      return this;
    }

    public Builder config(Config config) {
      this.client =
          DynamoDbClient.builder()
              .region(Region.of(config.get("store.region", "eu-west-1")))
              .build();
      this.tablePrefix = config.get("store.table-prefix", tablePrefix);
      return this;
    }

    public DynamoDbEventStore build() {
      return new DynamoDbEventStore(Objects.requireNonNull(client, "client"), tablePrefix);
    }
  }

  private static AttributeValue s(String v) {
    return AttributeValue.fromS(v);
  }

  private static AttributeValue n(long v) {
    return AttributeValue.fromN(Long.toString(v));
  }

  private static String crumbs(List<Integer> breadcrumbs) {
    return breadcrumbs.stream().map(String::valueOf).collect(Collectors.joining(":"));
  }

  private void ensureTables() {
    createTable(events, "pk", ScalarAttributeType.S, "seq", ScalarAttributeType.N);
    createTable(identities, "pk", ScalarAttributeType.S, null, null);
    createTable(commandLog, "pk", ScalarAttributeType.S, "kind", ScalarAttributeType.S);
    db.waiter().waitUntilTableExists(b -> b.tableName(events));
    db.waiter().waitUntilTableExists(b -> b.tableName(identities));
    db.waiter().waitUntilTableExists(b -> b.tableName(commandLog));
  }

  private void createTable(
      String name, String pk, ScalarAttributeType pkType, String sk, ScalarAttributeType skt) {
    List<AttributeDefinition> attrs = new ArrayList<>();
    attrs.add(AttributeDefinition.builder().attributeName(pk).attributeType(pkType).build());
    List<KeySchemaElement> keys = new ArrayList<>();
    keys.add(KeySchemaElement.builder().attributeName(pk).keyType(KeyType.HASH).build());
    if (sk != null) {
      attrs.add(AttributeDefinition.builder().attributeName(sk).attributeType(skt).build());
      keys.add(KeySchemaElement.builder().attributeName(sk).keyType(KeyType.RANGE).build());
    }
    try {
      db.createTable(
          CreateTableRequest.builder()
              .tableName(name)
              .attributeDefinitions(attrs)
              .keySchema(keys)
              .billingMode(BillingMode.PAY_PER_REQUEST)
              .build());
    } catch (ResourceInUseException alreadyExists) {
      // table already present — fine
    }
  }

  @Override
  public List<StoredEvent> load(String realm, UUID aggregateId) {
    return queryEvents("pk = :pk", Map.of(":pk", s(realm + "#" + aggregateId)), true);
  }

  @Override
  public List<StoredEvent> load(String realm, UUID aggregateId, long afterSeq) {
    return queryEvents(
        "pk = :pk AND seq > :s",
        Map.of(":pk", s(realm + "#" + aggregateId), ":s", n(afterSeq)),
        true);
  }

  @Override
  public long maxEventSeq(String realm, UUID aggregateId) {
    List<Map<String, AttributeValue>> items =
        db.query(
                b ->
                    b.tableName(events)
                        .keyConditionExpression("pk = :pk")
                        .expressionAttributeValues(Map.of(":pk", s(realm + "#" + aggregateId)))
                        .scanIndexForward(false)
                        .limit(1))
            .items();
    return items.isEmpty() ? 0 : Long.parseLong(items.getFirst().get("seq").n());
  }

  @Override
  public void append(
      String realm, UUID aggregateId, long expectedVersion, List<StoredEvent> toStore) {
    long count =
        db.query(
                b ->
                    b.tableName(events)
                        .keyConditionExpression("pk = :pk")
                        .expressionAttributeValues(Map.of(":pk", s(realm + "#" + aggregateId)))
                        .select(Select.COUNT))
            .count();
    if (count != expectedVersion) {
      throw new IllegalStateException(
          "Concurrent modification of "
              + aggregateId
              + ": expected "
              + expectedVersion
              + " but found "
              + count);
    }
    for (StoredEvent se : toStore) {
      Map<String, AttributeValue> item = new LinkedHashMap<>();
      item.put("pk", s(realm + "#" + se.aggregateId()));
      item.put("seq", n(se.eventSeq()));
      item.put("realm", s(realm));
      item.put("aggregate_id", s(se.aggregateId().toString()));
      String interaction = se.meta().get(EventMeta.INTERACTION_ID);
      if (interaction != null) {
        item.put("interaction", s(interaction));
      }
      item.put("doc", s(EddJson.envelope(se.event(), se.meta().annotations())));
      try {
        db.putItem(
            b -> b.tableName(events).item(item).conditionExpression("attribute_not_exists(pk)"));
      } catch (ConditionalCheckFailedException dup) {
        throw new IllegalStateException(
            "Duplicate event-seq " + se.eventSeq() + " for " + aggregateId, dup);
      }
    }
  }

  @Override
  public void appendBatch(
      String realm, String service, List<StoredEvent> eventList, List<Identity> identityList) {
    if (eventList.isEmpty() && identityList.isEmpty()) {
      return;
    }
    List<TransactWriteItem> items = new ArrayList<>();
    for (StoredEvent se : eventList) {
      Map<String, AttributeValue> item = new LinkedHashMap<>();
      item.put("pk", s(realm + "#" + se.aggregateId()));
      item.put("seq", n(se.eventSeq()));
      item.put("realm", s(realm));
      item.put("aggregate_id", s(se.aggregateId().toString()));
      String interaction = se.meta().get(EventMeta.INTERACTION_ID);
      if (interaction != null) {
        item.put("interaction", s(interaction));
      }
      item.put("doc", s(EddJson.envelope(se.event(), se.meta().annotations())));
      items.add(
          TransactWriteItem.builder()
              .put(
                  p ->
                      p.tableName(events)
                          .item(item)
                          .conditionExpression("attribute_not_exists(pk)"))
              .build());
    }
    for (Identity id : identityList) {
      items.add(
          TransactWriteItem.builder()
              .put(
                  p ->
                      p.tableName(identities)
                          .item(
                              Map.of(
                                  "pk",
                                  s(realm + "#" + service + "#" + id.name()),
                                  "aggregate_id",
                                  s(id.aggregateId().toString())))
                          .conditionExpression("attribute_not_exists(pk) OR aggregate_id = :agg")
                          .expressionAttributeValues(
                              Map.of(":agg", s(id.aggregateId().toString()))))
              .build());
    }
    if (items.size() > 100) {
      throw new IllegalArgumentException(
          "DynamoDB transaction limit is 100 items; batch had " + items.size());
    }
    try {
      db.transactWriteItems(b -> b.transactItems(items));
    } catch (TransactionCanceledException e) {
      List<CancellationReason> reasons = e.cancellationReasons();
      for (int i = 0; i < reasons.size(); i++) {
        if (!"ConditionalCheckFailed".equals(reasons.get(i).code())) {
          continue;
        }
        if (i < eventList.size()) {
          StoredEvent se = eventList.get(i);
          throw new OptimisticLockException(se.aggregateId(), se.eventSeq());
        }
        Identity id = identityList.get(i - eventList.size());
        UUID bound = aggregateIdByIdentity(realm, service, id.name()).orElse(null);
        throw new IdentityConflictException(id.name(), bound, id.aggregateId());
      }
      throw e;
    }
  }

  @Override
  public List<StoredEvent> eventsByInteraction(String realm, UUID interactionId) {
    List<StoredEvent> out = new ArrayList<>();
    db.scanPaginator(
            b ->
                b.tableName(events)
                    .filterExpression("realm = :r AND interaction = :i")
                    .expressionAttributeValues(
                        Map.of(":r", s(realm), ":i", s(interactionId.toString()))))
        .items()
        .forEach(item -> out.add(toStoredEvent(item)));
    return out;
  }

  @Override
  public void reserveIdentities(String realm, String service, List<Identity> toReserve) {
    for (Identity id : toReserve) {
      Map<String, AttributeValue> key = Map.of("pk", s(realm + "#" + service + "#" + id.name()));
      Map<String, AttributeValue> existing =
          db.getItem(b -> b.tableName(identities).key(key)).item();
      if (existing != null && existing.containsKey("aggregate_id")) {
        UUID bound = UUID.fromString(existing.get("aggregate_id").s());
        if (!bound.equals(id.aggregateId())) {
          throw new IdentityConflictException(id.name(), bound, id.aggregateId());
        }
      }
    }
    for (Identity id : toReserve) {
      db.putItem(
          b ->
              b.tableName(identities)
                  .item(
                      Map.of(
                          "pk",
                          s(realm + "#" + service + "#" + id.name()),
                          "aggregate_id",
                          s(id.aggregateId().toString()))));
    }
  }

  @Override
  public Optional<UUID> aggregateIdByIdentity(String realm, String service, String name) {
    Map<String, AttributeValue> item =
        db.getItem(
                b ->
                    b.tableName(identities)
                        .key(Map.of("pk", s(realm + "#" + service + "#" + name))))
            .item();
    return item == null || !item.containsKey("aggregate_id")
        ? Optional.empty()
        : Optional.of(UUID.fromString(item.get("aggregate_id").s()));
  }

  @Override
  public Map<String, UUID> aggregateIdByIdentity(
      String realm, String service, Collection<String> names) {
    Map<String, UUID> out = new LinkedHashMap<>();
    for (String name : names) {
      aggregateIdByIdentity(realm, service, name).ifPresent(id -> out.put(name, id));
    }
    return out;
  }

  @Override
  public void logRequest(String realm, UUID requestId, List<Integer> breadcrumbs, Object body) {
    putLog(realm, requestId, breadcrumbs, "request", EddJson.toJson(body));
  }

  @Override
  public void logResponse(
      String realm, UUID requestId, List<Integer> breadcrumbs, CommandResponse response) {
    putLog(realm, requestId, breadcrumbs, "response", EddJson.toJson(response));
  }

  @Override
  public void logError(String realm, UUID requestId, List<Integer> breadcrumbs, Object error) {
    putLog(realm, requestId, breadcrumbs, "error", EddJson.toJson(error));
  }

  @Override
  public Optional<CommandResponse> findResponse(
      String realm, UUID requestId, List<Integer> breadcrumbs) {
    Map<String, AttributeValue> item =
        db.getItem(
                b ->
                    b.tableName(commandLog)
                        .key(
                            Map.of(
                                "pk",
                                s(realm + "#" + requestId + "#" + crumbs(breadcrumbs)),
                                "kind",
                                s("response"))))
            .item();
    return item == null || !item.containsKey("data")
        ? Optional.empty()
        : Optional.of(EddJson.fromJson(item.get("data").s(), CommandResponse.class));
  }

  private void putLog(
      String realm, UUID requestId, List<Integer> breadcrumbs, String kind, String data) {
    db.putItem(
        b ->
            b.tableName(commandLog)
                .item(
                    Map.of(
                        "pk", s(realm + "#" + requestId + "#" + crumbs(breadcrumbs)),
                        "kind", s(kind),
                        "data", s(data))));
  }

  private List<StoredEvent> queryEvents(
      String keyExpr, Map<String, AttributeValue> values, boolean ascending) {
    List<StoredEvent> out = new ArrayList<>();
    db.queryPaginator(
            b ->
                b.tableName(events)
                    .keyConditionExpression(keyExpr)
                    .expressionAttributeValues(values)
                    .scanIndexForward(ascending))
        .items()
        .forEach(item -> out.add(toStoredEvent(item)));
    return out;
  }

  private StoredEvent toStoredEvent(Map<String, AttributeValue> item) {
    UUID aggId = UUID.fromString(item.get("aggregate_id").s());
    long seq = Long.parseLong(item.get("seq").n());
    JsonNode doc = EddJson.read(item.get("doc").s());
    Event event = EddJson.spec(doc, Event.class);
    return new StoredEvent(aggId, seq, event, new EventMeta(EddJson.meta(doc)));
  }
}

package com.alphaprosoft.edd.eventstore.postgres;

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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Postgres-backed {@link EventStore}. Tables are auto-created; data is realm-scoped via a column.
 */
public final class PostgresEventStore implements EventStore {

  private final DataSource dataSource;

  private PostgresEventStore(DataSource dataSource) {
    this.dataSource = dataSource;
    initSchema();
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Build from the app + config: {@code store.url}, {@code store.user}, {@code store.password}.
   * {@code app} is unused — event-store isolation is deployment-level (a per-service
   * database/schema), not encoded in the key — but the signature matches {@code EventStoreFactory}:
   * {@code .eventStore(PostgresEventStore::fromConfig)}.
   */
  public static PostgresEventStore fromConfig(Application app, Config config) {
    return builder().config(config).build();
  }

  public static final class Builder {
    private DataSource dataSource;

    private Builder() {}

    public Builder dataSource(DataSource dataSource) {
      this.dataSource = dataSource;
      return this;
    }

    public Builder config(Config config) {
      this.dataSource = dataSourceFrom(config);
      return this;
    }

    public PostgresEventStore build() {
      return new PostgresEventStore(Objects.requireNonNull(dataSource, "dataSource"));
    }
  }

  static DataSource dataSourceFrom(Config config) {
    PGSimpleDataSource ds = new PGSimpleDataSource();
    ds.setUrl(config.get("store.url", "jdbc:postgresql://localhost:5432/edd"));
    ds.setUser(config.get("store.user", "edd"));
    ds.setPassword(config.get("store.password", "edd"));
    return ds;
  }

  private static String crumbs(List<Integer> breadcrumbs) {
    return breadcrumbs.stream().map(String::valueOf).collect(Collectors.joining(":"));
  }

  private void initSchema() {
    String ddl =
        """
                CREATE TABLE IF NOT EXISTS event_store (
                  realm varchar(255) NOT NULL, aggregate_id uuid NOT NULL, event_seq bigint NOT NULL,
                  interaction_id varchar(64), doc jsonb NOT NULL,
                  PRIMARY KEY (realm, aggregate_id, event_seq));
                CREATE TABLE IF NOT EXISTS identity_store (
                  realm varchar(255) NOT NULL, service_name varchar(255) NOT NULL, name varchar(512) NOT NULL,
                  aggregate_id uuid NOT NULL, PRIMARY KEY (realm, service_name, name));
                CREATE TABLE IF NOT EXISTS command_request_log (
                  realm varchar(255) NOT NULL, request_id uuid NOT NULL, breadcrumbs varchar(255) NOT NULL,
                  data jsonb, receive_count int NOT NULL DEFAULT 1, PRIMARY KEY (realm, request_id, breadcrumbs));
                CREATE TABLE IF NOT EXISTS command_response_log (
                  realm varchar(255) NOT NULL, request_id uuid NOT NULL, breadcrumbs varchar(255) NOT NULL,
                  data jsonb NOT NULL, PRIMARY KEY (realm, request_id, breadcrumbs));
                CREATE TABLE IF NOT EXISTS command_error_log (
                  realm varchar(255) NOT NULL, request_id uuid NOT NULL, breadcrumbs varchar(255) NOT NULL,
                  data jsonb NOT NULL, PRIMARY KEY (realm, request_id, breadcrumbs));
                """;
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps = c.prepareStatement(ddl)) {
      ps.execute();
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to initialize event store schema", e);
    }
  }

  @Override
  public List<StoredEvent> load(String realm, UUID aggregateId) {
    return query(
        "SELECT aggregate_id, event_seq, doc FROM event_store WHERE realm=? AND aggregate_id=?"
            + " ORDER BY event_seq",
        realm,
        aggregateId);
  }

  @Override
  public List<StoredEvent> load(String realm, UUID aggregateId, long afterSeq) {
    return query(
        "SELECT aggregate_id, event_seq, doc FROM event_store WHERE realm=? AND aggregate_id=?"
            + " AND event_seq > "
            + afterSeq
            + " ORDER BY event_seq",
        realm,
        aggregateId);
  }

  @Override
  public long maxEventSeq(String realm, UUID aggregateId) {
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT COALESCE(MAX(event_seq),0) FROM event_store WHERE realm=? AND aggregate_id=?")) {
      ps.setString(1, realm);
      ps.setObject(2, aggregateId);
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void append(
      String realm, UUID aggregateId, long expectedVersion, List<StoredEvent> events) {
    try (Connection c = dataSource.getConnection()) {
      c.setAutoCommit(false);
      try {
        long count;
        try (PreparedStatement ps =
            c.prepareStatement(
                "SELECT COUNT(*) FROM event_store WHERE realm=? AND aggregate_id=?")) {
          ps.setString(1, realm);
          ps.setObject(2, aggregateId);
          try (ResultSet rs = ps.executeQuery()) {
            rs.next();
            count = rs.getLong(1);
          }
        }
        if (count != expectedVersion) {
          throw new IllegalStateException(
              "Concurrent modification of "
                  + aggregateId
                  + ": expected "
                  + expectedVersion
                  + " but found "
                  + count);
        }
        try (PreparedStatement ps =
            c.prepareStatement(
                "INSERT INTO event_store(realm,aggregate_id,event_seq,interaction_id,doc)"
                    + " VALUES (?,?,?,?,?::jsonb)")) {
          for (StoredEvent se : events) {
            ps.setString(1, realm);
            ps.setObject(2, se.aggregateId());
            ps.setLong(3, se.eventSeq());
            ps.setString(4, se.meta().get(EventMeta.INTERACTION_ID));
            ps.setString(5, EddJson.envelope(se.event(), se.meta().annotations()));
            ps.addBatch();
          }
          ps.executeBatch();
        }
        c.commit();
      } catch (SQLException | RuntimeException e) {
        c.rollback();
        throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
      }
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void appendBatch(
      String realm, String service, List<StoredEvent> events, List<Identity> identities) {
    try (Connection c = dataSource.getConnection()) {
      c.setAutoCommit(false);
      try {
        for (Identity id : identities) {
          UUID existing = findIdentity(c, realm, service, id.name());
          if (existing != null && !existing.equals(id.aggregateId())) {
            throw new IdentityConflictException(id.name(), existing, id.aggregateId());
          }
        }
        try (PreparedStatement ps =
            c.prepareStatement(
                "INSERT INTO event_store(realm,aggregate_id,event_seq,interaction_id,doc)"
                    + " VALUES (?,?,?,?,?::jsonb)")) {
          for (StoredEvent se : events) {
            ps.setString(1, realm);
            ps.setObject(2, se.aggregateId());
            ps.setLong(3, se.eventSeq());
            ps.setString(4, se.meta().get(EventMeta.INTERACTION_ID));
            ps.setString(5, EddJson.envelope(se.event(), se.meta().annotations()));
            ps.addBatch();
          }
          ps.executeBatch();
        }
        try (PreparedStatement ps =
            c.prepareStatement(
                "INSERT INTO identity_store(realm,service_name,name,aggregate_id) VALUES (?,?,?,?)"
                    + " ON CONFLICT (realm,service_name,name) DO NOTHING")) {
          for (Identity id : identities) {
            ps.setString(1, realm);
            ps.setString(2, service);
            ps.setString(3, id.name());
            ps.setObject(4, id.aggregateId());
            ps.addBatch();
          }
          ps.executeBatch();
        }
        c.commit();
      } catch (SQLException e) {
        c.rollback();
        if ("23505".equals(e.getSQLState()) && !events.isEmpty()) { // unique_violation on (agg,seq)
          throw new OptimisticLockException(
              events.getFirst().aggregateId(), events.getFirst().eventSeq());
        }
        throw new IllegalStateException(e);
      } catch (RuntimeException e) {
        c.rollback();
        throw e;
      }
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public List<StoredEvent> eventsByInteraction(String realm, UUID interactionId) {
    return query(
        "SELECT aggregate_id, event_seq, doc FROM event_store WHERE realm=? AND interaction_id=?",
        realm,
        interactionId.toString());
  }

  @Override
  public void reserveIdentities(String realm, String service, List<Identity> identities) {
    try (Connection c = dataSource.getConnection()) {
      c.setAutoCommit(false);
      try {
        for (Identity id : identities) {
          UUID existing = findIdentity(c, realm, service, id.name());
          if (existing != null && !existing.equals(id.aggregateId())) {
            throw new IdentityConflictException(id.name(), existing, id.aggregateId());
          }
        }
        try (PreparedStatement ps =
            c.prepareStatement(
                "INSERT INTO identity_store(realm,service_name,name,aggregate_id) VALUES (?,?,?,?)"
                    + " ON CONFLICT (realm,service_name,name) DO NOTHING")) {
          for (Identity id : identities) {
            ps.setString(1, realm);
            ps.setString(2, service);
            ps.setString(3, id.name());
            ps.setObject(4, id.aggregateId());
            ps.addBatch();
          }
          ps.executeBatch();
        }
        c.commit();
      } catch (SQLException | RuntimeException e) {
        c.rollback();
        throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
      }
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public Optional<UUID> aggregateIdByIdentity(String realm, String service, String name) {
    try (Connection c = dataSource.getConnection()) {
      return Optional.ofNullable(findIdentity(c, realm, service, name));
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public Map<String, UUID> aggregateIdByIdentity(
      String realm, String service, Collection<String> names) {
    Map<String, UUID> out = new LinkedHashMap<>();
    try (Connection c = dataSource.getConnection()) {
      for (String name : names) {
        UUID id = findIdentity(c, realm, service, name);
        if (id != null) {
          out.put(name, id);
        }
      }
      return out;
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void logRequest(String realm, UUID requestId, List<Integer> breadcrumbs, Object body) {
    upsert(
        "INSERT INTO command_request_log(realm,request_id,breadcrumbs,data,receive_count) VALUES (?,?,?,?::jsonb,1)"
            + " ON CONFLICT (realm,request_id,breadcrumbs) DO UPDATE SET receive_count=command_request_log.receive_count+1",
        realm,
        requestId,
        crumbs(breadcrumbs),
        EddJson.toJson(body));
  }

  @Override
  public void logResponse(
      String realm, UUID requestId, List<Integer> breadcrumbs, CommandResponse response) {
    upsert(
        "INSERT INTO command_response_log(realm,request_id,breadcrumbs,data) VALUES (?,?,?,?::jsonb)"
            + " ON CONFLICT (realm,request_id,breadcrumbs) DO UPDATE SET data=EXCLUDED.data",
        realm,
        requestId,
        crumbs(breadcrumbs),
        EddJson.toJson(response));
  }

  @Override
  public void logError(String realm, UUID requestId, List<Integer> breadcrumbs, Object error) {
    upsert(
        "INSERT INTO command_error_log(realm,request_id,breadcrumbs,data) VALUES (?,?,?,?::jsonb)"
            + " ON CONFLICT (realm,request_id,breadcrumbs) DO UPDATE SET data=EXCLUDED.data",
        realm,
        requestId,
        crumbs(breadcrumbs),
        EddJson.toJson(error));
  }

  @Override
  public Optional<CommandResponse> findResponse(
      String realm, UUID requestId, List<Integer> breadcrumbs) {
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT data FROM command_response_log WHERE realm=? AND request_id=? AND breadcrumbs=?")) {
      ps.setString(1, realm);
      ps.setObject(2, requestId);
      ps.setString(3, crumbs(breadcrumbs));
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next()
            ? Optional.of(EddJson.fromJson(rs.getString(1), CommandResponse.class))
            : Optional.empty();
      }
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  private UUID findIdentity(Connection c, String realm, String service, String name)
      throws SQLException {
    try (PreparedStatement ps =
        c.prepareStatement(
            "SELECT aggregate_id FROM identity_store WHERE realm=? AND service_name=? AND name=?")) {
      ps.setString(1, realm);
      ps.setString(2, service);
      ps.setString(3, name);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? (UUID) rs.getObject(1) : null;
      }
    }
  }

  private List<StoredEvent> query(String sql, String realm, Object second) {
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setString(1, realm);
      ps.setObject(2, second);
      try (ResultSet rs = ps.executeQuery()) {
        List<StoredEvent> out = new ArrayList<>();
        while (rs.next()) {
          UUID aggId = (UUID) rs.getObject("aggregate_id");
          long seq = rs.getLong("event_seq");
          JsonNode doc = EddJson.read(rs.getString("doc"));
          Event event = EddJson.spec(doc, Event.class);
          out.add(new StoredEvent(aggId, seq, event, new EventMeta(EddJson.meta(doc))));
        }
        return out;
      }
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  private void upsert(
      String sql, String realm, UUID requestId, String breadcrumbs, String dataJson) {
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setString(1, realm);
      ps.setObject(2, requestId);
      ps.setString(3, breadcrumbs);
      ps.setString(4, dataJson);
      ps.executeUpdate();
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }
}

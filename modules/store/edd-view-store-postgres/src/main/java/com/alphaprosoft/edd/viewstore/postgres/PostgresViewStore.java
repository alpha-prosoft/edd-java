package com.alphaprosoft.edd.viewstore.postgres;

import com.alphaprosoft.edd.core.Aggregate;
import com.alphaprosoft.edd.core.Application;
import com.alphaprosoft.edd.core.ViewStore;
import com.alphaprosoft.edd.core.config.Config;
import com.alphaprosoft.edd.json.EddJson;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Postgres-backed {@link ViewStore}: the basic id/version snapshot contract with version history,
 * realm isolation, and strict validation. A richer query DSL is intentionally left as a future
 * extension interface.
 */
public final class PostgresViewStore implements ViewStore {

  public static final String DEFAULT_SERVICE = "default";

  private final DataSource dataSource;
  private final String service;

  private PostgresViewStore(DataSource dataSource, String service) {
    this.dataSource = dataSource;
    this.service = service;
    initSchema();
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Build from the app + config: {@code store.url}/{@code store.user}/{@code store.password}, with
   * the service taken from {@link Application#serviceName()} so services sharing the DB never
   * collide. Use as a factory: {@code .viewStore(PostgresViewStore::fromConfig)}.
   */
  public static PostgresViewStore fromConfig(Application app, Config config) {
    return builder().config(config).service(app.serviceName()).build();
  }

  public static final class Builder {
    private DataSource dataSource;
    private String service = DEFAULT_SERVICE;

    private Builder() {}

    public Builder dataSource(DataSource dataSource) {
      this.dataSource = dataSource;
      return this;
    }

    /** The owning service — part of the key so services sharing the DB never collide. */
    public Builder service(String service) {
      this.service = service;
      return this;
    }

    public Builder config(Config config) {
      PGSimpleDataSource ds = new PGSimpleDataSource();
      ds.setUrl(config.get("store.url", "jdbc:postgresql://localhost:5432/edd"));
      ds.setUser(config.get("store.user", "edd"));
      ds.setPassword(config.get("store.password", "edd"));
      this.dataSource = ds;
      this.service = config.get("store.service", service);
      return this;
    }

    public PostgresViewStore build() {
      return new PostgresViewStore(
          Objects.requireNonNull(dataSource, "dataSource"),
          Objects.requireNonNull(service, "service"));
    }
  }

  private void initSchema() {
    // service is part of the primary key so multiple services can share one database without
    // colliding when they reuse an aggregate id (mirrors the per-service path of the S3 store).
    String ddl =
        """
                CREATE TABLE IF NOT EXISTS aggregates (
                  service varchar(255) NOT NULL,
                  realm varchar(255) NOT NULL,
                  aggregate_id uuid NOT NULL,
                  version bigint NOT NULL,
                  data jsonb NOT NULL,
                  PRIMARY KEY (service, realm, aggregate_id, version));
                """;
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps = c.prepareStatement(ddl)) {
      ps.execute();
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to initialize view store schema", e);
    }
  }

  @Override
  public void update(String realm, Aggregate aggregate) {
    if (realm == null) {
      throw new IllegalArgumentException("realm is required");
    }
    if (aggregate == null) {
      throw new IllegalArgumentException("aggregate is required");
    }
    if (aggregate.id() == null) {
      throw new IllegalArgumentException("aggregate id is required");
    }
    if (aggregate.version() <= 0) {
      throw new IllegalArgumentException(
          "aggregate version must be positive, was " + aggregate.version());
    }
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps =
            c.prepareStatement(
                "INSERT INTO aggregates(service,realm,aggregate_id,version,data) VALUES (?,?,?,?,?::jsonb)"
                    + " ON CONFLICT (service,realm,aggregate_id,version) DO UPDATE SET data=EXCLUDED.data")) {
      ps.setString(1, service);
      ps.setString(2, realm);
      ps.setObject(3, aggregate.id());
      ps.setLong(4, aggregate.version());
      ps.setString(5, EddJson.envelope(aggregate, java.util.Map.of()));
      ps.executeUpdate();
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public <A extends Aggregate> Optional<A> getSnapshot(String realm, UUID aggregateId) {
    return queryOne(
        "SELECT data FROM aggregates WHERE service=? AND realm=? AND aggregate_id=?"
            + " ORDER BY version DESC LIMIT 1",
        realm,
        aggregateId,
        null);
  }

  @Override
  public <A extends Aggregate> Optional<A> getSnapshot(
      String realm, UUID aggregateId, long version) {
    if (version <= 0) {
      throw new IllegalArgumentException("version must be positive, was " + version);
    }
    return queryOne(
        "SELECT data FROM aggregates WHERE service=? AND realm=? AND aggregate_id=? AND version=?",
        realm,
        aggregateId,
        version);
  }

  @SuppressWarnings("unchecked")
  private <A extends Aggregate> Optional<A> queryOne(
      String sql, String realm, UUID aggregateId, Long version) {
    try (Connection c = dataSource.getConnection();
        PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setString(1, service);
      ps.setString(2, realm);
      ps.setObject(3, aggregateId);
      if (version != null) {
        ps.setLong(4, version);
      }
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next()
            ? Optional.of((A) EddJson.spec(EddJson.read(rs.getString(1)), Aggregate.class))
            : Optional.empty();
      }
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }
}

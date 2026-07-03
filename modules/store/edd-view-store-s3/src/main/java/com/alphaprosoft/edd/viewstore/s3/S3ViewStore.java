package com.alphaprosoft.edd.viewstore.s3;

import com.alphaprosoft.edd.core.Aggregate;
import com.alphaprosoft.edd.core.Application;
import com.alphaprosoft.edd.core.ViewStore;
import com.alphaprosoft.edd.core.config.Config;
import com.alphaprosoft.edd.json.EddJson;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * S3-backed {@link ViewStore}. One shared {@code <account>-<env>-aggregates} bucket holds every
 * service's aggregates; the <em>key</em> is namespaced by service so services never collide
 * (mirrors edd-core's {@code edd.s3.view-store}):
 *
 * <ul>
 *   <li>latest: {@code aggregates/<realm>/latest/<service>/<partition>/<id>.json} (overwritten)
 *   <li>history: {@code aggregates/<realm>/history/<service>/<partition>/<id>/<version>.json}
 * </ul>
 *
 * {@code partition} is the 4-bit form of the id's last hex digit (16-way fan-out). The latest
 * snapshot is read by key (no listing); an exact version is read from its history key. History is
 * written before latest, so a crash can never leave latest pointing at a missing version.
 */
public final class S3ViewStore implements ViewStore {

  public static final String DEFAULT_SERVICE = "default";

  private final S3Client s3;
  private final String bucket;
  private final String service;

  private S3ViewStore(S3Client s3, String bucket, String service) {
    this.s3 = s3;
    this.bucket = bucket;
    this.service = service;
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Build from the app + config: {@code store.region} (default eu-west-1) and {@code store.bucket},
   * with the service taken from {@link Application#serviceName()} so services sharing the bucket
   * never collide. Use as a factory: {@code .viewStore(S3ViewStore::fromConfig)}.
   */
  public static S3ViewStore fromConfig(Application app, Config config) {
    return builder().config(config).service(app.serviceName()).build();
  }

  public static final class Builder {
    private S3Client client;
    private String bucket;
    private String service = DEFAULT_SERVICE;

    private Builder() {}

    public Builder client(S3Client client) {
      this.client = client;
      return this;
    }

    public Builder bucket(String bucket) {
      this.bucket = bucket;
      return this;
    }

    /** The owning service — becomes a path segment so services sharing the bucket never collide. */
    public Builder service(String service) {
      this.service = service;
      return this;
    }

    public Builder config(Config config) {
      this.client =
          S3Client.builder().region(Region.of(config.get("store.region", "eu-west-1"))).build();
      this.bucket = config.get("store.bucket", bucket);
      this.service = config.get("store.service", service);
      return this;
    }

    public S3ViewStore build() {
      return new S3ViewStore(
          Objects.requireNonNull(client, "client"),
          Objects.requireNonNull(bucket, "bucket"),
          Objects.requireNonNull(service, "service"));
    }
  }

  private String latestKey(String realm, UUID id) {
    return "aggregates/" + realm + "/latest/" + service + "/" + partition(id) + "/" + id + ".json";
  }

  private String historyKey(String realm, UUID id, long version) {
    return "aggregates/"
        + realm
        + "/history/"
        + service
        + "/"
        + partition(id)
        + "/"
        + id
        + "/"
        + version
        + ".json";
  }

  /**
   * 16-way partition: the id's last hex digit as a 4-bit string (edd-core's hex-str-to-bit-str).
   */
  private static String partition(UUID id) {
    String s = id.toString();
    int nibble = Integer.parseInt(s.substring(s.length() - 1), 16);
    return String.format("%4s", Integer.toBinaryString(nibble)).replace(' ', '0');
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
    String json = EddJson.envelope(aggregate, java.util.Map.of());
    // history before latest: a crash after latest but before history would point latest at a
    // version with no history record.
    put(historyKey(realm, aggregate.id(), aggregate.version()), json);
    put(latestKey(realm, aggregate.id()), json);
  }

  @Override
  public <A extends Aggregate> Optional<A> getSnapshot(
      String realm, UUID aggregateId, Class<A> type) {
    return read(latestKey(realm, aggregateId), type);
  }

  @Override
  public <A extends Aggregate> Optional<A> getSnapshot(
      String realm, UUID aggregateId, long version, Class<A> type) {
    if (version <= 0) {
      throw new IllegalArgumentException("version must be positive, was " + version);
    }
    return read(historyKey(realm, aggregateId, version), type);
  }

  private void put(String key, String json) {
    s3.putObject(
        b -> b.bucket(bucket).key(key), RequestBody.fromString(json, StandardCharsets.UTF_8));
  }

  private <A extends Aggregate> Optional<A> read(String key, Class<A> type) {
    try {
      String json = s3.getObjectAsBytes(b -> b.bucket(bucket).key(key)).asUtf8String();
      return Optional.of(type.cast(EddJson.spec(EddJson.read(json), Aggregate.class)));
    } catch (NoSuchKeyException e) {
      return Optional.empty();
    }
  }
}

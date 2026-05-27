package com.alphaprosoft.edd.e2e.pong;

import com.alphaprosoft.edd.core.Aggregate;
import java.util.UUID;

/** pong-svc aggregate. {@code version} is library-owned; apply methods never set it. */
public record PongAggregate(
    UUID id,
    long version,
    String last,
    long hops,
    long pongCount,
    String value,
    String pingValue,
    long pingVersion)
    implements Aggregate {

  public static PongAggregate ponged(PongAggregate agg, PongedEvent e) {
    return builder(agg)
        .id(e.id())
        .last("ponged")
        .hops(e.hops())
        .pongCount(agg == null ? 1 : agg.pongCount + 1)
        .build();
  }

  public static PongAggregate valueSet(PongAggregate agg, PongValueSetEvent e) {
    return builder(agg).id(e.id()).last("value-set").value(e.value()).build();
  }

  public static PongAggregate combined(PongAggregate agg, CombinedEvent e) {
    return builder(agg)
        .id(e.id())
        .last("combined")
        .value(e.pongValue())
        .pingValue(e.pingValue())
        .pingVersion(e.pingVersion())
        .build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(PongAggregate existing) {
    return new Builder(existing);
  }

  public static final class Builder {

    private UUID id;
    private long version;
    private String last;
    private long hops;
    private long pongCount;
    private String value;
    private String pingValue;
    private long pingVersion;

    private Builder() {}

    private Builder(PongAggregate a) {
      if (a != null) {
        this.id = a.id;
        this.version = a.version;
        this.last = a.last;
        this.hops = a.hops;
        this.pongCount = a.pongCount;
        this.value = a.value;
        this.pingValue = a.pingValue;
        this.pingVersion = a.pingVersion;
      }
    }

    public Builder id(UUID id) {
      this.id = id;
      return this;
    }

    public Builder last(String last) {
      this.last = last;
      return this;
    }

    public Builder hops(long hops) {
      this.hops = hops;
      return this;
    }

    public Builder pongCount(long pongCount) {
      this.pongCount = pongCount;
      return this;
    }

    public Builder value(String value) {
      this.value = value;
      return this;
    }

    public Builder pingValue(String pingValue) {
      this.pingValue = pingValue;
      return this;
    }

    public Builder pingVersion(long pingVersion) {
      this.pingVersion = pingVersion;
      return this;
    }

    public PongAggregate build() {
      return new PongAggregate(id, version, last, hops, pongCount, value, pingValue, pingVersion);
    }
  }
}

package com.alphaprosoft.edd.e2e.ping;

import com.alphaprosoft.edd.core.Aggregate;
import java.util.UUID;

/**
 * ping-svc aggregate. {@code version} is owned by the library (stamped after replay), so apply
 * methods never set it. {@code builder(existing)} is null-safe, so the first event folds from an
 * empty builder.
 */
public record PingAggregate(
    UUID id,
    long version,
    String last,
    long hops,
    long pingCount,
    String value,
    String name,
    long score)
    implements Aggregate {

  public static PingAggregate pinged(PingAggregate agg, PingedEvent e) {
    return builder(agg)
        .id(e.id())
        .last("pinged")
        .hops(e.hops())
        .pingCount(agg == null ? 1 : agg.pingCount + 1)
        .build();
  }

  public static PingAggregate valueSet(PingAggregate agg, PingValueSetEvent e) {
    return builder(agg).id(e.id()).last("value-set").value(e.value()).build();
  }

  public static PingAggregate broadcasted(PingAggregate agg, BroadcastedEvent e) {
    return builder(agg).id(e.id()).last("broadcasted").build();
  }

  public static PingAggregate nameClaimed(PingAggregate agg, NameClaimedEvent e) {
    return builder(agg).id(e.id()).last("name-claimed").name(e.name()).build();
  }

  public static PingAggregate scoreSet(PingAggregate agg, ScoreSetEvent e) {
    return builder(agg).id(e.id()).last("score-set").score(e.score()).build();
  }

  public static PingAggregate objectRecorded(PingAggregate agg, ObjectRecordedEvent e) {
    return builder(agg).id(e.id()).last("object-recorded").value(e.key()).build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(PingAggregate existing) {
    return new Builder(existing);
  }

  public static final class Builder {

    private UUID id;
    private long version;
    private String last;
    private long hops;
    private long pingCount;
    private String value;
    private String name;
    private long score;

    private Builder() {}

    private Builder(PingAggregate a) {
      if (a != null) {
        this.id = a.id;
        this.version = a.version;
        this.last = a.last;
        this.hops = a.hops;
        this.pingCount = a.pingCount;
        this.value = a.value;
        this.name = a.name;
        this.score = a.score;
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

    public Builder pingCount(long pingCount) {
      this.pingCount = pingCount;
      return this;
    }

    public Builder value(String value) {
      this.value = value;
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder score(long score) {
      this.score = score;
      return this;
    }

    public PingAggregate build() {
      return new PingAggregate(id, version, last, hops, pingCount, value, name, score);
    }
  }
}

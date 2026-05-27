package com.alphaprosoft.edd.e2e.ping;

import com.alphaprosoft.edd.command.Event;
import java.util.UUID;

/** eventId {@code ping-object-recorded}. */
public record ObjectRecordedEvent(UUID id, String bucket, String key) implements Event {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private UUID id;
    private String bucket;
    private String key;

    private Builder() {}

    public Builder id(UUID id) {
      this.id = id;
      return this;
    }

    public Builder bucket(String bucket) {
      this.bucket = bucket;
      return this;
    }

    public Builder key(String key) {
      this.key = key;
      return this;
    }

    public ObjectRecordedEvent build() {
      return new ObjectRecordedEvent(id, bucket, key);
    }
  }
}

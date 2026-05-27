package com.alphaprosoft.edd.e2e.ping;

import com.alphaprosoft.edd.command.Command;
import java.util.UUID;

/**
 * Produced from an S3 upload (bucket + key) by the S3 ingestion filter. cmdId {@code
 * ping-object-uploaded}.
 */
public record PingObjectUploadedCommand(UUID id, String bucket, String key) implements Command {

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

    public PingObjectUploadedCommand build() {
      return new PingObjectUploadedCommand(id, bucket, key);
    }
  }
}

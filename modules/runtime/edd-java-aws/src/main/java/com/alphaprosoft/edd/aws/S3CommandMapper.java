package com.alphaprosoft.edd.aws;

import com.alphaprosoft.edd.command.Command;

/**
 * Maps an S3 object (bucket + key) to the command to dispatch — supplied when configuring the S3
 * source.
 */
@FunctionalInterface
public interface S3CommandMapper {
  Command map(String bucket, String key);
}

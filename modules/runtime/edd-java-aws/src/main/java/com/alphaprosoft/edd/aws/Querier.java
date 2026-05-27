package com.alphaprosoft.edd.aws;

import com.alphaprosoft.edd.core.RequestMeta;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * What a {@link IngestFilter} calls to answer a read: resolve the {@code queryId} to its registered
 * query, deserialize the {@code query} JSON to that type, run it, and return the result. Supplied
 * by the runtime alongside the command {@link Processor}; only the {@link ApiFilter} needs it
 * (SQS/S3/ Direct ingest commands only).
 */
@FunctionalInterface
public interface Querier {
  Object query(String queryId, JsonNode query, RequestMeta meta);
}

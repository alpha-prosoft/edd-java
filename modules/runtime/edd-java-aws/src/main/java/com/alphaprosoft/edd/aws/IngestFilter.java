package com.alphaprosoft.edd.aws;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * An infra-specific ingestion filter: decode the raw event, drive each command through the {@link
 * Processor} (which dispatches + routes effects to the router), and build the response in the shape
 * that event source expects — an SQS partial-batch response, an API HTTP response, etc. Filters are
 * tried in order; the first that {@link #handles} the event processes it.
 */
public interface IngestFilter {

  boolean handles(JsonNode event);

  Object process(JsonNode event, Processor processor);
}

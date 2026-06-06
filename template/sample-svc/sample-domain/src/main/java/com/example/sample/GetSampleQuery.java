package com.example.sample;

import com.alphaprosoft.edd.query.Query;
import java.util.UUID;

/** Read a sample aggregate by id. queryId {@code get-sample}. */
public record GetSampleQuery(UUID id) implements Query {}

package com.alphaprosoft.edd.demo.greeter;

import com.alphaprosoft.edd.query.Query;
import java.util.UUID;

/** Reads the greeter aggregate (a projection) back from the view store. */
public record GetGreetingQuery(UUID id) implements Query {}

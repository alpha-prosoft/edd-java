package com.alphaprosoft.edd.demo.greeter;

import com.alphaprosoft.edd.query.Query;
import java.util.UUID;

/** A query whose dependency is satisfied by another service (customer-svc). */
public record GetCustomerNameQuery(UUID customerId) implements Query {}

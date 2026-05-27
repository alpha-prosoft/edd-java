package com.alphaprosoft.edd.demo.customer;

import com.alphaprosoft.edd.query.Query;
import java.util.UUID;

public record GetCustomerQuery(UUID id) implements Query {}

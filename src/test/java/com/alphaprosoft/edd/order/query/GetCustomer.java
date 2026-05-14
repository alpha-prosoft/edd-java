package com.alphaprosoft.edd.order.query;

import java.util.UUID;

import com.alphaprosoft.edd.Query;

public record GetCustomer(UUID id) implements Query {}

package com.alphaprosoft.edd.crossservice;

import com.alphaprosoft.edd.query.Query;
import java.util.UUID;

public record CustomerNameQuery(UUID customerId) implements Query {}

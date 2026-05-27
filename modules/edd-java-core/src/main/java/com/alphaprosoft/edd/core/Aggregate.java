package com.alphaprosoft.edd.core;

import java.util.UUID;

public interface Aggregate {
  UUID id();

  long version();
}

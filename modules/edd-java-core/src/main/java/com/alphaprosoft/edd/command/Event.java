package com.alphaprosoft.edd.command;

import java.util.UUID;

public non-sealed interface Event extends CommandEmission {
  UUID id();
}

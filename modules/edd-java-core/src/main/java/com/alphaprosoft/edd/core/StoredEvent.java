package com.alphaprosoft.edd.core;

import com.alphaprosoft.edd.command.Event;
import java.util.UUID;

/**
 * A domain {@link Event} as persisted: bound to its aggregate, ordered by {@code eventSeq}
 * (1-based, contiguous per aggregate), and stamped with {@link EventMeta}.
 */
public record StoredEvent(UUID aggregateId, long eventSeq, Event event, EventMeta meta) {}

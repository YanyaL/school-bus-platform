package com.schoolbus.cdcsync.event;

import java.time.Instant;

public record TripCacheInvalidationEvent(
        String eventId,
        String database,
        String table,
        String operation,
        Instant occurredAt,
        String binlogFile,
        long binlogOffset
) implements CdcEvent {
}

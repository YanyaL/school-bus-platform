package com.schoolbus.cdcsync.event;

import java.time.Instant;

public record ConsumedEventRecordedEvent(
        String eventId,
        String consumerName,
        String consumedEventId,
        Instant consumedAt,
        Instant occurredAt,
        String binlogFile,
        long binlogOffset
) implements CdcEvent {
}

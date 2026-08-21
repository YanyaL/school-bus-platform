package com.schoolbus.cdcsync.event;

import java.time.Instant;

public sealed interface CdcEvent
        permits ConsumedEventRecordedEvent, TripCacheInvalidationEvent {

    String eventId();

    Instant occurredAt();
}

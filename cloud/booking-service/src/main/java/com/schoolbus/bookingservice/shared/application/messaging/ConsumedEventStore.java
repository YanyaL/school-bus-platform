package com.schoolbus.bookingservice.shared.application.messaging;

import java.time.Instant;

public interface ConsumedEventStore {

    boolean exists(String consumerName, String eventId);

    boolean insertIfAbsent(
            String consumerName,
            String eventId,
            Instant consumedAt
    );
}

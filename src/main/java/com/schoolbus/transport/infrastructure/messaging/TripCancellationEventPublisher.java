package com.schoolbus.transport.infrastructure.messaging;

import com.schoolbus.payment.infrastructure.outbox.ClaimedOutboxEvent;

public interface TripCancellationEventPublisher {

    void publish(ClaimedOutboxEvent event);
}

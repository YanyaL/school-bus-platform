package com.schoolbus.transport.infrastructure.messaging;

import com.schoolbus.payment.infrastructure.outbox.ClaimedOutboxEvent;

public interface TripPublicationEventPublisher {
    void publish(ClaimedOutboxEvent event);
}

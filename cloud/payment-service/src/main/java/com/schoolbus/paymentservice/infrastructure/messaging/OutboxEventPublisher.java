package com.schoolbus.paymentservice.infrastructure.messaging;

import com.schoolbus.paymentservice.infrastructure.outbox.ClaimedOutboxEvent;

public interface OutboxEventPublisher {

    void publish(ClaimedOutboxEvent event);
}

package com.schoolbus.payment.infrastructure.messaging;

import com.schoolbus.payment.infrastructure.outbox.ClaimedOutboxEvent;

public interface OutboxEventPublisher {

    void publish(ClaimedOutboxEvent event);
}

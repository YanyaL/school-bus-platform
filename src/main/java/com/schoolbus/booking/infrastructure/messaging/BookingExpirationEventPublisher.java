package com.schoolbus.booking.infrastructure.messaging;

import com.schoolbus.payment.infrastructure.outbox.ClaimedOutboxEvent;

public interface BookingExpirationEventPublisher {

    void publish(ClaimedOutboxEvent event);
}

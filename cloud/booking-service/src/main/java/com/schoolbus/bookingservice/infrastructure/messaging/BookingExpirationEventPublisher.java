package com.schoolbus.bookingservice.infrastructure.messaging;

import com.schoolbus.bookingservice.support.payment.infrastructure.outbox.ClaimedOutboxEvent;

public interface BookingExpirationEventPublisher {

    void publish(ClaimedOutboxEvent event);
}

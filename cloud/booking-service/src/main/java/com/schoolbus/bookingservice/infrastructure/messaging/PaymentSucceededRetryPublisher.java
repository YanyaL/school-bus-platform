package com.schoolbus.bookingservice.infrastructure.messaging;

import org.springframework.amqp.core.Message;

public interface PaymentSucceededRetryPublisher {

    void scheduleRetry(Message message);
}

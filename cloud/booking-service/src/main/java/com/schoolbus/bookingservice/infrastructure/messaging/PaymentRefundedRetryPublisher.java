package com.schoolbus.bookingservice.infrastructure.messaging;

import org.springframework.amqp.core.Message;

public interface PaymentRefundedRetryPublisher {

    void scheduleRetry(Message message);
}

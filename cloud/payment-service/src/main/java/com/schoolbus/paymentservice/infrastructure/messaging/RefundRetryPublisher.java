package com.schoolbus.paymentservice.infrastructure.messaging;

import org.springframework.amqp.core.Message;

public interface RefundRetryPublisher {

    void scheduleRetry(Message message);
}

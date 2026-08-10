package com.schoolbus.payment.infrastructure.messaging;

import org.springframework.amqp.core.Message;

public interface RefundRetryPublisher {

    void scheduleRetry(Message message);
}

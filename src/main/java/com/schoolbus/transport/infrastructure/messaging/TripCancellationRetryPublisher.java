package com.schoolbus.transport.infrastructure.messaging;

import org.springframework.amqp.core.Message;

public interface TripCancellationRetryPublisher {

    void scheduleRetry(
            Message message,
            TripCancellationRetryLane lane
    );
}

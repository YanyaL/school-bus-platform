package com.schoolbus.paymentservice.application.refund;

import java.time.Instant;

public interface ConsumedEventRepository {

    boolean exists(String consumerName, String eventId);

    boolean insertIfAbsent(
            String consumerName,
            String eventId,
            Instant consumedAt
    );
}

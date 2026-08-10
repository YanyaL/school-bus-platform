package com.schoolbus.payment.infrastructure.outbox;

import java.time.Instant;
import java.util.Objects;

public record ClaimedOutboxEvent(
        long id,
        String eventId,
        String eventType,
        String payload,
        String traceId,
        int retryCount,
        Instant occurredAt,
        long claimedVersion
) {

    public ClaimedOutboxEvent {
        if (id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        eventId = requireText(eventId, "eventId");
        eventType = requireText(eventType, "eventType");
        payload = requireText(payload, "payload");
        if (traceId != null && traceId.isBlank()) {
            traceId = null;
        }
        if (retryCount < 0) {
            throw new IllegalArgumentException(
                    "retryCount must not be negative"
            );
        }
        occurredAt = Objects.requireNonNull(
                occurredAt,
                "occurredAt must not be null"
        );
        if (claimedVersion <= 0) {
            throw new IllegalArgumentException(
                    "claimedVersion must be positive"
            );
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}

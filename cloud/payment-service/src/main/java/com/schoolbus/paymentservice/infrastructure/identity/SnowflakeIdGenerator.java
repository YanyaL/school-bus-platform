package com.schoolbus.paymentservice.infrastructure.identity;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Generates positive 63-bit IDs: timestamp (41) + worker (10) + sequence (12).
 */
public final class SnowflakeIdGenerator {

    private static final long EPOCH_MILLIS = Instant.parse(
            "2025-01-01T00:00:00Z"
    ).toEpochMilli();
    private static final long MAX_WORKER_ID = 1023L;
    private static final long SEQUENCE_MASK = 4095L;
    private static final long WORKER_SHIFT = 12L;
    private static final long TIMESTAMP_SHIFT = 22L;

    private final long workerId;
    private final Clock clock;
    private long lastTimestamp = -1L;
    private long sequence;

    public SnowflakeIdGenerator(long workerId, Clock clock) {
        if (workerId < 0L || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException(
                    "workerId must be between 0 and " + MAX_WORKER_ID
            );
        }
        this.workerId = workerId;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public synchronized long nextId() {
        long timestamp = clock.millis();
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException(
                    "clock moved backwards by "
                            + (lastTimestamp - timestamp) + "ms"
            );
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1L) & SEQUENCE_MASK;
            if (sequence == 0L) {
                timestamp = waitForNextMillis(timestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH_MILLIS) << TIMESTAMP_SHIFT)
                | (workerId << WORKER_SHIFT)
                | sequence;
    }

    private long waitForNextMillis(long timestamp) {
        long current = clock.millis();
        while (current <= timestamp) {
            Thread.onSpinWait();
            current = clock.millis();
        }
        return current;
    }
}

package com.schoolbus.iamservice.infrastructure.identity;

import java.time.Instant;

public final class SnowflakeIdGenerator {

    private static final long CUSTOM_EPOCH =
            Instant.parse("2026-01-01T00:00:00Z")
                    .toEpochMilli();

    private static final int TIMESTAMP_BITS = 41;
    private static final int WORKER_ID_BITS = 10;
    private static final int SEQUENCE_BITS = 12;

    private static final long MAX_WORKER_ID =
            (1L << WORKER_ID_BITS) - 1;
    private static final long SEQUENCE_MASK =
            (1L << SEQUENCE_BITS) - 1;
    private static final long MAX_TIMESTAMP =
            (1L << TIMESTAMP_BITS) - 1;

    private static final int WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final int TIMESTAMP_SHIFT =
            WORKER_ID_BITS + SEQUENCE_BITS;

    private final long workerId;

    private long lastTimestamp = -1L;
    private long sequence;

    public SnowflakeIdGenerator(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException(
                    "workerId must be between 0 and "
                            + MAX_WORKER_ID
            );
        }
        this.workerId = workerId;
    }

    public synchronized long nextId() {
        long currentTimestamp = System.currentTimeMillis();

        if (currentTimestamp < lastTimestamp) {
            throw new IllegalStateException(
                    "system clock moved backwards"
            );
        }

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                currentTimestamp = waitUntilNextMillis(
                        lastTimestamp
                );
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;
        long elapsedTimestamp = currentTimestamp - CUSTOM_EPOCH;
        if (elapsedTimestamp < 0
                || elapsedTimestamp > MAX_TIMESTAMP) {
            throw new IllegalStateException(
                    "timestamp is outside the supported range"
            );
        }

        return (elapsedTimestamp << TIMESTAMP_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long waitUntilNextMillis(long previousTimestamp) {
        long currentTimestamp = System.currentTimeMillis();
        while (currentTimestamp <= previousTimestamp) {
            Thread.onSpinWait();
            currentTimestamp = System.currentTimeMillis();
        }
        return currentTimestamp;
    }
}

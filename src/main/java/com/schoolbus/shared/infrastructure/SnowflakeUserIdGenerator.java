package com.schoolbus.shared.infrastructure.identity;

import com.schoolbus.shared.domain.identity.UserId;
import com.schoolbus.shared.domain.identity.UserIdGenerator;

import java.time.Instant;

public final class SnowflakeUserIdGenerator implements UserIdGenerator{
    private static final long CUSTOM_EPOCH = Instant.parse("2026-01-01T00:00:00.00Z").toEpochMilli();

    private static final int TIMESTAMP_BITS = 41;
    private static final int WORKER_ID_BITS = 10;
    private static final int SEQUENCE_BITS = 12;

    private static final long MAX_WORKER_ID =
        (1L << WORKER_ID_BITS) - 1;

    private static final long SEQUENCE_MASK =
        (1L << SEQUENCE_BITS) - 1;

    private static final long MAX_TIMESTAMP =
        (1L << TIMESTAMP_BITS) - 1;

    private static final int WORKER_ID_SHIFT =
        SEQUENCE_BITS;

    private static final int TIMESTAMP_SHIFT =
        WORKER_ID_BITS + SEQUENCE_BITS;

    private final long workerId;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public  SnowflakeUserIdGenerator(long workerId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException("workerId can't be greater than MAX_WORKER_ID" + MAX_WORKER_ID);
        }

        this.workerId = workerId;
    }
    @Override
    public synchronized UserId nextId() {
        long currentTimestamp =
            System.currentTimeMillis();

        if (currentTimestamp < lastTimestamp) {
            throw new IllegalStateException(
                "system clock moved backwards"
            );
        }

        if (currentTimestamp == lastTimestamp){
            sequence = (sequence + 1) & SEQUENCE_MASK;

            if  (sequence == 0) {

            }
        }
    }
}

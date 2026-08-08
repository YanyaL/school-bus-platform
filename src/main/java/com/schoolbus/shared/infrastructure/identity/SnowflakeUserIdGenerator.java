package com.schoolbus.shared.infrastructure.identity;

import com.schoolbus.shared.domain.identity.UserId;
import com.schoolbus.shared.domain.identity.UserIdGenerator;

import java.util.Objects;

public final class SnowflakeUserIdGenerator
        implements UserIdGenerator {

    private final SnowflakeIdGenerator idGenerator;

    public SnowflakeUserIdGenerator(long workerId) {
        this(new SnowflakeIdGenerator(workerId));
    }

    public SnowflakeUserIdGenerator(
            SnowflakeIdGenerator idGenerator
    ) {
        this.idGenerator = Objects.requireNonNull(
                idGenerator,
                "idGenerator must not be null"
        );
    }

    @Override
    public UserId nextId() {
        return UserId.of(idGenerator.nextId());
    }
}

package com.schoolbus.payment.infrastructure.identity;

import com.schoolbus.payment.domain.PaymentId;
import com.schoolbus.payment.domain.PaymentIdGenerator;
import com.schoolbus.shared.infrastructure.identity.SnowflakeIdGenerator;

import java.util.Objects;

public final class SnowflakePaymentIdGenerator
        implements PaymentIdGenerator {

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public SnowflakePaymentIdGenerator(
            SnowflakeIdGenerator snowflakeIdGenerator
    ) {
        this.snowflakeIdGenerator = Objects.requireNonNull(
                snowflakeIdGenerator,
                "snowflakeIdGenerator must not be null"
        );
    }

    @Override
    public PaymentId nextId() {
        return PaymentId.of(snowflakeIdGenerator.nextId());
    }
}

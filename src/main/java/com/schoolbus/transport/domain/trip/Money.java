package com.schoolbus.transport.domain.trip;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(BigDecimal amount) {

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    "amount must not be negative"
            );
        }
        amount = amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount));
    }
}

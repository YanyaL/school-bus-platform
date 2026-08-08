package com.schoolbus.booking.domain.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record BookingAmount(BigDecimal amount) {

    public BookingAmount {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    "amount must not be negative"
            );
        }
        amount = amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static BookingAmount of(String amount) {
        return new BookingAmount(new BigDecimal(amount));
    }
}

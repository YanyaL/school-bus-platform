package com.schoolbus.transport.application.trip.admin;

import com.schoolbus.transport.domain.trip.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record CreateDraftTripCommand(
        String vehicleNo,
        String routeNo,
        Instant departureTime,
        Instant bookingDeadline,
        BigDecimal price
) {

    public CreateDraftTripCommand {
        vehicleNo = requireText(vehicleNo, "vehicleNo");
        routeNo = requireText(routeNo, "routeNo");
        Objects.requireNonNull(departureTime, "departureTime must not be null");
        Objects.requireNonNull(
                bookingDeadline,
                "bookingDeadline must not be null"
        );
        Objects.requireNonNull(price, "price must not be null");
        if (!bookingDeadline.isBefore(departureTime)) {
            throw new IllegalArgumentException(
                    "bookingDeadline must be before departureTime"
            );
        }
    }

    Money priceMoney() {
        return new Money(price);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    name + " must not be blank"
            );
        }
        return value.strip();
    }
}

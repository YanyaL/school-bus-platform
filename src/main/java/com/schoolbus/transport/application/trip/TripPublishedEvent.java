package com.schoolbus.transport.application.trip;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable publication snapshot. The wire contract is versioned independently of the aggregate. */
public record TripPublishedEvent(
        long tripId,
        UUID tripNumber,
        long tripVersion,
        List<String> seatNumbers,
        BigDecimal price,
        Instant bookingDeadline,
        Instant departureTime,
        Instant publishedAt
) {
    public static final String TYPE = "TripPublished";
    public static final int SCHEMA_VERSION = 1;

    public TripPublishedEvent {
        if (tripId <= 0 || tripVersion <= 0) {
            throw new IllegalArgumentException("tripId and tripVersion must be positive");
        }
        Objects.requireNonNull(tripNumber, "tripNumber must not be null");
        seatNumbers = List.copyOf(Objects.requireNonNull(seatNumbers, "seatNumbers must not be null"));
        if (seatNumbers.isEmpty() || seatNumbers.stream().anyMatch(String::isBlank)
                || new HashSet<>(seatNumbers).size() != seatNumbers.size()) {
            throw new IllegalArgumentException("seatNumbers must be nonempty, unique and nonblank");
        }
        price = Objects.requireNonNull(price, "price must not be null").setScale(2, RoundingMode.UNNECESSARY);
        if (price.signum() < 0) {
            throw new IllegalArgumentException("price must not be negative");
        }
        Objects.requireNonNull(publishedAt, "publishedAt must not be null");
        Objects.requireNonNull(bookingDeadline, "bookingDeadline must not be null");
        Objects.requireNonNull(departureTime, "departureTime must not be null");
        if (!publishedAt.isBefore(bookingDeadline) || !bookingDeadline.isBefore(departureTime)) {
            throw new IllegalArgumentException("publication must precede booking deadline and departure");
        }
    }

    public int totalSeats() {
        return seatNumbers.size();
    }
}

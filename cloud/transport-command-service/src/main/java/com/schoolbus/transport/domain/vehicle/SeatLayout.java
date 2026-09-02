package com.schoolbus.transport.domain.vehicle;

import java.util.List;
import java.util.stream.IntStream;

public record SeatLayout(
        int seatCount,
        List<String> seatNumbers
) {

    public static final int MIN_SEAT_COUNT = 1;
    public static final int MAX_SEAT_COUNT = 120;

    public SeatLayout {
        if (seatCount < MIN_SEAT_COUNT || seatCount > MAX_SEAT_COUNT) {
            throw new IllegalArgumentException(
                    "seatCount must be between "
                            + MIN_SEAT_COUNT
                            + " and "
                            + MAX_SEAT_COUNT
            );
        }
        seatNumbers = List.copyOf(seatNumbers);
        if (seatNumbers.size() != seatCount) {
            throw new IllegalArgumentException(
                    "seatNumbers size must match seatCount"
            );
        }
    }

    public static SeatLayout of(int seatCount) {
        List<String> seatNumbers = IntStream
                .rangeClosed(1, seatCount)
                .mapToObj(Integer::toString)
                .toList();
        return new SeatLayout(seatCount, seatNumbers);
    }
}

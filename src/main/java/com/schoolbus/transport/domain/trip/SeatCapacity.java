package com.schoolbus.transport.domain.trip;

public record SeatCapacity(
        int totalSeats,
        int availableSeats
) {

    public SeatCapacity {
        if (totalSeats <= 0) {
            throw new IllegalArgumentException(
                    "totalSeats must be positive"
            );
        }
        if (availableSeats < 0) {
            throw new IllegalArgumentException(
                    "availableSeats must not be negative"
            );
        }
        if (availableSeats > totalSeats) {
            throw new IllegalArgumentException(
                    "availableSeats must not exceed totalSeats"
            );
        }
    }

    public static SeatCapacity full(int totalSeats) {
        return new SeatCapacity(totalSeats, totalSeats);
    }

    public boolean hasAvailableSeat() {
        return availableSeats > 0;
    }

    public SeatCapacity reserveOne() {
        if (!hasAvailableSeat()) {
            throw new NoAvailableSeatException();
        }
        return new SeatCapacity(
                totalSeats,
                availableSeats - 1
        );
    }

    public SeatCapacity releaseOne() {
        if (availableSeats == totalSeats) {
            throw new SeatCapacityExceededException();
        }
        return new SeatCapacity(
                totalSeats,
                availableSeats + 1
        );
    }
}

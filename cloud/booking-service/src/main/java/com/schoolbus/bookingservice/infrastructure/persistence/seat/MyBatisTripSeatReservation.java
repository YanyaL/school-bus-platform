package com.schoolbus.bookingservice.infrastructure.persistence.seat;

import com.schoolbus.bookingservice.application.booking.SeatLockRequest;
import com.schoolbus.bookingservice.application.booking.SeatReleaseRequest;
import com.schoolbus.bookingservice.application.booking.SeatSaleRequest;
import com.schoolbus.bookingservice.application.booking.TripSeatReservationPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Repository
@Profile("!test")
public class MyBatisTripSeatReservation
        implements TripSeatReservationPort {

    private static final ZoneOffset DATABASE_ZONE = ZoneOffset.UTC;

    private final TripSeatMapper tripSeatMapper;

    public MyBatisTripSeatReservation(
            TripSeatMapper tripSeatMapper
    ) {
        this.tripSeatMapper = Objects.requireNonNull(
                tripSeatMapper,
                "tripSeatMapper must not be null"
        );
    }

    @Override
    public boolean tryLockSeat(SeatLockRequest request) {
        SeatLockRequest validatedRequest = Objects.requireNonNull(
                request,
                "request must not be null"
        );
        int updatedRows = tripSeatMapper.tryLockSeat(
                validatedRequest.tripReference().value(),
                validatedRequest.seatNumber().value(),
                validatedRequest.bookingNumber().toString(),
                validatedRequest.userId().value(),
                toDatabaseTime(validatedRequest.lockExpiresAt()),
                toDatabaseTime(validatedRequest.lockedAt())
        );
        return isSingleRowUpdated(updatedRows, "lock");
    }

    @Override
    public boolean releaseSeat(SeatReleaseRequest request) {
        SeatReleaseRequest validatedRequest = Objects.requireNonNull(
                request,
                "request must not be null"
        );
        int updatedRows = tripSeatMapper.releaseSeat(
                validatedRequest.tripReference().value(),
                validatedRequest.seatNumber().value(),
                validatedRequest.bookingNumber().toString(),
                toDatabaseTime(validatedRequest.releasedAt())
        );
        return isSingleRowUpdated(updatedRows, "release");
    }

    @Override
    public boolean releaseSoldSeat(SeatReleaseRequest request) {
        SeatReleaseRequest validatedRequest = Objects.requireNonNull(
                request,
                "request must not be null"
        );
        int updatedRows = tripSeatMapper.releaseSoldSeat(
                validatedRequest.tripReference().value(),
                validatedRequest.seatNumber().value(),
                validatedRequest.bookingNumber().toString(),
                toDatabaseTime(validatedRequest.releasedAt())
        );
        return isSingleRowUpdated(updatedRows, "sold seat release");
    }

    @Override
    public boolean confirmSeatSold(SeatSaleRequest request) {
        SeatSaleRequest validatedRequest = Objects.requireNonNull(
                request,
                "request must not be null"
        );
        int updatedRows = tripSeatMapper.confirmSeatSold(
                validatedRequest.tripReference().value(),
                validatedRequest.seatNumber().value(),
                validatedRequest.bookingNumber().toString(),
                toDatabaseTime(validatedRequest.soldAt())
        );
        return isSingleRowUpdated(updatedRows, "sale confirmation");
    }

    private boolean isSingleRowUpdated(
            int updatedRows,
            String operation
    ) {
        if (updatedRows < 0 || updatedRows > 1) {
            throw new IllegalStateException(
                    "seat " + operation
                            + " update affected an unexpected number of rows: "
                            + updatedRows
            );
        }
        return updatedRows == 1;
    }

    private LocalDateTime toDatabaseTime(Instant instant) {
        return LocalDateTime.ofInstant(
                Objects.requireNonNull(
                        instant,
                        "instant must not be null"
                ),
                DATABASE_ZONE
        );
    }
}

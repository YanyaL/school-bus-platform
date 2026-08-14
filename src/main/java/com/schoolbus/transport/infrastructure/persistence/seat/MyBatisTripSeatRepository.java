package com.schoolbus.transport.infrastructure.persistence.seat;

import com.schoolbus.transport.domain.trip.TripId;
import com.schoolbus.transport.domain.trip.TripSeatRepository;
import com.schoolbus.transport.domain.trip.TripSeatStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

@Repository
@Profile("!test")
public class MyBatisTripSeatRepository implements TripSeatRepository {

    private static final ZoneOffset DATABASE_ZONE = ZoneOffset.UTC;

    private final TripSeatQueryMapper tripSeatQueryMapper;

    public MyBatisTripSeatRepository(
            TripSeatQueryMapper tripSeatQueryMapper
    ) {
        this.tripSeatQueryMapper = Objects.requireNonNull(
                tripSeatQueryMapper,
                "tripSeatQueryMapper must not be null"
        );
    }

    @Override
    public void initializeSeats(
            TripId tripId,
            List<String> seatNumbers,
            Instant initializedAt
    ) {
        TripId validatedTripId = Objects.requireNonNull(
                tripId,
                "tripId must not be null"
        );
        List<String> validatedSeatNumbers = List.copyOf(
                Objects.requireNonNull(
                        seatNumbers,
                        "seatNumbers must not be null"
                )
        );
        if (validatedSeatNumbers.isEmpty()) {
            throw new IllegalArgumentException(
                    "seatNumbers must not be empty"
            );
        }
        LocalDateTime operationTime = LocalDateTime.ofInstant(
                Objects.requireNonNull(
                        initializedAt,
                        "initializedAt must not be null"
                ),
                DATABASE_ZONE
        );
        int insertedRows = tripSeatQueryMapper.insertAvailableSeats(
                validatedTripId.value(),
                validatedSeatNumbers,
                operationTime
        );
        if (insertedRows != validatedSeatNumbers.size()) {
            throw new IllegalStateException(
                    "failed to initialize all trip seats"
            );
        }
    }

    @Override
    public List<TripSeatStatus> findSeatStatusesByTripId(
            TripId tripId
    ) {
        TripId validatedTripId = Objects.requireNonNull(
                tripId,
                "tripId must not be null"
        );
        return tripSeatQueryMapper
                .selectSeatStatusesByTripId(validatedTripId.value())
                .stream()
                .map(dataObject -> new TripSeatStatus(
                        dataObject.getSeatNumber(),
                        dataObject.getStatus()
                ))
                .toList();
    }
}

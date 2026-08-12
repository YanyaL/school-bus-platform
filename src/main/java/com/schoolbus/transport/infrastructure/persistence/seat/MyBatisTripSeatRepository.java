package com.schoolbus.transport.infrastructure.persistence.seat;

import com.schoolbus.transport.domain.trip.TripId;
import com.schoolbus.transport.domain.trip.TripSeatRepository;
import com.schoolbus.transport.domain.trip.TripSeatStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

@Repository
@Profile("!test")
public class MyBatisTripSeatRepository implements TripSeatRepository {

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

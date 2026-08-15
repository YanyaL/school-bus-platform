package com.schoolbus.transportquery.infrastructure.persistence;

import com.schoolbus.transportquery.application.BookableTripView;
import com.schoolbus.transportquery.application.TripQueryRepository;
import com.schoolbus.transportquery.application.TripRecord;
import com.schoolbus.transportquery.application.TripSeatStatusView;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MyBatisTripQueryRepository implements TripQueryRepository {

    private final TripReadMapper tripReadMapper;
    private final TripSeatReadMapper tripSeatReadMapper;

    public MyBatisTripQueryRepository(
            TripReadMapper tripReadMapper,
            TripSeatReadMapper tripSeatReadMapper
    ) {
        this.tripReadMapper = Objects.requireNonNull(
                tripReadMapper,
                "tripReadMapper must not be null"
        );
        this.tripSeatReadMapper = Objects.requireNonNull(
                tripSeatReadMapper,
                "tripSeatReadMapper must not be null"
        );
    }

    @Override
    public List<BookableTripView> findBookableTrips(Instant now, int limit) {
        LocalDateTime utcNow = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        return tripReadMapper.selectBookableTrips(utcNow, limit).stream()
                .map(this::toBookableTripView)
                .toList();
    }

    @Override
    public Optional<TripRecord> findByTripNumber(String tripNumber) {
        TripReadDataObject dataObject = tripReadMapper.selectByTripNumber(tripNumber);
        if (dataObject == null) {
            return Optional.empty();
        }
        return Optional.of(new TripRecord(
                dataObject.getId(),
                dataObject.getTripNumber(),
                toInstant(dataObject.getBookingDeadline())
        ));
    }

    @Override
    public List<TripSeatStatusView> findSeatStatusesByTripId(long tripId) {
        return tripSeatReadMapper.selectSeatStatusesByTripId(tripId).stream()
                .map(row -> new TripSeatStatusView(
                        row.getSeatNumber(),
                        row.getStatus()
                ))
                .toList();
    }

    private BookableTripView toBookableTripView(TripReadDataObject dataObject) {
        return new BookableTripView(
                dataObject.getId(),
                dataObject.getTripNumber(),
                dataObject.getVehicleId(),
                dataObject.getRouteId(),
                toInstant(dataObject.getDepartureTime()),
                toInstant(dataObject.getBookingDeadline()),
                dataObject.getPrice()
        );
    }

    private static Instant toInstant(LocalDateTime value) {
        return value.atZone(ZoneOffset.UTC).toInstant();
    }
}

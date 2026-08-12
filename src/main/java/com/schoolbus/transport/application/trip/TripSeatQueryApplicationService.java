package com.schoolbus.transport.application.trip;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;
import com.schoolbus.transport.domain.trip.BusTrip;
import com.schoolbus.transport.domain.trip.BusTripRepository;
import com.schoolbus.transport.domain.trip.TripId;
import com.schoolbus.transport.domain.trip.TripSeatRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Profile("!test")
public class TripSeatQueryApplicationService {

    private final BusTripRepository busTripRepository;
    private final TripSeatRepository tripSeatRepository;

    public TripSeatQueryApplicationService(
            BusTripRepository busTripRepository,
            TripSeatRepository tripSeatRepository
    ) {
        this.busTripRepository = Objects.requireNonNull(
                busTripRepository,
                "busTripRepository must not be null"
        );
        this.tripSeatRepository = Objects.requireNonNull(
                tripSeatRepository,
                "tripSeatRepository must not be null"
        );
    }

    @Transactional(readOnly = true)
    public TripSeatMapView findTripSeatMap(long tripId) {
        TripId validatedTripId = TripId.of(tripId);
        BusTrip trip = busTripRepository.findById(validatedTripId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "trip not found: " + tripId
                ));
        List<TripSeatStatusView> seats = tripSeatRepository
                .findSeatStatusesByTripId(validatedTripId)
                .stream()
                .map(seat -> new TripSeatStatusView(
                        seat.seatNumber(),
                        seat.status()
                ))
                .toList();
        return new TripSeatMapView(
                trip.tripId().value(),
                trip.bookingDeadline(),
                seats
        );
    }
}

package com.schoolbus.transport.application.trip;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;
import com.schoolbus.transport.domain.trip.BusTrip;
import com.schoolbus.transport.domain.trip.BusTripRepository;
import com.schoolbus.transport.domain.trip.TripId;
import com.schoolbus.transport.domain.trip.TripNumber;
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
    public TripSeatMapView findTripSeatMap(String tripNumber) {
        TripNumber validatedTripNumber = TripNumber.of(tripNumber);
        BusTrip trip = busTripRepository
                .findByTripNumber(validatedTripNumber)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "trip not found: " + validatedTripNumber
                ));
        TripId tripId = trip.tripId();
        List<TripSeatStatusView> seats = tripSeatRepository
                .findSeatStatusesByTripId(tripId)
                .stream()
                .map(seat -> new TripSeatStatusView(
                        seat.seatNumber(),
                        seat.status()
                ))
                .toList();
        return new TripSeatMapView(
                trip.tripNumber().toString(),
                trip.bookingDeadline(),
                seats
        );
    }
}

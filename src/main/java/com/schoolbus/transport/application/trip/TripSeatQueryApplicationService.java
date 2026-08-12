package com.schoolbus.transport.application.trip;

import com.schoolbus.transport.domain.trip.BusTrip;
import com.schoolbus.transport.domain.trip.BusTripRepository;
import com.schoolbus.transport.domain.trip.TripId;
import com.schoolbus.transport.infrastructure.persistence.seat.TripSeatQueryMapper;
import com.schoolbus.transport.infrastructure.persistence.seat.TripSeatStatusDataObject;
import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Profile("!test")
public class TripSeatQueryApplicationService {

    private final BusTripRepository busTripRepository;
    private final TripSeatQueryMapper tripSeatQueryMapper;

    public TripSeatQueryApplicationService(
            BusTripRepository busTripRepository,
            TripSeatQueryMapper tripSeatQueryMapper
    ) {
        this.busTripRepository = Objects.requireNonNull(
                busTripRepository,
                "busTripRepository must not be null"
        );
        this.tripSeatQueryMapper = Objects.requireNonNull(
                tripSeatQueryMapper,
                "tripSeatQueryMapper must not be null"
        );
    }

    @Transactional(readOnly = true)
    public TripSeatMapView findTripSeatMap(long tripId) {
        if (tripId <= 0L) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        BusTrip trip = busTripRepository
                .findById(TripId.of(tripId))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        List<TripSeatView> seats = tripSeatQueryMapper
                .selectSeatStatusesByTripId(tripId)
                .stream()
                .map(this::toSeatView)
                .toList();

        return new TripSeatMapView(
                tripId,
                trip.bookingDeadline(),
                seats
        );
    }

    private TripSeatView toSeatView(TripSeatStatusDataObject seat) {
        return new TripSeatView(
                seat.getSeatNumber(),
                seat.getStatus()
        );
    }
}

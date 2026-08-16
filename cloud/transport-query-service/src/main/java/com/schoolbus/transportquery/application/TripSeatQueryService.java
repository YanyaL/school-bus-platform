package com.schoolbus.transportquery.application;

import com.schoolbus.transportquery.api.BusinessException;
import com.schoolbus.transportquery.api.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class TripSeatQueryService {

    private final TripQueryRepository tripQueryRepository;

    public TripSeatQueryService(TripQueryRepository tripQueryRepository) {
        this.tripQueryRepository = Objects.requireNonNull(
                tripQueryRepository,
                "tripQueryRepository must not be null"
        );
    }

    @Transactional(readOnly = true)
    public TripSeatMapView findTripSeatMap(String tripNumber) {
        String validatedTripNumber = requireUuid(tripNumber);
        TripRecord trip = tripQueryRepository
                .findByTripNumber(validatedTripNumber)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "trip not found: " + validatedTripNumber
                ));
        List<TripSeatStatusView> seats = tripQueryRepository
                .findSeatStatusesByTripId(trip.tripId());
        return new TripSeatMapView(
                trip.tripNumber(),
                trip.bookingDeadline(),
                seats
        );
    }

    private static String requireUuid(String tripNumber) {
        try {
            return UUID.fromString(Objects.requireNonNull(tripNumber).strip())
                    .toString();
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "tripNumber must be a valid UUID"
            );
        }
    }
}

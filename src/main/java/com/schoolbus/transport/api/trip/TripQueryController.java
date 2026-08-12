package com.schoolbus.transport.api.trip;

import com.schoolbus.shared.api.ApiResponse;
import com.schoolbus.transport.application.trip.TripQueryApplicationService;
import com.schoolbus.transport.application.trip.TripSeatQueryApplicationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/trips")
@Validated
@Profile("!test")
public class TripQueryController {

    private final TripQueryApplicationService applicationService;
    private final TripSeatQueryApplicationService seatQueryApplicationService;

    public TripQueryController(
            TripQueryApplicationService applicationService,
            TripSeatQueryApplicationService seatQueryApplicationService
    ) {
        this.applicationService = Objects.requireNonNull(
                applicationService,
                "applicationService must not be null"
        );
        this.seatQueryApplicationService = Objects.requireNonNull(
                seatQueryApplicationService,
                "seatQueryApplicationService must not be null"
        );
    }

    @GetMapping
    public ApiResponse<List<BookableTripResponse>> findBookableTrips(
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "limit must be at least 1")
            @Max(value = 100, message = "limit must not exceed 100")
            int limit
    ) {
        List<BookableTripResponse> response = applicationService
                .findBookableTrips(limit)
                .stream()
                .map(BookableTripResponse::from)
                .toList();
        return ApiResponse.success(response);
    }

    @GetMapping("/{tripId}/seats")
    public ApiResponse<TripSeatMapResponse> findTripSeats(
            @PathVariable long tripId
    ) {
        return ApiResponse.success(
                TripSeatMapResponse.from(
                        seatQueryApplicationService.findTripSeatMap(tripId)
                )
        );
    }
}

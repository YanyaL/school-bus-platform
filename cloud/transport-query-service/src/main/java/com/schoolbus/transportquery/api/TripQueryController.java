package com.schoolbus.transportquery.api;

import com.schoolbus.transportquery.application.BookableTripQueryService;
import com.schoolbus.transportquery.application.TripSeatQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class TripQueryController {

    private final BookableTripQueryService bookableTripQueryService;
    private final TripSeatQueryService tripSeatQueryService;

    public TripQueryController(
            BookableTripQueryService bookableTripQueryService,
            TripSeatQueryService tripSeatQueryService
    ) {
        this.bookableTripQueryService = Objects.requireNonNull(
                bookableTripQueryService,
                "bookableTripQueryService must not be null"
        );
        this.tripSeatQueryService = Objects.requireNonNull(
                tripSeatQueryService,
                "tripSeatQueryService must not be null"
        );
    }

    @GetMapping
    public ApiResponse<List<BookableTripResponse>> findBookableTrips(
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "limit must be at least 1")
            @Max(value = 100, message = "limit must not exceed 100")
            int limit
    ) {
        List<BookableTripResponse> response = bookableTripQueryService
                .findBookableTrips(limit)
                .stream()
                .map(BookableTripResponse::from)
                .toList();
        return ApiResponse.success(response);
    }

    @GetMapping("/{tripNumber}/seats")
    public ApiResponse<TripSeatMapResponse> findTripSeats(
            @PathVariable String tripNumber
    ) {
        return ApiResponse.success(
                TripSeatMapResponse.from(
                        tripSeatQueryService.findTripSeatMap(tripNumber)
                )
        );
    }
}

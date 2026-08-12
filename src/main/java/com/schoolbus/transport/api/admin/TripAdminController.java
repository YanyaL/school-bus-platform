package com.schoolbus.transport.api.admin;

import com.schoolbus.shared.api.ApiResponse;
import com.schoolbus.transport.application.trip.admin.CreateDraftTripCommand;
import com.schoolbus.transport.application.trip.admin.PublishTripCommand;
import com.schoolbus.transport.application.trip.admin.TripAdminApplicationService;
import com.schoolbus.transport.application.trip.admin.TripAdminView;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/admin/trips")
@Profile("!test")
public class TripAdminController {

    private final TripAdminApplicationService applicationService;

    public TripAdminController(
            TripAdminApplicationService applicationService
    ) {
        this.applicationService = Objects.requireNonNull(
                applicationService,
                "applicationService must not be null"
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TripAdminResponse>> createDraftTrip(
            @Valid @RequestBody CreateDraftTripRequest request
    ) {
        TripAdminView view = applicationService.createDraftTrip(
                new CreateDraftTripCommand(
                        request.vehicleNo(),
                        request.routeNo(),
                        request.departureTime(),
                        request.bookingDeadline(),
                        request.price()
                )
        );
        URI location = URI.create(
                "/api/v1/admin/trips/" + view.tripNumber()
        );
        return ResponseEntity
                .created(location)
                .body(ApiResponse.success(TripAdminResponse.from(view)));
    }

    @GetMapping("/{tripNo}")
    public ApiResponse<TripAdminResponse> findTrip(
            @PathVariable("tripNo") String tripNo
    ) {
        return ApiResponse.success(
                TripAdminResponse.from(
                        applicationService.findByTripNumber(tripNo)
                )
        );
    }

    @PostMapping("/{tripNo}/publish")
    public ApiResponse<TripAdminResponse> publishTrip(
            @PathVariable("tripNo") String tripNo,
            @Valid @RequestBody PublishTripRequest request
    ) {
        TripAdminView view = applicationService.publishTrip(
                new PublishTripCommand(
                        tripNo,
                        request.version()
                )
        );
        return ApiResponse.success(TripAdminResponse.from(view));
    }
}

package com.schoolbus.transport.api.admin;

import com.schoolbus.shared.api.ApiResponse;
import com.schoolbus.shared.api.HttpResourceId;
import com.schoolbus.transport.application.trip.AdminTripView;
import com.schoolbus.transport.application.trip.CancelTripCommand;
import com.schoolbus.transport.application.trip.CreateTripDraftCommand;
import com.schoolbus.transport.application.trip.PublishTripCommand;
import com.schoolbus.transport.application.trip.TripManagementApplicationService;
import com.schoolbus.transport.application.trip.TripCancellationApplicationService;
import com.schoolbus.transport.application.trip.TripPublicationApplicationService;
import com.schoolbus.transport.domain.trip.TripStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/admin/trips")
@Validated
@Profile("!test")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTripController {

    private final TripManagementApplicationService applicationService;
    private final TripPublicationApplicationService publicationService;
    private final TripCancellationApplicationService cancellationService;

    public AdminTripController(
            TripManagementApplicationService applicationService,
            TripPublicationApplicationService publicationService,
            TripCancellationApplicationService cancellationService
    ) {
        this.applicationService = Objects.requireNonNull(
                applicationService,
                "applicationService must not be null"
        );
        this.publicationService = Objects.requireNonNull(
                publicationService,
                "publicationService must not be null"
        );
        this.cancellationService = Objects.requireNonNull(
                cancellationService,
                "cancellationService must not be null"
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminTripResponse>> createDraft(
            @Valid @RequestBody CreateTripDraftRequest request
    ) {
        AdminTripView view = applicationService.createDraft(
                new CreateTripDraftCommand(
                        HttpResourceId.parse(request.vehicleId(), "vehicleId"),
                        HttpResourceId.parse(request.routeId(), "routeId"),
                        request.departureTime(),
                        request.bookingDeadline(),
                        request.price()
                )
        );
        URI location = URI.create(
                "/api/v1/admin/trips/" + HttpResourceId.format(view.tripId())
        );
        return ResponseEntity
                .created(location)
                .body(ApiResponse.success(AdminTripResponse.from(view)));
    }

    @GetMapping
    public ApiResponse<List<AdminTripResponse>> listTrips(
            @RequestParam(required = false) TripStatus status,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page must not be negative")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100")
            int size
    ) {
        List<AdminTripResponse> trips = applicationService
                .listTrips(status, page, size)
                .stream()
                .map(AdminTripResponse::from)
                .toList();
        return ApiResponse.success(trips);
    }

    @GetMapping("/{tripId}")
    public ApiResponse<AdminTripResponse> findTrip(
            @PathVariable String tripId
    ) {
        long parsedTripId = HttpResourceId.parse(tripId, "tripId");
        return ApiResponse.success(
                AdminTripResponse.from(
                        applicationService.findById(parsedTripId)
                )
        );
    }

    @PostMapping("/{tripId}/publication")
    public ApiResponse<AdminTripResponse> publishTrip(
            @PathVariable String tripId,
            @Valid @RequestBody PublishTripRequest request
    ) {
        long parsedTripId = HttpResourceId.parse(tripId, "tripId");
        AdminTripView view = publicationService.publish(
                new PublishTripCommand(
                        parsedTripId,
                        request.version()
                )
        );
        return ApiResponse.success(AdminTripResponse.from(view));
    }

    @PostMapping("/{tripId}/cancellation")
    public ApiResponse<AdminTripResponse> cancelTrip(
            @PathVariable String tripId,
            @Valid @RequestBody CancelTripRequest request
    ) {
        long parsedTripId = HttpResourceId.parse(tripId, "tripId");
        AdminTripView view = cancellationService.cancel(
                new CancelTripCommand(parsedTripId, request.version())
        );
        return ApiResponse.success(AdminTripResponse.from(view));
    }
}

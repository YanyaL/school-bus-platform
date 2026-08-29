package com.schoolbus.transport.api.admin;

import com.schoolbus.shared.api.ApiResponse;
import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;
import com.schoolbus.shared.api.HttpResourceId;
import com.schoolbus.transport.application.route.CreateRouteCommand;
import com.schoolbus.transport.application.route.RouteManagementApplicationService;
import com.schoolbus.transport.application.route.RouteView;
import com.schoolbus.transport.application.route.UpdateRouteStatusCommand;
import com.schoolbus.transport.domain.route.RouteStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/v1/admin/routes")
@Validated
@Profile("!test")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRouteController {

    private final RouteManagementApplicationService applicationService;

    public AdminRouteController(
            RouteManagementApplicationService applicationService
    ) {
        this.applicationService = Objects.requireNonNull(
                applicationService,
                "applicationService must not be null"
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RouteResponse>> createRoute(
            @Valid @RequestBody CreateRouteRequest request
    ) {
        if (request.departureCampus()
                .equalsIgnoreCase(request.arrivalCampus())) {
            throw new BusinessException(
                    ErrorCode.INVALID_ROUTE_DIRECTION,
                    "departureCampus and arrivalCampus must differ"
            );
        }

        RouteView view = applicationService.createRoute(
                new CreateRouteCommand(
                        request.routeCode(),
                        request.departureCampus(),
                        request.arrivalCampus(),
                        request.estimatedDurationMinutes()
                )
        );
        URI location = URI.create(
                "/api/v1/admin/routes/" + HttpResourceId.format(view.routeId())
        );
        return ResponseEntity
                .created(location)
                .body(ApiResponse.success(RouteResponse.from(view)));
    }

    @GetMapping
    public ApiResponse<List<RouteResponse>> listRoutes(
            @RequestParam(required = false) RouteStatus status,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page must not be negative")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100")
            int size
    ) {
        List<RouteResponse> routes = applicationService
                .listRoutes(status, page, size)
                .stream()
                .map(RouteResponse::from)
                .toList();
        return ApiResponse.success(routes);
    }

    @GetMapping("/{routeId}")
    public ApiResponse<RouteResponse> findRoute(
            @PathVariable String routeId
    ) {
        long parsedRouteId = HttpResourceId.parse(routeId, "routeId");
        return ApiResponse.success(
                RouteResponse.from(
                        applicationService.findById(parsedRouteId)
                )
        );
    }

    @PatchMapping("/{routeId}/status")
    public ApiResponse<RouteResponse> updateRouteStatus(
            @PathVariable String routeId,
            @Valid @RequestBody UpdateRouteStatusRequest request
    ) {
        long parsedRouteId = HttpResourceId.parse(routeId, "routeId");
        RouteView view = applicationService.updateStatus(
                new UpdateRouteStatusCommand(
                        parsedRouteId,
                        request.status(),
                        request.version()
                )
        );
        return ApiResponse.success(RouteResponse.from(view));
    }
}

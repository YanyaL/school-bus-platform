package com.schoolbus.transport.api.admin;

import com.schoolbus.shared.api.ApiResponse;
import com.schoolbus.shared.api.HttpResourceId;
import com.schoolbus.transport.application.vehicle.CreateVehicleCommand;
import com.schoolbus.transport.application.vehicle.UpdateVehicleStatusCommand;
import com.schoolbus.transport.application.vehicle.VehicleManagementApplicationService;
import com.schoolbus.transport.application.vehicle.VehicleView;
import com.schoolbus.transport.domain.vehicle.VehicleStatus;
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
@RequestMapping("/api/v1/admin/vehicles")
@Validated
@Profile("!test")
@PreAuthorize("hasRole('ADMIN')")
public class AdminVehicleController {

    private final VehicleManagementApplicationService applicationService;

    public AdminVehicleController(
            VehicleManagementApplicationService applicationService
    ) {
        this.applicationService = Objects.requireNonNull(
                applicationService,
                "applicationService must not be null"
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<VehicleResponse>> createVehicle(
            @Valid @RequestBody CreateVehicleRequest request
    ) {
        VehicleView view = applicationService.createVehicle(
                new CreateVehicleCommand(
                        request.licensePlate(),
                        request.seatCount()
                )
        );
        URI location = URI.create(
                "/api/v1/admin/vehicles/" + HttpResourceId.format(view.vehicleId())
        );
        return ResponseEntity
                .created(location)
                .body(ApiResponse.success(VehicleResponse.from(view)));
    }

    @GetMapping
    public ApiResponse<List<VehicleResponse>> listVehicles(
            @RequestParam(required = false) VehicleStatus status,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page must not be negative")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100")
            int size
    ) {
        List<VehicleResponse> vehicles = applicationService
                .listVehicles(status, page, size)
                .stream()
                .map(VehicleResponse::from)
                .toList();
        return ApiResponse.success(vehicles);
    }

    @GetMapping("/{vehicleId}")
    public ApiResponse<VehicleResponse> findVehicle(
            @PathVariable String vehicleId
    ) {
        long parsedVehicleId = HttpResourceId.parse(vehicleId, "vehicleId");
        return ApiResponse.success(
                VehicleResponse.from(
                        applicationService.findById(parsedVehicleId)
                )
        );
    }

    @PatchMapping("/{vehicleId}/status")
    public ApiResponse<VehicleResponse> updateVehicleStatus(
            @PathVariable String vehicleId,
            @Valid @RequestBody UpdateVehicleStatusRequest request
    ) {
        long parsedVehicleId = HttpResourceId.parse(vehicleId, "vehicleId");
        VehicleView view = applicationService.updateStatus(
                new UpdateVehicleStatusCommand(
                        parsedVehicleId,
                        request.status(),
                        request.version()
                )
        );
        return ApiResponse.success(VehicleResponse.from(view));
    }
}

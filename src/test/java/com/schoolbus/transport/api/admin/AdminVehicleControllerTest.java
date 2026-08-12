package com.schoolbus.transport.api.admin;

import com.schoolbus.shared.api.GlobalExceptionHandler;
import com.schoolbus.shared.config.SecurityConfig;
import com.schoolbus.transport.application.vehicle.VehicleManagementApplicationService;
import com.schoolbus.transport.application.vehicle.VehicleNotFoundException;
import com.schoolbus.transport.application.vehicle.VehicleView;
import com.schoolbus.transport.domain.vehicle.VehicleStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("web-test")
@WebMvcTest(AdminVehicleController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class
})
class AdminVehicleControllerTest {

    private static final String VEHICLE_NUMBER =
            "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VehicleManagementApplicationService applicationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldCreateVehicleForAdmin() throws Exception {
        when(applicationService.createVehicle(any()))
                .thenReturn(vehicleView(VehicleStatus.ENABLED, 0L));

        mockMvc.perform(
                        post("/api/v1/admin/vehicles")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "licensePlate": "粤A12345",
                                          "seatCount": 50
                                        }
                                        """)
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_ADMIN"
                                        )
                                ))
                )
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "/api/v1/admin/vehicles/3001"
                ))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.vehicleId").value(3001L))
                .andExpect(jsonPath("$.data.vehicleNumber")
                        .value(VEHICLE_NUMBER))
                .andExpect(jsonPath("$.data.licensePlate")
                        .value("粤A12345"))
                .andExpect(jsonPath("$.data.seatCount").value(50))
                .andExpect(jsonPath("$.data.status").value("ENABLED"));

        verify(applicationService).createVehicle(any());
    }

    @Test
    void shouldRejectStudentAccessWithForbidden() throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/vehicles")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "licensePlate": "粤A12345",
                                          "seatCount": 50
                                        }
                                        """)
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_STUDENT"
                                        )
                                ))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void shouldRejectUnauthenticatedAccess() throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/vehicles")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "licensePlate": "粤A12345",
                                          "seatCount": 50
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(applicationService);
    }

    @Test
    void shouldRejectInvalidCreateRequest() throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/vehicles")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "licensePlate": "",
                                          "seatCount": 0
                                        }
                                        """)
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_ADMIN"
                                        )
                                ))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void shouldFindVehicleForAdmin() throws Exception {
        when(applicationService.findById(3001L))
                .thenReturn(vehicleView(VehicleStatus.ENABLED, 0L));

        mockMvc.perform(
                        get("/api/v1/admin/vehicles/3001")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_ADMIN"
                                        )
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vehicleId").value(3001L))
                .andExpect(jsonPath("$.data.vehicleNumber")
                        .value(VEHICLE_NUMBER));
    }

    @Test
    void shouldListVehiclesForAdmin() throws Exception {
        when(applicationService.listVehicles(
                VehicleStatus.ENABLED,
                0,
                20
        )).thenReturn(List.of(
                vehicleView(VehicleStatus.ENABLED, 0L)
        ));

        mockMvc.perform(
                        get("/api/v1/admin/vehicles")
                                .param("status", "ENABLED")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_ADMIN"
                                        )
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].vehicleId").value(3001L));
    }

    @Test
    void shouldUpdateVehicleStatusForAdmin() throws Exception {
        when(applicationService.updateStatus(any()))
                .thenReturn(vehicleView(VehicleStatus.DISABLED, 1L));

        mockMvc.perform(
                        patch("/api/v1/admin/vehicles/3001/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "status": "DISABLED",
                                          "version": 0
                                        }
                                        """)
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_ADMIN"
                                        )
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.version").value(1));
    }

    @Test
    void shouldReturnNotFoundWhenVehicleMissing() throws Exception {
        when(applicationService.findById(9999L))
                .thenThrow(new VehicleNotFoundException(9999L));

        mockMvc.perform(
                        get("/api/v1/admin/vehicles/9999")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_ADMIN"
                                        )
                                ))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("VEHICLE_NOT_FOUND"));
    }

    private VehicleView vehicleView(
            VehicleStatus status,
            long version
    ) {
        Instant timestamp = Instant.parse("2026-08-12T00:00:00Z");
        return new VehicleView(
                3001L,
                VEHICLE_NUMBER,
                "粤A12345",
                50,
                status,
                version,
                timestamp,
                timestamp
        );
    }
}

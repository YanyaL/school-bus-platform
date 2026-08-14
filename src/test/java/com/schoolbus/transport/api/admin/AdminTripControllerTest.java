package com.schoolbus.transport.api.admin;

import com.schoolbus.shared.api.GlobalExceptionHandler;
import com.schoolbus.shared.config.SecurityConfig;
import com.schoolbus.transport.application.trip.AdminTripView;
import com.schoolbus.transport.application.trip.TripManagementApplicationService;
import com.schoolbus.transport.application.trip.TripNotFoundException;
import com.schoolbus.transport.application.trip.VehicleScheduleConflictException;
import com.schoolbus.transport.domain.trip.TripStatus;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("web-test")
@WebMvcTest(AdminTripController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class
})
class AdminTripControllerTest {

    private static final Instant DEPARTURE_TIME =
            Instant.parse("2027-08-15T08:00:00Z");
    private static final Instant BOOKING_DEADLINE =
            Instant.parse("2027-08-15T07:30:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TripManagementApplicationService applicationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldCreateDraftForAdmin() throws Exception {
        when(applicationService.createDraft(any()))
                .thenReturn(tripView());

        mockMvc.perform(
                        post("/api/v1/admin/trips")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequest())
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_ADMIN"
                                        )
                                ))
                )
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "/api/v1/admin/trips/5001"
                ))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.tripId").value(5001L))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.price").value(5.00));

        verify(applicationService).createDraft(any());
    }

    @Test
    void shouldRejectStudentAccess() throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/trips")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequest())
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
                        post("/api/v1/admin/trips")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequest())
                )
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(applicationService);
    }

    @Test
    void shouldRejectInvalidRequest() throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/trips")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "vehicleId": 0,
                                          "routeId": -1,
                                          "departureTime": "2020-01-01T00:00:00Z",
                                          "bookingDeadline": "2020-01-01T00:00:00Z",
                                          "price": -1.00
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
    void shouldListDraftTripsForAdmin() throws Exception {
        when(applicationService.listTrips(TripStatus.DRAFT, 0, 20))
                .thenReturn(List.of(tripView()));

        mockMvc.perform(
                        get("/api/v1/admin/trips")
                                .param("status", "DRAFT")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_ADMIN"
                                        )
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].tripId").value(5001L))
                .andExpect(jsonPath("$.data[0].status").value("DRAFT"));
    }

    @Test
    void shouldFindTripForAdmin() throws Exception {
        when(applicationService.findById(5001L))
                .thenReturn(tripView());

        mockMvc.perform(
                        get("/api/v1/admin/trips/5001")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_ADMIN"
                                        )
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tripId").value(5001L));
    }

    @Test
    void shouldReturnNotFoundForMissingTrip() throws Exception {
        when(applicationService.findById(9999L))
                .thenThrow(new TripNotFoundException(9999L));

        mockMvc.perform(
                        get("/api/v1/admin/trips/9999")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_ADMIN"
                                        )
                                ))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));
    }

    @Test
    void shouldReturnConflictForOverlappingSchedule() throws Exception {
        when(applicationService.createDraft(any()))
                .thenThrow(new VehicleScheduleConflictException(3001L));

        mockMvc.perform(
                        post("/api/v1/admin/trips")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validRequest())
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_ADMIN"
                                        )
                                ))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("VEHICLE_SCHEDULE_CONFLICT"));
    }

    private String validRequest() {
        return """
                {
                  "vehicleId": 3001,
                  "routeId": 2001,
                  "departureTime": "2027-08-15T08:00:00Z",
                  "bookingDeadline": "2027-08-15T07:30:00Z",
                  "price": 5.00
                }
                """;
    }

    private AdminTripView tripView() {
        Instant createdAt = Instant.parse("2026-08-14T00:00:00Z");
        return new AdminTripView(
                5001L,
                "33333333-3333-3333-3333-333333333333",
                3001L,
                2001L,
                DEPARTURE_TIME,
                BOOKING_DEADLINE,
                new BigDecimal("5.00"),
                TripStatus.DRAFT,
                0L,
                createdAt,
                createdAt
        );
    }
}

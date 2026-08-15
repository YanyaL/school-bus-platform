package com.schoolbus.transport.api.admin;

import com.schoolbus.shared.api.GlobalExceptionHandler;
import com.schoolbus.shared.config.SecurityConfig;
import com.schoolbus.transport.application.trip.AdminTripView;
import com.schoolbus.transport.application.trip.TripCancellationApplicationService;
import com.schoolbus.transport.application.trip.TripHasActiveBookingsException;
import com.schoolbus.transport.application.trip.TripManagementApplicationService;
import com.schoolbus.transport.application.trip.TripNotFoundException;
import com.schoolbus.transport.application.trip.TripNotPublishableException;
import com.schoolbus.transport.application.trip.TripPublicationApplicationService;
import com.schoolbus.transport.application.trip.VehicleScheduleConflictException;
import com.schoolbus.transport.domain.trip.TripStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    private TripPublicationApplicationService publicationService;

    @MockitoBean
    private TripCancellationApplicationService cancellationService;

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
                .andExpect(jsonPath("$.data.tripId").value("5001"))
                .andExpect(jsonPath("$.data.vehicleId").value("3001"))
                .andExpect(jsonPath("$.data.routeId").value("2001"))
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
                                          "vehicleId": "0",
                                          "routeId": "-1",
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
                .andExpect(jsonPath("$.data[0].tripId").value("5001"))
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
                .andExpect(jsonPath("$.data.tripId").value("5001"))
                .andExpect(jsonPath("$.data.vehicleId").value("3001"))
                .andExpect(jsonPath("$.data.routeId").value("2001"));
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

    @Test
    void shouldPublishDraftForAdmin() throws Exception {
        AdminTripView published = new AdminTripView(
                5001L,
                "33333333-3333-3333-3333-333333333333",
                3001L,
                2001L,
                DEPARTURE_TIME,
                BOOKING_DEADLINE,
                new BigDecimal("5.00"),
                TripStatus.OPEN_FOR_BOOKING,
                1L,
                Instant.parse("2026-08-14T00:00:00Z"),
                Instant.parse("2026-08-14T00:05:00Z")
        );
        when(publicationService.publish(any())).thenReturn(published);

        mockMvc.perform(
                        post("/api/v1/admin/trips/5001/publication")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":0}")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_ADMIN"
                                        )
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status")
                        .value("OPEN_FOR_BOOKING"))
                .andExpect(jsonPath("$.data.version").value(1));

        verify(publicationService).publish(any());
    }

    @Test
    void shouldRejectStudentPublication() throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/trips/5001/publication")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":0}")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_STUDENT"
                                        )
                                ))
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(publicationService);
    }

    @Test
    void shouldRejectInvalidPublicationVersion() throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/trips/5001/publication")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":-1}")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_ADMIN"
                                        )
                                ))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"));

        verifyNoInteractions(publicationService);
    }

    @Test
    void shouldReturnConflictWhenTripCannotBePublished() throws Exception {
        when(publicationService.publish(any()))
                .thenThrow(new TripNotPublishableException(
                        5001L,
                        "only DRAFT trips can be published"
                ));

        mockMvc.perform(
                        post("/api/v1/admin/trips/5001/publication")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":1}")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_ADMIN"
                                        )
                                ))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("TRIP_NOT_PUBLISHABLE"));
    }

    @Test
    void shouldCancelTripForAdmin() throws Exception {
        AdminTripView cancelled = new AdminTripView(
                5001L,
                "33333333-3333-3333-3333-333333333333",
                3001L,
                2001L,
                DEPARTURE_TIME,
                BOOKING_DEADLINE,
                new BigDecimal("5.00"),
                TripStatus.CANCELLED,
                1L,
                Instant.parse("2026-08-14T00:00:00Z"),
                Instant.parse("2026-08-14T00:05:00Z")
        );
        when(cancellationService.cancel(any())).thenReturn(cancelled);

        mockMvc.perform(
                        post("/api/v1/admin/trips/5001/cancellation")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":0}")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_ADMIN"
                                        )
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status")
                        .value("CANCELLED"))
                .andExpect(jsonPath("$.data.version").value(1));

        verify(cancellationService).cancel(any());
    }

    @Test
    void shouldRejectStudentCancellation() throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/trips/5001/cancellation")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":0}")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_STUDENT"
                                        )
                                ))
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(cancellationService);
    }

    @Test
    void shouldRejectInvalidCancellationVersion() throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/trips/5001/cancellation")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":-1}")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_ADMIN"
                                        )
                                ))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"));

        verifyNoInteractions(cancellationService);
    }

    @Test
    void shouldReturnConflictWhenTripHasActiveBookings()
            throws Exception {
        when(cancellationService.cancel(any()))
                .thenThrow(new TripHasActiveBookingsException(5001L));

        mockMvc.perform(
                        post("/api/v1/admin/trips/5001/cancellation")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":1}")
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority(
                                                "ROLE_ADMIN"
                                        )
                                ))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("TRIP_HAS_ACTIVE_BOOKINGS"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abc",
            "0",
            "9223372036854775808"
    })
    void shouldRejectInvalidVehicleId(String vehicleId)
            throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/trips")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "vehicleId": "%s",
                                          "routeId": "2001",
                                          "departureTime": "2027-08-15T08:00:00Z",
                                          "bookingDeadline": "2027-08-15T07:30:00Z",
                                          "price": 5.00
                                        }
                                        """.formatted(vehicleId))
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

    @ParameterizedTest
    @ValueSource(strings = {
            "abc",
            "0",
            "9223372036854775808"
    })
    void shouldRejectInvalidRouteId(String routeId) throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/trips")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "vehicleId": "3001",
                                          "routeId": "%s",
                                          "departureTime": "2027-08-15T08:00:00Z",
                                          "bookingDeadline": "2027-08-15T07:30:00Z",
                                          "price": 5.00
                                        }
                                        """.formatted(routeId))
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

    @ParameterizedTest
    @ValueSource(strings = {
            "abc",
            "0",
            "9223372036854775808"
    })
    void shouldRejectInvalidTripId(String tripId) throws Exception {
        mockMvc.perform(
                        get("/api/v1/admin/trips/" + tripId)
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

    private String validRequest() {
        return """
                {
                  "vehicleId": "3001",
                  "routeId": "2001",
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

package com.schoolbus.transport.api.admin;

import com.schoolbus.shared.api.GlobalExceptionHandler;
import com.schoolbus.shared.config.SecurityConfig;
import com.schoolbus.transport.application.trip.admin.TripAdminApplicationService;
import com.schoolbus.transport.application.trip.admin.TripAdminView;
import com.schoolbus.transport.domain.trip.TripStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

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
@WebMvcTest(TripAdminController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class
})
class TripAdminControllerTest {

    private static final String TRIP_NUMBER =
            "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TripAdminApplicationService applicationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldCreateDraftTripForAdmin() throws Exception {
        when(applicationService.createDraftTrip(any()))
                .thenReturn(draftTripView());

        mockMvc.perform(
                        post("/api/v1/admin/trips")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "vehicleNo": "33333333-3333-3333-3333-333333333333",
                                          "routeNo": "22222222-2222-2222-2222-222222222222",
                                          "departureTime": "2026-08-05T09:00:00Z",
                                          "bookingDeadline": "2026-08-05T08:30:00Z",
                                          "price": 5.50
                                        }
                                        """)
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority("ROLE_ADMIN")
                                ))
                )
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "/api/v1/admin/trips/" + TRIP_NUMBER
                ))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.tripNumber")
                        .value(TRIP_NUMBER))
                .andExpect(jsonPath("$.data.status")
                        .value("DRAFT"));

        verify(applicationService).createDraftTrip(any());
    }

    @Test
    void shouldRejectStudentCreatingDraftTrip() throws Exception {
        mockMvc.perform(
                        post("/api/v1/admin/trips")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "vehicleNo": "33333333-3333-3333-3333-333333333333",
                                          "routeNo": "22222222-2222-2222-2222-222222222222",
                                          "departureTime": "2026-08-05T09:00:00Z",
                                          "bookingDeadline": "2026-08-05T08:30:00Z",
                                          "price": 5.50
                                        }
                                        """)
                                .with(jwt().jwt(builder -> builder
                                        .subject("1000001")
                                        .claim("roles", List.of("STUDENT"))))
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(applicationService);
    }

    @Test
    void shouldPublishTripForAdmin() throws Exception {
        when(applicationService.publishTrip(any()))
                .thenReturn(publishedTripView());

        mockMvc.perform(
                        post("/api/v1/admin/trips/" + TRIP_NUMBER + "/publish")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "version": 0
                                        }
                                        """)
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority("ROLE_ADMIN")
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status")
                        .value("OPEN_FOR_BOOKING"));

        verify(applicationService).publishTrip(any());
    }

    @Test
    void shouldReturnTripDetailForAdmin() throws Exception {
        when(applicationService.findByTripNumber(TRIP_NUMBER))
                .thenReturn(publishedTripView());

        mockMvc.perform(
                        get("/api/v1/admin/trips/" + TRIP_NUMBER)
                                .with(jwt().authorities(
                                        new SimpleGrantedAuthority("ROLE_ADMIN")
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tripId").value(1001L));

        verify(applicationService).findByTripNumber(TRIP_NUMBER);
    }

    private TripAdminView draftTripView() {
        return new TripAdminView(
                1001L,
                TRIP_NUMBER,
                3001L,
                2001L,
                Instant.parse("2026-08-05T09:00:00Z"),
                Instant.parse("2026-08-05T08:30:00Z"),
                new BigDecimal("5.50"),
                TripStatus.DRAFT,
                0L,
                Instant.parse("2026-08-04T00:00:00Z"),
                Instant.parse("2026-08-04T00:00:00Z")
        );
    }

    private TripAdminView publishedTripView() {
        return new TripAdminView(
                1001L,
                TRIP_NUMBER,
                3001L,
                2001L,
                Instant.parse("2026-08-05T09:00:00Z"),
                Instant.parse("2026-08-05T08:30:00Z"),
                new BigDecimal("5.50"),
                TripStatus.OPEN_FOR_BOOKING,
                1L,
                Instant.parse("2026-08-04T00:00:00Z"),
                Instant.parse("2026-08-04T01:00:00Z")
        );
    }
}

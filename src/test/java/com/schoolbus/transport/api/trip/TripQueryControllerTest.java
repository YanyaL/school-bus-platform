package com.schoolbus.transport.api.trip;

import com.schoolbus.shared.api.GlobalExceptionHandler;
import com.schoolbus.shared.config.SecurityConfig;
import com.schoolbus.transport.application.trip.BookableTripView;
import com.schoolbus.transport.application.trip.TripQueryApplicationService;
import com.schoolbus.transport.application.trip.TripSeatMapView;
import com.schoolbus.transport.application.trip.TripSeatQueryApplicationService;
import com.schoolbus.transport.application.trip.TripSeatStatusView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("web-test")
@WebMvcTest(TripQueryController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class
})
class TripQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TripQueryApplicationService applicationService;

    @MockitoBean
    private TripSeatQueryApplicationService seatQueryApplicationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldReturnBookableTripsForAuthenticatedUser()
            throws Exception {
        when(applicationService.findBookableTrips(20))
                .thenReturn(List.of(bookableTrip()));

        mockMvc.perform(
                        get("/api/v1/trips")
                                .with(jwt().jwt(builder -> builder
                                        .subject("1000001")
                                        .claim(
                                                "roles",
                                                List.of("STUDENT")
                                        )))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].tripId")
                        .value(1001L))
                .andExpect(jsonPath("$.data[0].tripNumber")
                        .value(
                                "11111111-1111-1111-1111-111111111111"
                        ))
                .andExpect(jsonPath("$.data[0].vehicleId")
                        .value(3001L))
                .andExpect(jsonPath("$.data[0].routeId")
                        .value(2001L))
                .andExpect(jsonPath("$.data[0].departureTime")
                        .value("2026-08-05T09:00:00Z"))
                .andExpect(jsonPath("$.data[0].price")
                        .value(5.00));

        verify(applicationService).findBookableTrips(20);
    }

    @Test
    void shouldPassRequestedLimitToApplicationService()
            throws Exception {
        when(applicationService.findBookableTrips(5))
                .thenReturn(List.of());

        mockMvc.perform(
                        get("/api/v1/trips")
                                .param("limit", "5")
                                .with(jwt())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        verify(applicationService).findBookableTrips(5);
    }

    @Test
    void shouldRejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/v1/trips"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(applicationService);
    }

    @Test
    void shouldRejectLimitBelowOne() throws Exception {
        mockMvc.perform(
                        get("/api/v1/trips")
                                .param("limit", "0")
                                .with(jwt())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void shouldRejectLimitAboveOneHundred() throws Exception {
        mockMvc.perform(
                        get("/api/v1/trips")
                                .param("limit", "101")
                                .with(jwt())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void shouldReturnTripSeatMapForAuthenticatedUser() throws Exception {
        when(seatQueryApplicationService.findTripSeatMap(1001L))
                .thenReturn(new TripSeatMapView(
                        1001L,
                        Instant.parse("2026-08-05T08:30:00Z"),
                        List.of(
                                new TripSeatStatusView(
                                        "A01",
                                        "AVAILABLE"
                                ),
                                new TripSeatStatusView(
                                        "A02",
                                        "LOCKED"
                                )
                        )
                ));

        mockMvc.perform(
                        get("/api/v1/trips/1001/seats")
                                .with(jwt().jwt(builder -> builder
                                        .subject("1000001")))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tripId").value(1001L))
                .andExpect(jsonPath("$.data.seats.length()").value(2))
                .andExpect(jsonPath("$.data.seats[0].seatNumber")
                        .value("A01"))
                .andExpect(jsonPath("$.data.seats[1].status")
                        .value("LOCKED"));

        verify(seatQueryApplicationService).findTripSeatMap(1001L);
    }

    private BookableTripView bookableTrip() {
        return new BookableTripView(
                1001L,
                "11111111-1111-1111-1111-111111111111",
                3001L,
                2001L,
                Instant.parse("2026-08-05T09:00:00Z"),
                Instant.parse("2026-08-05T08:30:00Z"),
                new BigDecimal("5.00")
        );
    }
}

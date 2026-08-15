package com.schoolbus.transportquery.api;

import com.schoolbus.transportquery.application.BookableTripQueryService;
import com.schoolbus.transportquery.application.BookableTripView;
import com.schoolbus.transportquery.application.TripSeatMapView;
import com.schoolbus.transportquery.application.TripSeatQueryService;
import com.schoolbus.transportquery.application.TripSeatStatusView;
import com.schoolbus.transportquery.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TripQueryController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class TripQueryControllerTest {

    private static final String TRIP_NUMBER =
            "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookableTripQueryService bookableTripQueryService;

    @MockitoBean
    private TripSeatQueryService tripSeatQueryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldReturnBookableTripsForAuthenticatedUser() throws Exception {
        when(bookableTripQueryService.findBookableTrips(20))
                .thenReturn(List.of(bookableTrip()));

        mockMvc.perform(
                        get("/api/v1/trips")
                                .with(jwt().jwt(builder -> builder
                                        .subject("1000001")
                                        .claim("roles", List.of("STUDENT"))))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].tripId").doesNotExist())
                .andExpect(jsonPath("$.data[0].tripNumber").value(TRIP_NUMBER))
                .andExpect(jsonPath("$.data[0].vehicleId").value("3001"))
                .andExpect(jsonPath("$.data[0].routeId").value("2001"));
    }

    @Test
    void shouldRejectUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/trips"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "101"})
    void shouldRejectInvalidLimit(String limit) throws Exception {
        mockMvc.perform(
                        get("/api/v1/trips")
                                .param("limit", limit)
                                .with(jwt())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldRejectInvalidTripNumber() throws Exception {
        when(tripSeatQueryService.findTripSeatMap("not-a-uuid"))
                .thenThrow(new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "tripNumber must be a valid UUID"
                ));

        mockMvc.perform(
                        get("/api/v1/trips/not-a-uuid/seats")
                                .with(jwt())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturnNotFoundForMissingTrip() throws Exception {
        when(tripSeatQueryService.findTripSeatMap(TRIP_NUMBER))
                .thenThrow(new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "trip not found: " + TRIP_NUMBER
                ));

        mockMvc.perform(
                        get("/api/v1/trips/" + TRIP_NUMBER + "/seats")
                                .with(jwt())
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldReturnSeatsSortedBySeatNumberContract() throws Exception {
        when(tripSeatQueryService.findTripSeatMap(TRIP_NUMBER))
                .thenReturn(new TripSeatMapView(
                        TRIP_NUMBER,
                        Instant.parse("2026-08-05T08:00:00Z"),
                        List.of(
                                new TripSeatStatusView("A01", "AVAILABLE"),
                                new TripSeatStatusView("A02", "LOCKED"),
                                new TripSeatStatusView("B01", "SOLD")
                        )
                ));

        mockMvc.perform(
                        get("/api/v1/trips/" + TRIP_NUMBER + "/seats")
                                .with(jwt())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tripId").doesNotExist())
                .andExpect(jsonPath("$.data.tripNumber").value(TRIP_NUMBER))
                .andExpect(jsonPath("$.data.seats[0].seatNumber").value("A01"))
                .andExpect(jsonPath("$.data.seats[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.seats[1].status").value("LOCKED"))
                .andExpect(jsonPath("$.data.seats[2].status").value("SOLD"));

        verify(tripSeatQueryService).findTripSeatMap(TRIP_NUMBER);
    }

    private static BookableTripView bookableTrip() {
        return new BookableTripView(
                9_007_199_254_740_991L,
                TRIP_NUMBER,
                3001L,
                2001L,
                Instant.parse("2026-08-05T09:00:00Z"),
                Instant.parse("2026-08-05T08:00:00Z"),
                new BigDecimal("5.00")
        );
    }
}

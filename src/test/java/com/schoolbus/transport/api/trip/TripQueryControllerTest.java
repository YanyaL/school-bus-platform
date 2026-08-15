package com.schoolbus.transport.api.trip;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;
import com.schoolbus.shared.api.GlobalExceptionHandler;
import com.schoolbus.shared.config.SecurityConfig;
import com.schoolbus.transport.application.trip.BookableTripView;
import com.schoolbus.transport.application.trip.TripQueryApplicationService;
import com.schoolbus.transport.application.trip.TripSeatMapView;
import com.schoolbus.transport.application.trip.TripSeatQueryApplicationService;
import com.schoolbus.transport.application.trip.TripSeatStatusView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    private static final String TRIP_NUMBER =
            "11111111-1111-1111-1111-111111111111";

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
                .andExpect(jsonPath("$.data[0].tripId").doesNotExist())
                .andExpect(jsonPath("$.data[0].tripNumber")
                        .value(TRIP_NUMBER))
                .andExpect(jsonPath("$.data[0].vehicleId")
                        .value("3001"))
                .andExpect(jsonPath("$.data[0].routeId")
                        .value("2001"))
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
        when(seatQueryApplicationService.findTripSeatMap(TRIP_NUMBER))
                .thenReturn(new TripSeatMapView(
                        TRIP_NUMBER,
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
                        get("/api/v1/trips/" + TRIP_NUMBER + "/seats")
                                .with(jwt().jwt(builder -> builder
                                        .subject("1000001")))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tripNumber")
                        .value(TRIP_NUMBER))
                .andExpect(jsonPath("$.data.tripId").doesNotExist())
                .andExpect(jsonPath("$.data.seats.length()").value(2))
                .andExpect(jsonPath("$.data.seats[0].seatNumber")
                        .value("A01"))
                .andExpect(jsonPath("$.data.seats[1].status")
                        .value("LOCKED"));

        verify(seatQueryApplicationService)
                .findTripSeatMap(TRIP_NUMBER);
    }

    @Test
    void shouldReturnNotFoundForUnknownTripNumber() throws Exception {
        when(seatQueryApplicationService.findTripSeatMap(TRIP_NUMBER))
                .thenThrow(new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "trip not found: " + TRIP_NUMBER
                ));

        mockMvc.perform(
                        get("/api/v1/trips/" + TRIP_NUMBER + "/seats")
                                .with(jwt())
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldKeepSnowflakeIdsAsJsonStrings() throws Exception {
        when(applicationService.findBookableTrips(20))
                .thenReturn(List.of(new BookableTripView(
                        81_765_424_194_125_824L,
                        "11111111-1111-1111-1111-111111111111",
                        81_765_424_194_125_824L,
                        81_765_424_194_125_824L,
                        Instant.parse("2026-08-05T09:00:00Z"),
                        Instant.parse("2026-08-05T08:30:00Z"),
                        new BigDecimal("5.00")
                )));

        mockMvc.perform(
                        get("/api/v1/trips")
                                .with(jwt().jwt(builder -> builder
                                        .subject("1000001")))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].vehicleId")
                        .value("81765424194125824"))
                .andExpect(jsonPath("$.data[0].routeId")
                        .value("81765424194125824"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abc",
            "1001",
            "11111111-1111-1111-1111-1111111111zz"
    })
    void shouldRejectInvalidTripNumber(String tripNumber)
            throws Exception {
        mockMvc.perform(
                        get("/api/v1/trips/" + tripNumber + "/seats")
                                .with(jwt())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"));

        verifyNoInteractions(seatQueryApplicationService);
    }

    private BookableTripView bookableTrip() {
        return new BookableTripView(
                1001L,
                TRIP_NUMBER,
                3001L,
                2001L,
                Instant.parse("2026-08-05T09:00:00Z"),
                Instant.parse("2026-08-05T08:30:00Z"),
                new BigDecimal("5.00")
        );
    }
}

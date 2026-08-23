package com.schoolbus.bookingservice.api;

import com.schoolbus.bookingservice.application.booking.BookingApplicationService;
import com.schoolbus.bookingservice.application.booking.BookingCancellationApplicationService;
import com.schoolbus.bookingservice.application.booking.BookingDetailView;
import com.schoolbus.bookingservice.application.booking.BookingAlreadyExistsException;
import com.schoolbus.bookingservice.application.booking.CancelBookingResult;
import com.schoolbus.bookingservice.application.booking.BookingQueryApplicationService;
import com.schoolbus.bookingservice.application.booking.BookingRequestConflictException;
import com.schoolbus.bookingservice.application.booking.BookingSummaryView;
import com.schoolbus.bookingservice.application.booking.ListMyBookingsQuery;
import com.schoolbus.bookingservice.application.booking.CreateBookingCommand;
import com.schoolbus.bookingservice.application.booking.CreateBookingOutcome;
import com.schoolbus.bookingservice.application.booking.CreateBookingResult;
import com.schoolbus.bookingservice.application.booking.SeatAlreadyReservedException;
import com.schoolbus.bookingservice.application.booking.BookingNotCancellableException;
import com.schoolbus.bookingservice.application.booking.BookingNotFoundException;
import com.schoolbus.bookingservice.domain.order.BookingNumber;
import com.schoolbus.bookingservice.domain.order.BookingRequestNumber;
import com.schoolbus.bookingservice.domain.order.BookingStatus;
import com.schoolbus.bookingservice.domain.order.CancellationReason;
import com.schoolbus.bookingservice.domain.order.SeatNumber;
import com.schoolbus.bookingservice.domain.trip.TripReference;
import com.schoolbus.bookingservice.shared.api.GlobalExceptionHandler;
import com.schoolbus.bookingservice.security.SecurityConfig;
import com.schoolbus.bookingservice.shared.domain.identity.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
@WebMvcTest(BookingController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class
})
class BookingControllerTest {

    private static final String IDEMPOTENCY_KEY =
            "550e8400-e29b-41d4-a716-446655440000";
    private static final String TRIP_NUMBER =
            "22222222-2222-2222-2222-222222222222";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingApplicationService applicationService;

    @MockitoBean
    private BookingQueryApplicationService queryApplicationService;

    @MockitoBean
    private BookingCancellationApplicationService cancellationApplicationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldCreateBookingForAuthenticatedStudent() throws Exception {
        when(applicationService.createBookingOutcome(any(CreateBookingCommand.class)))
                .thenReturn(new CreateBookingOutcome(
                        createBookingResult(),
                        false
                ));

        mockMvc.perform(
                        post("/api/v1/bookings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(
                                        BookingController.IDEMPOTENCY_KEY_HEADER,
                                        IDEMPOTENCY_KEY
                                )
                                .content("""
                                        {
                                          "tripNumber": "22222222-2222-2222-2222-222222222222",
                                          "seatNumber": "A01"
                                        }
                                        """)
                                .with(jwt().jwt(builder -> builder
                                        .subject("1000001")
                                        .claim(
                                                "roles",
                                                java.util.List.of("STUDENT")
                                        )))
                )
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "/api/v1/bookings/"
                                + "55555555-5555-5555-5555-555555555555"
                ))
                .andExpect(header().string(
                        BookingController.IDEMPOTENCY_REPLAYED_HEADER,
                        "false"
                ))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.bookingId").value("5001"))
                .andExpect(jsonPath("$.data.bookingNumber")
                        .value("55555555-5555-5555-5555-555555555555"))
                .andExpect(jsonPath("$.data.tripNumber")
                        .value(TRIP_NUMBER))
                .andExpect(jsonPath("$.data.seatNumber").value("A01"))
                .andExpect(jsonPath("$.data.amount").value(5.50))
                .andExpect(jsonPath("$.data.status")
                        .value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.expiresAt")
                        .value("2026-08-08T00:15:00Z"));

        verify(applicationService).createBookingOutcome(
                new CreateBookingCommand(
                        1000001L,
                        TRIP_NUMBER,
                        "A01",
                        IDEMPOTENCY_KEY
                )
        );
    }

    @Test
    void shouldReturnIdempotentReplayHeader() throws Exception {
        when(applicationService.createBookingOutcome(any(CreateBookingCommand.class)))
                .thenReturn(new CreateBookingOutcome(
                        createBookingResult(),
                        true
                ));

        mockMvc.perform(
                        post("/api/v1/bookings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(
                                        BookingController.IDEMPOTENCY_KEY_HEADER,
                                        IDEMPOTENCY_KEY
                                )
                                .content("""
                                        {
                                          "tripNumber": "22222222-2222-2222-2222-222222222222",
                                          "seatNumber": "A01"
                                        }
                                        """)
                                .with(jwt().jwt(builder -> builder.subject("1000001")))
                )
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        BookingController.IDEMPOTENCY_REPLAYED_HEADER,
                        "true"
                ));
    }

    @Test
    void shouldRejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(
                        post("/api/v1/bookings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(
                                        BookingController.IDEMPOTENCY_KEY_HEADER,
                                        IDEMPOTENCY_KEY
                                )
                                .content("""
                                        {
                                          "tripNumber": "22222222-2222-2222-2222-222222222222",
                                          "seatNumber": "A01"
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(applicationService);
    }

    @Test
    void shouldRejectMissingIdempotencyKey() throws Exception {
        mockMvc.perform(
                        post("/api/v1/bookings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "tripNumber": "22222222-2222-2222-2222-222222222222",
                                          "seatNumber": "A01"
                                        }
                                        """)
                                .with(jwt())
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(applicationService);
    }

    @Test
    void shouldRejectBlankIdempotencyKey() throws Exception {
        mockMvc.perform(
                        post("/api/v1/bookings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(
                                        BookingController.IDEMPOTENCY_KEY_HEADER,
                                        "   "
                                )
                                .content("""
                                        {
                                          "tripNumber": "22222222-2222-2222-2222-222222222222",
                                          "seatNumber": "A01"
                                        }
                                        """)
                                .with(jwt())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void shouldRejectIdempotencyKeyLongerThanSixtyFourCharacters()
            throws Exception {
        mockMvc.perform(
                        post("/api/v1/bookings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(
                                        BookingController.IDEMPOTENCY_KEY_HEADER,
                                        "a".repeat(65)
                                )
                                .content("""
                                        {
                                          "tripNumber": "22222222-2222-2222-2222-222222222222",
                                          "seatNumber": "A01"
                                        }
                                        """)
                                .with(jwt())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void shouldRejectInvalidSeatNumberFormat() throws Exception {
        mockMvc.perform(
                        post("/api/v1/bookings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(
                                        BookingController.IDEMPOTENCY_KEY_HEADER,
                                        IDEMPOTENCY_KEY
                                )
                                .content("""
                                        {
                                          "tripNumber": "22222222-2222-2222-2222-222222222222",
                                          "seatNumber": "seat 1"
                                        }
                                        """)
                                .with(jwt())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"));

        verifyNoInteractions(applicationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abc",
            "2001",
            "22222222-2222-2222-2222-2222222222zz"
    })
    void shouldRejectInvalidTripNumber(String tripNumber)
            throws Exception {
        mockMvc.perform(
                        post("/api/v1/bookings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(
                                        BookingController.IDEMPOTENCY_KEY_HEADER,
                                        IDEMPOTENCY_KEY
                                )
                                .content("""
                                        {
                                          "tripNumber": "%s",
                                          "seatNumber": "A01"
                                        }
                                        """.formatted(tripNumber))
                                .with(jwt().jwt(builder -> builder
                                        .subject("1000001")))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"));

        verifyNoInteractions(applicationService);
    }

    @Test
    void shouldMapSeatAlreadyReservedConflict() throws Exception {
        when(applicationService.createBookingOutcome(any(CreateBookingCommand.class)))
                .thenThrow(new SeatAlreadyReservedException(
                        TripReference.of(2001L),
                        SeatNumber.of("A01")
                ));

        mockMvc.perform(
                        post("/api/v1/bookings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(
                                        BookingController.IDEMPOTENCY_KEY_HEADER,
                                        IDEMPOTENCY_KEY
                                )
                                .content("""
                                        {
                                          "tripNumber": "22222222-2222-2222-2222-222222222222",
                                          "seatNumber": "A01"
                                        }
                                        """)
                                .with(jwt().jwt(builder -> builder.subject("1000001")))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("SEAT_ALREADY_RESERVED"));
    }

    @Test
    void shouldMapBookingAlreadyExistsConflict() throws Exception {
        when(applicationService.createBookingOutcome(any(CreateBookingCommand.class)))
                .thenThrow(new BookingAlreadyExistsException(
                        UserId.of(1000001L),
                        TripReference.of(2001L)
                ));

        mockMvc.perform(
                        post("/api/v1/bookings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(
                                        BookingController.IDEMPOTENCY_KEY_HEADER,
                                        IDEMPOTENCY_KEY
                                )
                                .content("""
                                        {
                                          "tripNumber": "22222222-2222-2222-2222-222222222222",
                                          "seatNumber": "A01"
                                        }
                                        """)
                                .with(jwt().jwt(builder -> builder.subject("1000001")))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("BOOKING_ALREADY_EXISTS"));
    }

    @Test
    void shouldMapBookingRequestConflict() throws Exception {
        when(applicationService.createBookingOutcome(any(CreateBookingCommand.class)))
                .thenThrow(new BookingRequestConflictException(
                        BookingRequestNumber.of(IDEMPOTENCY_KEY)
                ));

        mockMvc.perform(
                        post("/api/v1/bookings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(
                                        BookingController.IDEMPOTENCY_KEY_HEADER,
                                        IDEMPOTENCY_KEY
                                )
                                .content("""
                                        {
                                          "tripNumber": "22222222-2222-2222-2222-222222222222",
                                          "seatNumber": "A01"
                                        }
                                        """)
                                .with(jwt().jwt(builder -> builder.subject("1000001")))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("BOOKING_REQUEST_CONFLICT"));
    }

    @Test
    void shouldListMyBookingsForAuthenticatedStudent() throws Exception {
        when(queryApplicationService.listMyBookings(
                any(ListMyBookingsQuery.class)
        )).thenReturn(List.of(bookingSummary()));
        when(queryApplicationService.countMyBookings(
                any(ListMyBookingsQuery.class)
        )).thenReturn(1L);

        mockMvc.perform(
                        get("/api/v1/bookings")
                                .with(jwt().jwt(builder -> builder
                                        .subject("1000001")
                                        .claim(
                                                "roles",
                                                List.of("STUDENT")
                                        )))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.items[0].bookingId")
                        .value("5001"))
                .andExpect(jsonPath("$.data.items[0].bookingNumber")
                        .value("55555555-5555-5555-5555-555555555555"))
                .andExpect(jsonPath("$.data.items[0].tripNumber")
                        .value(TRIP_NUMBER))
                .andExpect(jsonPath("$.data.items[0].status")
                        .value("PENDING_PAYMENT"));

        verify(queryApplicationService).listMyBookings(
                new ListMyBookingsQuery(
                        1000001L,
                        null,
                        0,
                        20,
                        false
                )
        );
    }

    @Test
    void shouldFilterBookingsByStatus() throws Exception {
        when(queryApplicationService.listMyBookings(
                any(ListMyBookingsQuery.class)
        )).thenReturn(List.of());
        when(queryApplicationService.countMyBookings(
                any(ListMyBookingsQuery.class)
        )).thenReturn(0L);

        mockMvc.perform(
                        get("/api/v1/bookings")
                                .param("status", "PENDING_PAYMENT")
                                .param("page", "1")
                                .param("size", "10")
                                .param("sort", "createdAt,asc")
                                .with(jwt().jwt(builder -> builder.subject("1000001")))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(10));

        verify(queryApplicationService).listMyBookings(
                new ListMyBookingsQuery(
                        1000001L,
                        BookingStatus.PENDING_PAYMENT,
                        1,
                        10,
                        true
                )
        );
    }

    @Test
    void shouldRejectUnauthenticatedListRequest() throws Exception {
        mockMvc.perform(get("/api/v1/bookings"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(queryApplicationService);
    }

    @Test
    void shouldRejectInvalidBookingStatusFilter() throws Exception {
        mockMvc.perform(
                        get("/api/v1/bookings")
                                .param("status", "UNKNOWN")
                                .with(jwt().jwt(builder -> builder.subject("1000001")))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"));

        verifyNoInteractions(queryApplicationService);
    }

    @Test
    void shouldRejectInvalidSortParameter() throws Exception {
        mockMvc.perform(
                        get("/api/v1/bookings")
                                .param("sort", "departureTime,asc")
                                .with(jwt().jwt(builder -> builder.subject("1000001")))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"));

        verifyNoInteractions(queryApplicationService);
    }

    @Test
    void shouldReturnBookingDetailForOwner() throws Exception {
        when(queryApplicationService.getMyBookingDetail(
                1000001L,
                BookingNumber.of(
                        "55555555-5555-5555-5555-555555555555"
                )
        )).thenReturn(bookingDetail());

        mockMvc.perform(
                        get("/api/v1/bookings/"
                                + "55555555-5555-5555-5555-555555555555")
                                .with(jwt().jwt(builder -> builder
                                        .subject("1000001")))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookingId").value("5001"))
                .andExpect(jsonPath("$.data.bookingNumber")
                        .value("55555555-5555-5555-5555-555555555555"))
                .andExpect(jsonPath("$.data.tripNumber")
                        .value(TRIP_NUMBER))
                .andExpect(jsonPath("$.data.status")
                        .value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.createdAt")
                        .value("2026-08-08T00:00:00Z"));
    }

    @Test
    void shouldReturnNotFoundForMissingBookingDetail() throws Exception {
        when(queryApplicationService.getMyBookingDetail(
                1000001L,
                BookingNumber.of(
                        "55555555-5555-5555-5555-555555555555"
                )
        )).thenThrow(new BookingNotFoundException(
                BookingNumber.of(
                        "55555555-5555-5555-5555-555555555555"
                )
        ));

        mockMvc.perform(
                        get("/api/v1/bookings/"
                                + "55555555-5555-5555-5555-555555555555")
                                .with(jwt().jwt(builder -> builder
                                        .subject("1000001")))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldCancelPendingBooking() throws Exception {
        when(cancellationApplicationService.cancelMyBooking(
                1000001L,
                "55555555-5555-5555-5555-555555555555"
        )).thenReturn(new CancelBookingResult(
                "55555555-5555-5555-5555-555555555555",
                BookingStatus.CANCELLED,
                CancellationReason.USER_CANCELLED,
                Instant.parse("2026-08-08T00:05:00Z"),
                true
        ));

        mockMvc.perform(
                        post("/api/v1/bookings/"
                                + "55555555-5555-5555-5555-555555555555"
                                + "/cancellation")
                                .with(jwt().jwt(builder -> builder
                                        .subject("1000001")))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status")
                        .value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelReason")
                        .value("USER_CANCELLED"));
    }

    @Test
    void shouldCancelPaidBookingIntoRefundPending() throws Exception {
        when(cancellationApplicationService.cancelMyBooking(
                1000001L,
                "55555555-5555-5555-5555-555555555555"
        )).thenReturn(new CancelBookingResult(
                "55555555-5555-5555-5555-555555555555",
                BookingStatus.REFUND_PENDING,
                CancellationReason.USER_CANCELLED,
                Instant.parse("2026-08-08T00:05:00Z"),
                true
        ));

        mockMvc.perform(
                        post("/api/v1/bookings/"
                                + "55555555-5555-5555-5555-555555555555"
                                + "/cancellation")
                                .with(jwt().jwt(builder -> builder
                                        .subject("1000001")))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status")
                        .value("REFUND_PENDING"))
                .andExpect(jsonPath("$.data.cancelReason")
                        .value("USER_CANCELLED"));
    }

    private BookingDetailView bookingDetail() {
        return new BookingDetailView(
                5001L,
                "55555555-5555-5555-5555-555555555555",
                TRIP_NUMBER,
                "A01",
                new BigDecimal("5.50"),
                BookingStatus.PENDING_PAYMENT,
                Instant.parse("2026-08-08T00:15:00Z"),
                null,
                null,
                null,
                Instant.parse("2026-08-08T00:00:00Z")
        );
    }

    private BookingSummaryView bookingSummary() {
        return new BookingSummaryView(
                5001L,
                "55555555-5555-5555-5555-555555555555",
                TRIP_NUMBER,
                "A01",
                new BigDecimal("5.50"),
                BookingStatus.PENDING_PAYMENT,
                Instant.parse("2026-08-08T00:15:00Z"),
                Instant.parse("2026-08-08T00:00:00Z")
        );
    }

    private CreateBookingResult createBookingResult() {
        return new CreateBookingResult(
                5001L,
                "55555555-5555-5555-5555-555555555555",
                1000001L,
                TRIP_NUMBER,
                "A01",
                new BigDecimal("5.50"),
                BookingStatus.PENDING_PAYMENT,
                Instant.parse("2026-08-08T00:15:00Z")
        );
    }
}

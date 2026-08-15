package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.order.BookingStatus;
import com.schoolbus.booking.domain.order.SeatNumber;
import com.schoolbus.booking.domain.trip.TripReference;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingApplicationServiceTest {

    private static final String TRIP_NUMBER =
            "22222222-2222-2222-2222-222222222222";
    private static final CreateBookingCommand COMMAND =
            new CreateBookingCommand(
                    1001L,
                    TRIP_NUMBER,
                    "A01",
                    "request-5001"
            );
    private static final CreateBookingResult RESULT =
            new CreateBookingResult(
                    5001L,
                    "55555555-5555-5555-5555-555555555555",
                    1001L,
                    TRIP_NUMBER,
                    "A01",
                    new BigDecimal("5.50"),
                    BookingStatus.PENDING_PAYMENT,
                    Instant.parse("2026-08-08T00:15:00Z")
            );

    @Test
    void shouldRetryOptimisticConflictInFreshAttempts() {
        BookingCreationTransaction transaction =
                mock(BookingCreationTransaction.class);
        when(transaction.findIdempotentResult(COMMAND))
                .thenReturn(Optional.empty());
        when(transaction.createOnce(COMMAND))
                .thenThrow(new OptimisticLockingFailureException(
                        "conflict"
                ))
                .thenThrow(new OptimisticLockingFailureException(
                        "conflict"
                ))
                .thenReturn(RESULT);
        BookingApplicationService service =
                new BookingApplicationService(transaction, 3);

        assertThat(service.createBooking(COMMAND)).isEqualTo(RESULT);
        verify(transaction, times(3)).createOnce(COMMAND);
    }

    @Test
    void shouldExposeBusinessConflictAfterMaximumAttempts() {
        BookingCreationTransaction transaction =
                mock(BookingCreationTransaction.class);
        when(transaction.findIdempotentResult(COMMAND))
                .thenReturn(Optional.empty());
        when(transaction.createOnce(COMMAND))
                .thenThrow(new OptimisticLockingFailureException(
                        "conflict"
                ));
        BookingApplicationService service =
                new BookingApplicationService(transaction, 3);

        assertThatThrownBy(() -> service.createBooking(COMMAND))
                .isInstanceOf(BookingConcurrencyException.class);
        verify(transaction, times(3)).createOnce(COMMAND);
    }

    @Test
    void shouldNotRetrySeatBusinessConflict() {
        BookingCreationTransaction transaction =
                mock(BookingCreationTransaction.class);
        SeatAlreadyReservedException conflict =
                new SeatAlreadyReservedException(
                        TripReference.of(2001L),
                        SeatNumber.of("A01")
                );
        when(transaction.findIdempotentResult(COMMAND))
                .thenReturn(Optional.empty());
        when(transaction.createOnce(COMMAND)).thenThrow(conflict);
        BookingApplicationService service =
                new BookingApplicationService(transaction, 3);

        assertThatThrownBy(() -> service.createBooking(COMMAND))
                .isSameAs(conflict);
        verify(transaction).createOnce(COMMAND);
    }

    @Test
    void shouldRecoverConcurrentDuplicateIdempotentRequest() {
        BookingCreationTransaction transaction =
                mock(BookingCreationTransaction.class);
        when(transaction.findIdempotentResult(COMMAND))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(RESULT));
        when(transaction.createOnce(COMMAND))
                .thenThrow(new SeatAlreadyReservedException(
                        TripReference.of(2001L),
                        SeatNumber.of("A01")
                ));
        BookingApplicationService service =
                new BookingApplicationService(transaction, 3);

        CreateBookingOutcome outcome = service.createBookingOutcome(COMMAND);
        assertThat(outcome.result()).isEqualTo(RESULT);
        assertThat(outcome.idempotencyReplayed()).isTrue();
        verify(transaction).createOnce(COMMAND);
    }

    @Test
    void shouldReturnExistingResultForIdempotentRequest() {
        BookingCreationTransaction transaction =
                mock(BookingCreationTransaction.class);
        when(transaction.findIdempotentResult(COMMAND))
                .thenReturn(Optional.of(RESULT));
        BookingApplicationService service =
                new BookingApplicationService(transaction, 3);

        CreateBookingOutcome outcome = service.createBookingOutcome(COMMAND);
        assertThat(outcome.result()).isEqualTo(RESULT);
        assertThat(outcome.idempotencyReplayed()).isTrue();
        verify(transaction).findIdempotentResult(COMMAND);
        verify(transaction, times(0)).createOnce(COMMAND);
    }
}

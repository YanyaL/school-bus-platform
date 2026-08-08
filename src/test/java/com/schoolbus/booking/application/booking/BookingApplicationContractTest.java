package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.order.BookingAmount;
import com.schoolbus.booking.domain.order.BookingId;
import com.schoolbus.booking.domain.order.BookingNumber;
import com.schoolbus.booking.domain.order.BookingOrder;
import com.schoolbus.booking.domain.order.BookingRequestNumber;
import com.schoolbus.booking.domain.order.BookingStatus;
import com.schoolbus.booking.domain.order.SeatNumber;
import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.shared.api.ErrorCode;
import com.schoolbus.shared.domain.identity.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingApplicationContractTest {

    private static final Instant NOW =
            Instant.parse("2026-08-08T00:00:00Z");
    private static final Instant DEADLINE =
            Instant.parse("2026-08-08T01:00:00Z");
    private static final Instant DEPARTURE =
            Instant.parse("2026-08-08T02:00:00Z");

    @Test
    void shouldNormalizeCreateBookingCommand() {
        CreateBookingCommand command = new CreateBookingCommand(
                1001L,
                2001L,
                " a01 ",
                " request-5001 "
        );

        assertThat(command.seatNumber()).isEqualTo("A01");
        assertThat(command.requestNumber())
                .isEqualTo("request-5001");
    }

    @Test
    void shouldRejectInvalidCreateBookingCommand() {
        assertThatThrownBy(
                () -> new CreateBookingCommand(
                        0L,
                        2001L,
                        "A01",
                        "request-5001"
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId must be positive");

        assertThatThrownBy(
                () -> new CreateBookingCommand(
                        1001L,
                        2001L,
                        " ",
                        "request-5001"
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "seatNumber must contain 1 to 10 letters, digits or hyphens"
                );
    }

    @Test
    void shouldEvaluateBookableTripAtDeadlineBoundary() {
        BookableTripSnapshot snapshot = bookableTrip(true);

        assertThat(snapshot.canBookAt(DEADLINE.minusMillis(1)))
                .isTrue();
        assertThat(snapshot.canBookAt(DEADLINE)).isFalse();
        assertThat(snapshot.canBookAt(DEADLINE.plusMillis(1)))
                .isFalse();
        assertThat(bookableTrip(false).canBookAt(NOW)).isFalse();
    }

    @Test
    void shouldCreateApplicationResultFromDomainOrder() {
        BookingOrder order = BookingOrder.place(
                BookingId.of(5001L),
                BookingNumber.of(
                        "55555555-5555-5555-5555-555555555555"
                ),
                BookingRequestNumber.of("request-5001"),
                UserId.of(1001L),
                TripReference.of(2001L),
                SeatNumber.of("A01"),
                BookingAmount.of("5.50"),
                DEADLINE,
                NOW
        );

        CreateBookingResult result = CreateBookingResult.from(order);

        assertThat(result.bookingId()).isEqualTo(5001L);
        assertThat(result.bookingNumber()).isEqualTo(
                "55555555-5555-5555-5555-555555555555"
        );
        assertThat(result.status())
                .isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(result.amount())
                .isEqualByComparingTo("5.50");
    }

    @Test
    void shouldRequireSeatLockExpirationAfterLockTime() {
        assertThatThrownBy(
                () -> new SeatLockRequest(
                        TripReference.of(2001L),
                        SeatNumber.of("A01"),
                        BookingNumber.of(
                                "55555555-5555-5555-5555-555555555555"
                        ),
                        UserId.of(1001L),
                        NOW,
                        NOW
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "lockExpiresAt must be after lockedAt"
                );
    }

    @Test
    void shouldExposeSpecificBookingErrorCodes() {
        TripReference tripReference = TripReference.of(2001L);

        assertThat(
                new TripNotBookableException(tripReference)
                        .errorCode()
        ).isEqualTo(ErrorCode.TRIP_NOT_BOOKABLE);
        assertThat(
                new BookingAlreadyExistsException(
                        UserId.of(1001L),
                        tripReference
                ).errorCode()
        ).isEqualTo(ErrorCode.BOOKING_ALREADY_EXISTS);
        assertThat(
                new SeatAlreadyReservedException(
                        tripReference,
                        SeatNumber.of("A01")
                ).errorCode()
        ).isEqualTo(ErrorCode.SEAT_ALREADY_RESERVED);
    }

    private BookableTripSnapshot bookableTrip(
            boolean openForBooking
    ) {
        return new BookableTripSnapshot(
                TripReference.of(2001L),
                BookingAmount.of("5.50"),
                DEPARTURE,
                DEADLINE,
                openForBooking
        );
    }
}

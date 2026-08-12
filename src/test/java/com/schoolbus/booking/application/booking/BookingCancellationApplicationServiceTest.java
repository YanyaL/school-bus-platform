package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.order.BookingAmount;
import com.schoolbus.booking.domain.order.BookingId;
import com.schoolbus.booking.domain.order.BookingNumber;
import com.schoolbus.booking.domain.order.BookingOrder;
import com.schoolbus.booking.domain.order.BookingOrderRepository;
import com.schoolbus.booking.domain.order.BookingRequestNumber;
import com.schoolbus.booking.domain.order.BookingStatus;
import com.schoolbus.booking.domain.order.CancellationReason;
import com.schoolbus.booking.domain.order.SeatNumber;
import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;
import com.schoolbus.booking.domain.order.PaymentReference;
import com.schoolbus.shared.domain.identity.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingCancellationApplicationServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-08-08T00:05:00Z");

    @Mock
    private BookingOrderRepository bookingOrderRepository;

    @Mock
    private BookingCancellationTransaction cancellationTransaction;

    private BookingCancellationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new BookingCancellationApplicationService(
                bookingOrderRepository,
                cancellationTransaction,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldCancelPendingPaymentBooking() {
        BookingOrder pending = pendingOrder();
        when(bookingOrderRepository.findByBookingNumber(
                BookingNumber.of(
                        "55555555-5555-5555-5555-555555555555"
                )
        )).thenReturn(Optional.of(pending));
        BookingOrder cancelled = cancelledOrder();
        when(cancellationTransaction.cancelOne(
                pending.bookingId(),
                UserId.of(1001L),
                NOW
        )).thenReturn(cancelled);

        BookingCancellationView result = service.cancelMyBooking(
                new CancelMyBookingCommand(
                        1001L,
                        "55555555-5555-5555-5555-555555555555"
                )
        );

        assertThat(result.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(result.cancelReason())
                .isEqualTo(CancellationReason.USER_CANCELLED);
    }

    @Test
    void shouldReturnExistingCancellationIdempotently() {
        BookingOrder cancelled = cancelledOrder();
        when(bookingOrderRepository.findByBookingNumber(
                BookingNumber.of(
                        "55555555-5555-5555-5555-555555555555"
                )
        )).thenReturn(Optional.of(cancelled));

        BookingCancellationView result = service.cancelMyBooking(
                new CancelMyBookingCommand(
                        1001L,
                        "55555555-5555-5555-5555-555555555555"
                )
        );

        assertThat(result.status()).isEqualTo(BookingStatus.CANCELLED);
        verify(cancellationTransaction, never()).cancelOne(
                any(),
                any(),
                any()
        );
    }

    @Test
    void shouldRejectCancellationForPaidBooking() {
        BookingOrder paid = paidOrder();
        when(bookingOrderRepository.findByBookingNumber(
                BookingNumber.of(
                        "55555555-5555-5555-5555-555555555555"
                )
        )).thenReturn(Optional.of(paid));

        assertThatThrownBy(() -> service.cancelMyBooking(
                new CancelMyBookingCommand(
                        1001L,
                        "55555555-5555-5555-5555-555555555555"
                )
        ))
                .isInstanceOf(BookingNotCancellableException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).errorCode()
                ).isEqualTo(ErrorCode.BOOKING_NOT_CANCELLABLE));
    }

    @Test
    void shouldHideBookingOwnedByAnotherUser() {
        when(bookingOrderRepository.findByBookingNumber(
                BookingNumber.of(
                        "55555555-5555-5555-5555-555555555555"
                )
        )).thenReturn(Optional.of(pendingOrder()));

        assertThatThrownBy(() -> service.cancelMyBooking(
                new CancelMyBookingCommand(
                        2002L,
                        "55555555-5555-5555-5555-555555555555"
                )
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).errorCode()
                ).isEqualTo(ErrorCode.PAYMENT_BOOKING_NOT_FOUND));
    }

    private BookingOrder pendingOrder() {
        return BookingOrder.place(
                BookingId.of(5001L),
                BookingNumber.of(
                        "55555555-5555-5555-5555-555555555555"
                ),
                BookingRequestNumber.of("request-5001"),
                UserId.of(1001L),
                TripReference.of(2001L),
                SeatNumber.of("A01"),
                BookingAmount.of("5.50"),
                Instant.parse("2026-08-08T00:15:00Z"),
                Instant.parse("2026-08-08T00:00:00Z")
        );
    }

    private BookingOrder cancelledOrder() {
        BookingOrder order = pendingOrder();
        order.cancel(NOW);
        return order;
    }

    private BookingOrder paidOrder() {
        BookingOrder order = pendingOrder();
        order.confirmPayment(
                PaymentReference.of(
                        "77777777-7777-7777-7777-777777777777"
                ),
                NOW,
                NOW
        );
        return order;
    }
}

package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.inventory.SeatInventory;
import com.schoolbus.booking.domain.inventory.SeatInventoryRepository;
import com.schoolbus.booking.domain.order.BookingAmount;
import com.schoolbus.booking.domain.order.BookingId;
import com.schoolbus.booking.domain.order.BookingNumber;
import com.schoolbus.booking.domain.order.BookingOrder;
import com.schoolbus.booking.domain.order.BookingOrderRepository;
import com.schoolbus.booking.domain.order.BookingRequestNumber;
import com.schoolbus.booking.domain.order.BookingStatus;
import com.schoolbus.booking.domain.order.CancellationReason;
import com.schoolbus.booking.domain.order.PaymentReference;
import com.schoolbus.booking.domain.order.SeatNumber;
import com.schoolbus.booking.domain.trip.PublicTripNumber;
import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.payment.application.PaymentRefundOutboxPort;
import com.schoolbus.payment.application.RefundRequiredEvent;
import com.schoolbus.shared.domain.identity.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BookingCancellationTransactionTest {

    private static final Instant PLACED_AT =
            Instant.parse("2026-08-08T00:00:00Z");
    private static final Instant EXPIRES_AT =
            Instant.parse("2026-08-08T00:15:00Z");
    private static final Instant CANCELLED_AT =
            Instant.parse("2026-08-08T00:05:00Z");
    private static final Instant PAID_AT =
            Instant.parse("2026-08-08T00:03:00Z");
    private static final TripReference TRIP =
            TripReference.of(2001L);
    private static final PublicTripNumber TRIP_NUMBER =
            PublicTripNumber.of(
                    "22222222-2222-2222-2222-222222222222"
            );
    private static final BookingNumber BOOKING_NUMBER =
            BookingNumber.of(
                    "55555555-5555-5555-5555-555555555555"
            );
    private static final String PAYMENT_NO =
            "66666666-6666-6666-6666-666666666666";

    private BookingOrderRepository orderRepository;
    private SeatInventoryRepository inventoryRepository;
    private TripSeatReservationPort seatReservationPort;
    private PaymentRefundOutboxPort refundOutboxPort;
    private BookingCancellationTransaction transaction;

    @BeforeEach
    void setUp() {
        orderRepository = mock(BookingOrderRepository.class);
        inventoryRepository = mock(SeatInventoryRepository.class);
        seatReservationPort = mock(TripSeatReservationPort.class);
        refundOutboxPort = mock(PaymentRefundOutboxPort.class);
        Clock clock = Clock.fixed(
                CANCELLED_AT,
                ZoneOffset.UTC
        );
        transaction = new BookingCancellationTransaction(
                orderRepository,
                inventoryRepository,
                seatReservationPort,
                refundOutboxPort,
                clock
        );
    }

    @Test
    void shouldCancelPendingBookingAndReleaseResourcesWithoutRefundOutbox() {
        BookingOrder order = pendingOrder();
        SeatInventory inventory = reservedInventory();
        when(orderRepository.findByBookingNumber(BOOKING_NUMBER))
                .thenReturn(Optional.of(order));
        when(inventoryRepository.findByTripReference(TRIP))
                .thenReturn(Optional.of(inventory));
        when(seatReservationPort.releaseSeat(any()))
                .thenReturn(true);
        when(inventoryRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CancelBookingResult result = transaction.cancelOne(
                UserId.of(1001L),
                BOOKING_NUMBER
        );

        assertThat(result.newlyCancelled()).isTrue();
        assertThat(result.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(result.cancelReason())
                .isEqualTo(CancellationReason.USER_CANCELLED);
        verifyNoInteractions(refundOutboxPort);
    }

    @Test
    void shouldRequestRefundOutboxForPaidBookingCancel() {
        BookingOrder order = paidOrder();
        SeatInventory inventory = reservedInventory();
        when(orderRepository.findByBookingNumber(BOOKING_NUMBER))
                .thenReturn(Optional.of(order));
        when(inventoryRepository.findByTripReference(TRIP))
                .thenReturn(Optional.of(inventory));
        when(seatReservationPort.releaseSoldSeat(any()))
                .thenReturn(true);
        when(inventoryRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CancelBookingResult result = transaction.cancelOne(
                UserId.of(1001L),
                BOOKING_NUMBER
        );

        assertThat(result.newlyCancelled()).isTrue();
        assertThat(result.status()).isEqualTo(BookingStatus.REFUND_PENDING);
        ArgumentCaptor<RefundRequiredEvent> captor =
                ArgumentCaptor.forClass(RefundRequiredEvent.class);
        verify(refundOutboxPort).append(captor.capture());
        assertThat(captor.getValue().reason())
                .isEqualTo("USER_CANCELLED");
    }

    @Test
    void shouldReturnExistingCancelledBookingWithoutReleasingAgain() {
        BookingOrder order = pendingOrder();
        order.cancel(CANCELLED_AT);
        when(orderRepository.findByBookingNumber(BOOKING_NUMBER))
                .thenReturn(Optional.of(order));

        CancelBookingResult result = transaction.cancelOne(
                UserId.of(1001L),
                BOOKING_NUMBER
        );

        assertThat(result.newlyCancelled()).isFalse();
        verify(seatReservationPort, never()).releaseSeat(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldHideBookingOwnedByAnotherUser() {
        when(orderRepository.findByBookingNumber(BOOKING_NUMBER))
                .thenReturn(Optional.of(pendingOrder()));

        assertThatThrownBy(() -> transaction.cancelOne(
                UserId.of(9999L),
                BOOKING_NUMBER
        )).isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void shouldFailWhenSeatReleaseDoesNotMatch() {
        BookingOrder order = pendingOrder();
        when(orderRepository.findByBookingNumber(BOOKING_NUMBER))
                .thenReturn(Optional.of(order));
        when(inventoryRepository.findByTripReference(TRIP))
                .thenReturn(Optional.of(reservedInventory()));
        when(seatReservationPort.releaseSeat(any()))
                .thenReturn(false);

        assertThatThrownBy(() -> transaction.cancelOne(
                UserId.of(1001L),
                BOOKING_NUMBER
        )).isInstanceOf(OptimisticLockingFailureException.class);
    }

    private BookingOrder pendingOrder() {
        return BookingOrder.place(
                BookingId.of(5001L),
                BOOKING_NUMBER,
                BookingRequestNumber.of("request-5001"),
                UserId.of(1001L),
                TRIP,
                TRIP_NUMBER,
                SeatNumber.of("A01"),
                BookingAmount.of("5.50"),
                EXPIRES_AT,
                PLACED_AT
        );
    }

    private BookingOrder paidOrder() {
        BookingOrder order = pendingOrder();
        order.confirmPayment(
                PaymentReference.of(PAYMENT_NO),
                PAID_AT,
                PAID_AT.plusSeconds(1)
        );
        return order;
    }

    private SeatInventory reservedInventory() {
        SeatInventory inventory = SeatInventory.initialize(
                TRIP,
                50,
                PLACED_AT
        );
        inventory.reserve(PLACED_AT);
        return inventory;
    }
}

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
import com.schoolbus.booking.domain.order.SeatNumber;
import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.shared.domain.identity.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingExpirationTransactionTest {

    private static final Instant PLACED_AT =
            Instant.parse("2026-08-08T00:00:00Z");
    private static final Instant EXPIRES_AT =
            Instant.parse("2026-08-08T00:15:00Z");
    private static final TripReference TRIP =
            TripReference.of(2001L);

    private BookingOrderRepository orderRepository;
    private SeatInventoryRepository inventoryRepository;
    private TripSeatReservationPort seatReservationPort;
    private BookingExpirationTransaction transaction;

    @BeforeEach
    void setUp() {
        orderRepository = mock(BookingOrderRepository.class);
        inventoryRepository = mock(SeatInventoryRepository.class);
        seatReservationPort = mock(TripSeatReservationPort.class);
        transaction = new BookingExpirationTransaction(
                orderRepository,
                inventoryRepository,
                seatReservationPort
        );
    }

    @Test
    void shouldCancelOrderReleaseOwnedSeatAndRestoreInventory() {
        BookingOrder order = pendingOrder();
        SeatInventory inventory = reservedInventory();
        when(orderRepository.findById(BookingId.of(5001L)))
                .thenReturn(Optional.of(order));
        when(inventoryRepository.findByTripReference(TRIP))
                .thenReturn(Optional.of(inventory));
        when(seatReservationPort.releaseSeat(any()))
                .thenReturn(true);
        when(inventoryRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(transaction.expireOne(
                BookingId.of(5001L),
                EXPIRES_AT
        )).isTrue();

        assertThat(order.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(order.cancellationReason())
                .isEqualTo(CancellationReason.PAYMENT_TIMEOUT);
        assertThat(inventory.availableSeats()).isEqualTo(10);
        ArgumentCaptor<SeatReleaseRequest> releaseCaptor =
                ArgumentCaptor.forClass(SeatReleaseRequest.class);
        verify(seatReservationPort).releaseSeat(
                releaseCaptor.capture()
        );
        assertThat(releaseCaptor.getValue().bookingNumber())
                .isEqualTo(order.bookingNumber());
    }

    @Test
    void shouldIgnoreOrderThatIsNotExpired() {
        when(orderRepository.findById(BookingId.of(5001L)))
                .thenReturn(Optional.of(pendingOrder()));

        assertThat(transaction.expireOne(
                BookingId.of(5001L),
                EXPIRES_AT.minusMillis(1)
        )).isFalse();

        verify(seatReservationPort, never()).releaseSeat(any());
        verify(inventoryRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldReportConflictWhenSeatIsNoLongerOwnedByOrder() {
        BookingOrder order = pendingOrder();
        when(orderRepository.findById(BookingId.of(5001L)))
                .thenReturn(Optional.of(order));
        when(inventoryRepository.findByTripReference(TRIP))
                .thenReturn(Optional.of(reservedInventory()));
        when(seatReservationPort.releaseSeat(any()))
                .thenReturn(false);

        assertThatThrownBy(
                () -> transaction.expireOne(
                        BookingId.of(5001L),
                        EXPIRES_AT
                )
        ).isInstanceOf(OptimisticLockingFailureException.class);

        verify(inventoryRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    private BookingOrder pendingOrder() {
        return BookingOrder.place(
                BookingId.of(5001L),
                BookingNumber.of(
                        "55555555-5555-5555-5555-555555555555"
                ),
                BookingRequestNumber.of("request-5001"),
                UserId.of(1001L),
                TRIP,
                SeatNumber.of("A01"),
                BookingAmount.of("5.50"),
                EXPIRES_AT,
                PLACED_AT
        );
    }

    private SeatInventory reservedInventory() {
        SeatInventory inventory = SeatInventory.initialize(
                TRIP,
                10,
                PLACED_AT
        );
        inventory.reserve(PLACED_AT);
        return inventory;
    }
}

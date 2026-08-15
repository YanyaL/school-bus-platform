package com.schoolbus.booking.application.tripcancellation;

import com.schoolbus.booking.application.booking.TripSeatReservationPort;
import com.schoolbus.booking.domain.inventory.SeatInventory;
import com.schoolbus.booking.domain.inventory.SeatInventoryRepository;
import com.schoolbus.booking.domain.order.BookingAmount;
import com.schoolbus.booking.domain.order.BookingId;
import com.schoolbus.booking.domain.order.BookingNumber;
import com.schoolbus.booking.domain.order.BookingOrder;
import com.schoolbus.booking.domain.order.BookingOrderRepository;
import com.schoolbus.booking.domain.order.BookingRequestNumber;
import com.schoolbus.booking.domain.order.BookingStatus;
import com.schoolbus.booking.domain.order.PaymentReference;
import com.schoolbus.booking.domain.order.SeatNumber;
import com.schoolbus.booking.domain.trip.PublicTripNumber;
import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.shared.application.messaging.ConsumedEventStore;
import com.schoolbus.shared.domain.identity.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TripCancellationBookingTransactionTest {

    private static final Instant NOW =
            Instant.parse("2026-08-14T10:00:00Z");
    private static final TripReference TRIP = TripReference.of(7001L);
    private static final PublicTripNumber TRIP_NUMBER =
            PublicTripNumber.of(
                    "77777777-7777-7777-7777-777777777701"
            );

    private BookingOrderRepository orderRepository;
    private SeatInventoryRepository inventoryRepository;
    private TripSeatReservationPort seatReservationPort;
    private TripCancellationRefundPort refundPort;
    private TripCancellationSettlementOutboxPort settlementOutboxPort;
    private TripCancellationProgressPort progressPort;
    private ConsumedEventStore consumedEventStore;
    private TripCancellationBookingTransaction transaction;

    @BeforeEach
    void setUp() {
        orderRepository = mock(BookingOrderRepository.class);
        inventoryRepository = mock(SeatInventoryRepository.class);
        seatReservationPort = mock(TripSeatReservationPort.class);
        refundPort = mock(TripCancellationRefundPort.class);
        settlementOutboxPort = mock(TripCancellationSettlementOutboxPort.class);
        progressPort = mock(TripCancellationProgressPort.class);
        consumedEventStore = mock(ConsumedEventStore.class);
        transaction = new TripCancellationBookingTransaction(
                orderRepository,
                inventoryRepository,
                seatReservationPort,
                refundPort,
                settlementOutboxPort,
                progressPort,
                consumedEventStore,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldCancelPendingAndRequestRefundForPaidBooking() {
        BookingOrder pending = pendingOrder(1L, "A01", "request-1");
        BookingOrder paid = pendingOrder(2L, "A02", "request-2");
        paid.confirmPayment(
                PaymentReference.of(
                        "77777777-7777-7777-7777-777777777777"
                ),
                NOW.minusSeconds(300),
                NOW.minusSeconds(290)
        );
        SeatInventory inventory = SeatInventory.restore(
                TRIP,
                2,
                0,
                2L,
                NOW.minusSeconds(600),
                NOW.minusSeconds(290)
        );
        when(consumedEventStore.insertIfAbsent(
                TripCancellationBookingTransaction.CONSUMER_NAME,
                "event-1",
                NOW
        )).thenReturn(true);
        when(orderRepository.findActiveByTripReferenceForUpdate(TRIP))
                .thenReturn(List.of(pending, paid));
        when(inventoryRepository.findByTripReference(TRIP))
                .thenReturn(Optional.of(inventory));
        when(seatReservationPort.releaseSeat(any())).thenReturn(true);
        when(seatReservationPort.releaseSoldSeat(any())).thenReturn(true);
        when(inventoryRepository.save(any())).thenAnswer(
                invocation -> invocation.getArgument(0)
        );
        when(orderRepository.save(any())).thenAnswer(
                invocation -> invocation.getArgument(0)
        );
        when(progressPort.start(7001L, "event-1", 1, NOW))
                .thenReturn(false);

        TripCancellationBookingResult result = transaction.process(
                envelope()
        );

        assertThat(result.cancelledBookings()).isEqualTo(1);
        assertThat(result.refundsRequested()).isEqualTo(1);
        assertThat(pending.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(paid.status()).isEqualTo(BookingStatus.REFUND_PENDING);
        assertThat(inventory.availableSeats()).isEqualTo(2);
        verify(refundPort).requestRefund(paid, NOW);
        verify(progressPort).start(7001L, "event-1", 1, NOW);
        verifyNoInteractions(settlementOutboxPort);
    }

    @Test
    void shouldIgnoreDuplicateEvent() {
        when(consumedEventStore.insertIfAbsent(
                TripCancellationBookingTransaction.CONSUMER_NAME,
                "event-1",
                NOW
        )).thenReturn(false);

        TripCancellationBookingResult result = transaction.process(
                envelope()
        );

        assertThat(result.duplicateEvent()).isTrue();
        verify(orderRepository, never())
                .findActiveByTripReferenceForUpdate(any());
        verifyNoInteractions(
                inventoryRepository,
                seatReservationPort,
                refundPort,
                settlementOutboxPort,
                progressPort
        );
    }

    private TripCancellationRequestedEnvelope envelope() {
        return new TripCancellationRequestedEnvelope(
                "event-1",
                new TripCancellationRequestedMessage(
                        7001L,
                        2L,
                        NOW.minusSeconds(10)
                )
        );
    }

    private BookingOrder pendingOrder(
            long id,
            String seat,
            String request
    ) {
        return BookingOrder.place(
                BookingId.of(id),
                BookingNumber.of(id == 1L
                        ? "11111111-1111-1111-1111-111111111111"
                        : "22222222-2222-2222-2222-222222222222"),
                BookingRequestNumber.of(request),
                UserId.of(1000L + id),
                TRIP,
                TRIP_NUMBER,
                SeatNumber.of(seat),
                BookingAmount.of("5.50"),
                NOW.plusSeconds(300),
                NOW.minusSeconds(600)
        );
    }
}

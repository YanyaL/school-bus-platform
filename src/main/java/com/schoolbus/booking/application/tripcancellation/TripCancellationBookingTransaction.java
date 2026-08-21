package com.schoolbus.booking.application.tripcancellation;

import com.schoolbus.booking.config.ConditionalOnEmbeddedBooking;

import com.schoolbus.booking.application.booking.SeatReleaseRequest;
import com.schoolbus.booking.application.booking.TripSeatReservationPort;
import com.schoolbus.booking.domain.inventory.SeatInventory;
import com.schoolbus.booking.domain.inventory.SeatInventoryRepository;
import com.schoolbus.booking.domain.order.BookingOrder;
import com.schoolbus.booking.domain.order.BookingOrderRepository;
import com.schoolbus.booking.domain.order.BookingStatus;
import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.shared.application.messaging.ConsumedEventStore;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@ConditionalOnEmbeddedBooking
@Service
@Profile("!test")
public class TripCancellationBookingTransaction {

    public static final String CONSUMER_NAME =
            "booking-trip-cancellation-requested-consumer";

    private final BookingOrderRepository orderRepository;
    private final SeatInventoryRepository inventoryRepository;
    private final TripSeatReservationPort seatReservationPort;
    private final TripCancellationRefundPort refundPort;
    private final TripCancellationSettlementOutboxPort settlementOutboxPort;
    private final TripCancellationProgressPort progressPort;
    private final ConsumedEventStore consumedEventStore;
    private final Clock clock;

    public TripCancellationBookingTransaction(
            BookingOrderRepository orderRepository,
            SeatInventoryRepository inventoryRepository,
            TripSeatReservationPort seatReservationPort,
            TripCancellationRefundPort refundPort,
            TripCancellationSettlementOutboxPort settlementOutboxPort,
            TripCancellationProgressPort progressPort,
            ConsumedEventStore consumedEventStore,
            Clock clock
    ) {
        this.orderRepository = Objects.requireNonNull(orderRepository);
        this.inventoryRepository = Objects.requireNonNull(inventoryRepository);
        this.seatReservationPort = Objects.requireNonNull(seatReservationPort);
        this.refundPort = Objects.requireNonNull(refundPort);
        this.settlementOutboxPort = Objects.requireNonNull(settlementOutboxPort);
        this.progressPort = Objects.requireNonNull(progressPort);
        this.consumedEventStore = Objects.requireNonNull(consumedEventStore);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TripCancellationBookingResult process(
            TripCancellationRequestedEnvelope envelope
    ) {
        TripCancellationRequestedEnvelope checked = Objects.requireNonNull(
                envelope,
                "envelope must not be null"
        );
        long tripId = checked.payload().tripId();
        Instant now = clock.instant();
        if (!consumedEventStore.insertIfAbsent(
                CONSUMER_NAME,
                checked.eventId(),
                now
        )) {
            return TripCancellationBookingResult.duplicate(tripId);
        }

        TripReference tripReference = TripReference.of(tripId);
        List<BookingOrder> activeOrders = orderRepository
                .findActiveByTripReferenceForUpdate(tripReference);
        SeatInventory inventory = activeOrders.isEmpty()
                ? null
                : inventoryRepository.findByTripReference(tripReference)
                    .orElseThrow(() -> new IllegalStateException(
                            "seat inventory was not found for trip " + tripId
                    ));

        int cancelled = 0;
        int refunds = 0;
        for (BookingOrder order : activeOrders) {
            SeatReleaseRequest releaseRequest = new SeatReleaseRequest(
                    order.tripReference(),
                    order.seatNumber(),
                    order.bookingNumber(),
                    now
            );
            if (order.status() == BookingStatus.PENDING_PAYMENT) {
                order.cancelBecauseTripWasCancelled(now);
                ensureReleased(
                        seatReservationPort.releaseSeat(releaseRequest),
                        order
                );
                cancelled++;
            } else if (order.status() == BookingStatus.PAID) {
                order.requestRefundBecauseTripWasCancelled(now);
                ensureReleased(
                        seatReservationPort.releaseSoldSeat(releaseRequest),
                        order
                );
                refundPort.requestRefund(order, now);
                refunds++;
            } else {
                continue;
            }

            inventory.release(now);
            inventoryRepository.save(inventory);
            orderRepository.save(order);
        }

        boolean settled = progressPort.start(
                tripId,
                checked.eventId(),
                refunds,
                now
        );
        if (settled) {
            settlementOutboxPort.append(
                    new TripCancellationBookingsSettledEvent(
                            tripId,
                            now
                    )
            );
        }
        return new TripCancellationBookingResult(
                tripId,
                cancelled,
                refunds,
                false
        );
    }

    private void ensureReleased(boolean released, BookingOrder order) {
        if (!released) {
            throw new OptimisticLockingFailureException(
                    "seat state changed while cancelling booking "
                            + order.bookingNumber()
            );
        }
    }
}

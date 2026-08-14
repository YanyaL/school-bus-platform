package com.schoolbus.transport.application.trip;

import com.schoolbus.shared.application.messaging.ConsumedEventStore;
import com.schoolbus.transport.domain.route.RouteId;
import com.schoolbus.transport.domain.trip.BusTrip;
import com.schoolbus.transport.domain.trip.BusTripRepository;
import com.schoolbus.transport.domain.trip.Money;
import com.schoolbus.transport.domain.trip.TripId;
import com.schoolbus.transport.domain.trip.TripNumber;
import com.schoolbus.transport.domain.trip.TripStatus;
import com.schoolbus.transport.domain.vehicle.VehicleId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripCancellationCompletionTransactionTest {

    private static final Instant NOW =
            Instant.parse("2026-08-14T10:00:00Z");

    private BusTripRepository repository;
    private ConsumedEventStore consumedEventStore;
    private ApplicationEventPublisher eventPublisher;
    private TripCancellationCompletionTransaction transaction;

    @BeforeEach
    void setUp() {
        repository = mock(BusTripRepository.class);
        consumedEventStore = mock(ConsumedEventStore.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        transaction = new TripCancellationCompletionTransaction(
                repository,
                consumedEventStore,
                eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldFinalizeCancellationOnce() {
        BusTrip trip = cancellationPendingTrip();
        when(consumedEventStore.insertIfAbsent(
                TripCancellationCompletionTransaction.CONSUMER_NAME,
                "event-2",
                NOW
        )).thenReturn(true);
        when(repository.findByIdForUpdate(TripId.of(5001L)))
                .thenReturn(Optional.of(trip));
        when(repository.save(trip)).thenReturn(trip);

        boolean changed = transaction.complete(envelope());

        assertThat(changed).isTrue();
        assertThat(trip.status()).isEqualTo(TripStatus.CANCELLED);
        verify(eventPublisher).publishEvent(
                new TripAvailabilityChangedEvent(NOW)
        );
    }

    @Test
    void shouldIgnoreDuplicateSettledEvent() {
        when(consumedEventStore.insertIfAbsent(
                TripCancellationCompletionTransaction.CONSUMER_NAME,
                "event-2",
                NOW
        )).thenReturn(false);

        assertThat(transaction.complete(envelope())).isFalse();
        verify(repository, never()).findByIdForUpdate(TripId.of(5001L));
    }

    private TripCancellationSettledEnvelope envelope() {
        return new TripCancellationSettledEnvelope(
                "event-2",
                new TripCancellationSettledMessage(
                        5001L,
                        NOW.minusSeconds(1)
                )
        );
    }

    private BusTrip cancellationPendingTrip() {
        BusTrip trip = BusTrip.draft(
                TripId.of(5001L),
                TripNumber.of(
                        "33333333-3333-3333-3333-333333333333"
                ),
                VehicleId.of(3001L),
                RouteId.of(2001L),
                NOW.plusSeconds(3600),
                NOW.plusSeconds(1800),
                Money.of("5.50"),
                NOW.minusSeconds(3600)
        );
        trip.openForBooking(NOW.minusSeconds(1800));
        trip.requestCancellation(NOW.minusSeconds(60));
        return trip;
    }
}

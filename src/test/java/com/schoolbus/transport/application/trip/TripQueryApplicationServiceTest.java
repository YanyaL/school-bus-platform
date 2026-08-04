package com.schoolbus.transport.application.trip;

import com.schoolbus.transport.domain.trip.BusTrip;
import com.schoolbus.transport.domain.trip.BusTripRepository;
import com.schoolbus.transport.domain.trip.Money;
import com.schoolbus.transport.domain.trip.RouteId;
import com.schoolbus.transport.domain.trip.TripId;
import com.schoolbus.transport.domain.trip.TripNumber;
import com.schoolbus.transport.domain.trip.VehicleId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripQueryApplicationServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-08-04T08:00:00Z");

    @Mock
    private BusTripRepository busTripRepository;

    @Mock
    private BookableTripCache bookableTripCache;

    private TripQueryApplicationService service;

    @BeforeEach
    void setUp() {
        service = new TripQueryApplicationService(
                busTripRepository,
                bookableTripCache,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldReturnRedisListWithoutQueryingMySqlOnCacheHit() {
        List<BookableTripView> cached = List.of(
                BookableTripView.from(openTrip(1001L)),
                BookableTripView.from(openTrip(1002L))
        );
        when(bookableTripCache.findAll())
                .thenReturn(Optional.of(cached));

        List<BookableTripView> result =
                service.findBookableTrips(1);

        assertThat(result).containsExactly(cached.getFirst());
        verify(busTripRepository, never())
                .findBookableTrips(NOW, 100);
    }

    @Test
    void shouldQueryMySqlAndPopulateRedisListOnCacheMiss() {
        BusTrip trip = openTrip(1001L);
        when(bookableTripCache.findAll())
                .thenReturn(Optional.empty());
        when(busTripRepository.findBookableTrips(
                NOW,
                TripQueryApplicationService.MAX_CACHE_SIZE
        )).thenReturn(List.of(trip));

        List<BookableTripView> result =
                service.findBookableTrips(10);

        List<BookableTripView> expected = List.of(
                BookableTripView.from(trip)
        );
        assertThat(result).isEqualTo(expected);
        verify(bookableTripCache).replaceAll(expected);
    }

    @Test
    void shouldRejectLimitOutsideCachedWindow() {
        assertThatThrownBy(() -> service.findBookableTrips(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findBookableTrips(101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldFallBackToMySqlWhenRedisIsUnavailable() {
        BusTrip trip = openTrip(1001L);
        when(bookableTripCache.findAll()).thenThrow(
                new IllegalStateException("redis unavailable")
        );
        when(busTripRepository.findBookableTrips(
                NOW,
                TripQueryApplicationService.MAX_CACHE_SIZE
        )).thenReturn(List.of(trip));

        List<BookableTripView> result =
                service.findBookableTrips(10);

        assertThat(result).containsExactly(
                BookableTripView.from(trip)
        );
    }

    private BusTrip openTrip(long id) {
        BusTrip trip = BusTrip.draft(
                TripId.of(id),
                TripNumber.generate(),
                VehicleId.of(3001L),
                RouteId.of(2001L),
                NOW.plusSeconds(7200),
                NOW.plusSeconds(3600),
                Money.of("5.00"),
                NOW.minusSeconds(3600)
        );
        trip.openForBooking(NOW.minusSeconds(1800));
        return trip;
    }
}

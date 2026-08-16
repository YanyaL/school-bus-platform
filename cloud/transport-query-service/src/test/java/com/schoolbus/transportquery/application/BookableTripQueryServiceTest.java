package com.schoolbus.transportquery.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookableTripQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");

    @Mock
    private TripQueryRepository tripQueryRepository;

    @Mock
    private BookableTripCache bookableTripCache;

    private BookableTripQueryService service;

    @BeforeEach
    void setUp() {
        service = new BookableTripQueryService(
                tripQueryRepository,
                bookableTripCache,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldReturnCachedTripsOnHit() {
        when(bookableTripCache.findAll()).thenReturn(Optional.of(List.of(trip("A"), trip("B"))));

        List<BookableTripView> result = service.findBookableTrips(1);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().tripNumber()).isEqualTo("A");
        verify(tripQueryRepository, never()).findBookableTrips(any(), anyInt());
    }

    @Test
    void shouldLoadMysqlAndPopulateCacheOnMiss() {
        when(bookableTripCache.findAll()).thenReturn(Optional.empty());
        when(tripQueryRepository.findBookableTrips(NOW, 100))
                .thenReturn(List.of(trip("A")));

        List<BookableTripView> result = service.findBookableTrips(20);

        assertThat(result).hasSize(1);
        verify(bookableTripCache).replaceAll(List.of(trip("A")));
    }

    @Test
    void shouldFallbackToMysqlWhenCacheReadFails() {
        when(bookableTripCache.findAll()).thenThrow(new IllegalStateException("redis down"));
        when(tripQueryRepository.findBookableTrips(NOW, 100))
                .thenReturn(List.of(trip("A")));

        assertThat(service.findBookableTrips(20)).hasSize(1);
    }

    @Test
    void shouldStillReturnMysqlWhenCacheWriteFails() {
        when(bookableTripCache.findAll()).thenReturn(Optional.empty());
        when(tripQueryRepository.findBookableTrips(NOW, 100))
                .thenReturn(List.of(trip("A")));
        doThrow(new IllegalStateException("write failed"))
                .when(bookableTripCache).replaceAll(any());

        assertThat(service.findBookableTrips(20)).hasSize(1);
    }

    @Test
    void shouldCacheEmptyListMarkerPathViaReplaceAll() {
        when(bookableTripCache.findAll()).thenReturn(Optional.of(List.of()));

        assertThat(service.findBookableTrips(20)).isEmpty();
        verify(tripQueryRepository, never()).findBookableTrips(eq(NOW), anyInt());
    }

    private static BookableTripView trip(String number) {
        return new BookableTripView(
                1L,
                number,
                2L,
                3L,
                NOW.plusSeconds(3600),
                NOW.plusSeconds(1800),
                new BigDecimal("5.00")
        );
    }
}

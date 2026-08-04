package com.schoolbus.transport.infrastructure.cache;

import com.schoolbus.transport.application.trip.BookableTripCache;
import com.schoolbus.transport.application.trip.TripAvailabilityChangedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookableTripCacheInvalidationListenerTest {

    @Mock
    private BookableTripCache bookableTripCache;

    @Test
    void shouldEvictBookableTripListAfterCommittedChange() {
        BookableTripCacheInvalidationListener listener =
                new BookableTripCacheInvalidationListener(
                        bookableTripCache
                );

        listener.onTripAvailabilityChanged(
                new TripAvailabilityChangedEvent(
                        Instant.parse("2026-08-04T08:00:00Z")
                )
        );

        verify(bookableTripCache).evict();
    }
}

package com.schoolbus.transport.infrastructure.cache;

import com.schoolbus.transport.application.trip.BookableTripCache;
import com.schoolbus.transport.application.trip.TripAvailabilityChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Objects;

@Component
@Profile("!test")
public class BookableTripCacheInvalidationListener {

    private static final Logger log = LoggerFactory.getLogger(
            BookableTripCacheInvalidationListener.class
    );

    private final BookableTripCache bookableTripCache;

    public BookableTripCacheInvalidationListener(
            BookableTripCache bookableTripCache
    ) {
        this.bookableTripCache = Objects.requireNonNull(
                bookableTripCache,
                "bookableTripCache must not be null"
        );
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void onTripAvailabilityChanged(
            TripAvailabilityChangedEvent event
    ) {
        Objects.requireNonNull(event, "event must not be null");
        try {
            bookableTripCache.evict();
        } catch (RuntimeException exception) {
            log.warn(
                    "Unable to evict bookable trip cache after "
                            + "committed status change",
                    exception
            );
        }
    }
}

package com.schoolbus.transport.application.trip;

import com.schoolbus.transport.domain.trip.BusTripRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Profile("!test")
public class TripQueryApplicationService {

    static final int MAX_CACHE_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(
            TripQueryApplicationService.class
    );

    private final BusTripRepository busTripRepository;
    private final BookableTripCache bookableTripCache;
    private final Clock clock;

    public TripQueryApplicationService(
            BusTripRepository busTripRepository,
            BookableTripCache bookableTripCache,
            Clock clock
    ) {
        this.busTripRepository = Objects.requireNonNull(
                busTripRepository,
                "busTripRepository must not be null"
        );
        this.bookableTripCache = Objects.requireNonNull(
                bookableTripCache,
                "bookableTripCache must not be null"
        );
        this.clock = Objects.requireNonNull(
                clock,
                "clock must not be null"
        );
    }

    @Transactional(readOnly = true)
    public List<BookableTripView> findBookableTrips(int limit) {
        int validatedLimit = validateLimit(limit);
        List<BookableTripView> cachedTrips = findCachedTrips()
                .orElseGet(this::loadAndCacheBookableTrips);
        return cachedTrips.stream()
                .limit(validatedLimit)
                .toList();
    }

    private List<BookableTripView> loadAndCacheBookableTrips() {
        List<BookableTripView> trips = busTripRepository
                .findBookableTrips(
                        clock.instant(),
                        MAX_CACHE_SIZE
                )
                .stream()
                .map(BookableTripView::from)
                .toList();
        try {
            bookableTripCache.replaceAll(trips);
        } catch (RuntimeException exception) {
            log.warn(
                    "Unable to populate bookable trip cache; "
                            + "returning MySQL result",
                    exception
            );
        }
        return trips;
    }

    private Optional<List<BookableTripView>> findCachedTrips() {
        try {
            return bookableTripCache.findAll();
        } catch (RuntimeException exception) {
            log.warn(
                    "Unable to read bookable trip cache; "
                            + "falling back to MySQL",
                    exception
            );
            return Optional.empty();
        }
    }

    private int validateLimit(int limit) {
        if (limit <= 0 || limit > MAX_CACHE_SIZE) {
            throw new IllegalArgumentException(
                    "limit must be between 1 and "
                            + MAX_CACHE_SIZE
            );
        }
        return limit;
    }
}

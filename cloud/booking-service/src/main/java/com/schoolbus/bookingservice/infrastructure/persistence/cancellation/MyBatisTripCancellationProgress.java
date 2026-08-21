package com.schoolbus.bookingservice.infrastructure.persistence.cancellation;

import com.schoolbus.bookingservice.application.tripcancellation.TripCancellationProgressPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Repository
@Profile("!test")
public class MyBatisTripCancellationProgress
        implements TripCancellationProgressPort {

    private static final ZoneOffset DATABASE_ZONE = ZoneOffset.UTC;
    private static final String PROCESSING = "PROCESSING";
    private static final String SETTLED = "SETTLED";

    private final TripCancellationProgressMapper mapper;

    public MyBatisTripCancellationProgress(
            TripCancellationProgressMapper mapper
    ) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public boolean start(
            long tripId,
            String requestEventId,
            int pendingRefunds,
            Instant startedAt
    ) {
        if (tripId <= 0L) {
            throw new IllegalArgumentException("tripId must be positive");
        }
        if (requestEventId == null || requestEventId.isBlank()) {
            throw new IllegalArgumentException(
                    "requestEventId must not be blank"
            );
        }
        if (pendingRefunds < 0) {
            throw new IllegalArgumentException(
                    "pendingRefunds must not be negative"
            );
        }
        int inserted = mapper.insertProgress(
                tripId,
                requestEventId.strip(),
                pendingRefunds,
                pendingRefunds == 0 ? SETTLED : PROCESSING,
                databaseTime(startedAt)
        );
        if (inserted != 1) {
            throw new IllegalStateException(
                    "failed to initialize trip cancellation progress"
            );
        }
        return pendingRefunds == 0;
    }

    @Override
    public boolean completeRefund(long tripId, Instant completedAt) {
        int updated = mapper.decrementPendingRefund(
                tripId,
                databaseTime(completedAt)
        );
        if (updated == 0) {
            String status = mapper.selectStatus(tripId);
            if (SETTLED.equals(status)) {
                return false;
            }
            throw new IllegalStateException(
                    "trip cancellation progress cannot complete refund for trip "
                            + tripId
            );
        }
        if (updated != 1) {
            throw new IllegalStateException(
                    "unexpected trip cancellation progress update count: "
                            + updated
            );
        }
        return SETTLED.equals(mapper.selectStatus(tripId));
    }

    private LocalDateTime databaseTime(Instant instant) {
        return LocalDateTime.ofInstant(
                Objects.requireNonNull(instant, "instant must not be null"),
                DATABASE_ZONE
        );
    }
}

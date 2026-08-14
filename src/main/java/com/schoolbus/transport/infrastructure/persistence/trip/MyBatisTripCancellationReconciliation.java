package com.schoolbus.transport.infrastructure.persistence.trip;

import com.schoolbus.transport.application.trip.TripCancellationReconciliationPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

@Repository
@Profile("!test")
public class MyBatisTripCancellationReconciliation
        implements TripCancellationReconciliationPort {

    private final TripCancellationReconciliationMapper mapper;

    public MyBatisTripCancellationReconciliation(
            TripCancellationReconciliationMapper mapper
    ) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public List<Long> findSettledCancellationsAwaitingFinalization(
            Instant settledBefore,
            int limit
    ) {
        Objects.requireNonNull(settledBefore, "settledBefore must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return mapper.selectSettledAwaitingFinalization(
                LocalDateTime.ofInstant(settledBefore, ZoneOffset.UTC),
                limit
        );
    }
}

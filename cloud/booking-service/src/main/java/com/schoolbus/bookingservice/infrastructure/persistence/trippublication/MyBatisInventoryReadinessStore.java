package com.schoolbus.bookingservice.infrastructure.persistence.trippublication;

import com.schoolbus.bookingservice.application.trippublication.InventoryReadinessCandidate;
import com.schoolbus.bookingservice.application.trippublication.InventoryReadinessObservation;
import com.schoolbus.bookingservice.application.trippublication.InventoryReadinessStore;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

public class MyBatisInventoryReadinessStore
        implements InventoryReadinessStore {
    private final InventoryReadinessMapper mapper;

    public MyBatisInventoryReadinessStore(InventoryReadinessMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public List<InventoryReadinessCandidate> findCandidates(int limit) {
        return List.copyOf(mapper.findCandidates(limit));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Integer findInventoryTotal(long tripId) {
        return mapper.findInventoryTotal(tripId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<String> findSeatNumbers(long tripId) {
        return List.copyOf(mapper.findSeatNumbers(tripId));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveObservation(InventoryReadinessObservation observation) {
        Instant readyAt = observation.status()
                == InventoryReadinessObservation.Status.READY
                ? observation.checkedAt()
                : null;
        mapper.saveObservation(
                observation.tripId(),
                observation.tripNumber(),
                observation.publicationVersion(),
                observation.expectedTotalSeats(),
                observation.observedInventoryTotal(),
                observation.observedSeatCount(),
                observation.status().name(),
                observation.diagnosticCode(),
                time(observation.checkedAt()),
                readyAt == null ? null : time(readyAt)
        );
    }

    private static LocalDateTime time(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}

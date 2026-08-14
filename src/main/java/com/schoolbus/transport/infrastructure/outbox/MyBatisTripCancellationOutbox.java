package com.schoolbus.transport.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.booking.application.tripcancellation.TripCancellationBookingsSettledEvent;
import com.schoolbus.booking.application.tripcancellation.TripCancellationSettlementOutboxPort;
import com.schoolbus.shared.web.TraceContext;
import com.schoolbus.transport.application.trip.TripCancellationOutboxPort;
import com.schoolbus.transport.application.trip.TripCancellationRequestedEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Repository
@Profile("!test")
public class MyBatisTripCancellationOutbox
        implements TripCancellationOutboxPort,
        TripCancellationSettlementOutboxPort {

    private static final ZoneOffset DATABASE_ZONE = ZoneOffset.UTC;

    private final TripCancellationOutboxMapper mapper;
    private final ObjectMapper objectMapper;

    public MyBatisTripCancellationOutbox(
            TripCancellationOutboxMapper mapper,
            ObjectMapper objectMapper
    ) {
        this.mapper = Objects.requireNonNull(mapper);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public void append(TripCancellationRequestedEvent event) {
        TripCancellationRequestedEvent checked = Objects.requireNonNull(event);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tripId", checked.tripId());
        payload.put("tripVersion", checked.tripVersion());
        payload.put("requestedAt", checked.requestedAt());
        insert(
                "transport",
                "BusTrip",
                Long.toString(checked.tripId()),
                checked.tripVersion(),
                TripCancellationRequestedEvent.TYPE,
                payload,
                checked.requestedAt()
        );
    }

    @Override
    public void append(TripCancellationBookingsSettledEvent event) {
        TripCancellationBookingsSettledEvent checked = Objects.requireNonNull(event);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tripId", checked.tripId());
        payload.put("settledAt", checked.settledAt());
        insert(
                "booking",
                "TripCancellation",
                Long.toString(checked.tripId()),
                0L,
                TripCancellationBookingsSettledEvent.TYPE,
                payload,
                checked.settledAt()
        );
    }

    private void insert(
            String contextName,
            String aggregateType,
            String aggregateId,
            long aggregateVersion,
            String eventType,
            Map<String, Object> payload,
            Instant occurredAt
    ) {
        LocalDateTime databaseTime = LocalDateTime.ofInstant(
                occurredAt,
                DATABASE_ZONE
        );
        int inserted = mapper.insertEvent(
                UUID.randomUUID().toString(),
                contextName,
                aggregateType,
                aggregateId,
                aggregateVersion,
                eventType,
                serialize(payload),
                TraceContext.currentTraceId(),
                databaseTime,
                databaseTime
        );
        if (inserted != 1) {
            throw new IllegalStateException(
                    "failed to insert trip cancellation outbox event"
            );
        }
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "failed to serialize trip cancellation event",
                    exception
            );
        }
    }
}

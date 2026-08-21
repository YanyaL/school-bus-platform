package com.schoolbus.bookingservice.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.bookingservice.application.tripcancellation.TripCancellationBookingsSettledEvent;
import com.schoolbus.bookingservice.application.tripcancellation.TripCancellationSettlementOutboxPort;
import com.schoolbus.bookingservice.support.payment.infrastructure.outbox.OutboxMapper;
import com.schoolbus.bookingservice.shared.web.TraceContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Repository
@Profile("!test")
public class MyBatisTripCancellationSettlementOutbox
        implements TripCancellationSettlementOutboxPort {

    private static final ZoneOffset DATABASE_ZONE = ZoneOffset.UTC;

    private final OutboxMapper mapper;
    private final ObjectMapper objectMapper;

    public MyBatisTripCancellationSettlementOutbox(
            OutboxMapper mapper,
            ObjectMapper objectMapper
    ) {
        this.mapper = Objects.requireNonNull(mapper);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public void append(TripCancellationBookingsSettledEvent event) {
        TripCancellationBookingsSettledEvent checked =
                Objects.requireNonNull(event);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tripId", checked.tripId());
        payload.put("settledAt", checked.settledAt());
        LocalDateTime databaseTime = LocalDateTime.ofInstant(
                checked.settledAt(),
                DATABASE_ZONE
        );
        int inserted = mapper.insertEvent(
                UUID.randomUUID().toString(),
                "booking",
                "TripCancellation",
                Long.toString(checked.tripId()),
                0L,
                TripCancellationBookingsSettledEvent.TYPE,
                serialize(payload),
                TraceContext.currentTraceId(),
                databaseTime,
                databaseTime
        );
        if (inserted != 1) {
            throw new IllegalStateException(
                    "failed to insert trip cancellation settlement outbox event"
            );
        }
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "failed to serialize trip cancellation settled event",
                    exception
            );
        }
    }
}

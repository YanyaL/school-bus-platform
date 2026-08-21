package com.schoolbus.bookingservice.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.bookingservice.application.booking.BookingExpirationOutboxPort;
import com.schoolbus.bookingservice.application.booking.BookingPaymentDeadlineEvent;
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
public class MyBatisBookingExpirationOutbox
        implements BookingExpirationOutboxPort {

    private static final ZoneOffset DATABASE_ZONE = ZoneOffset.UTC;

    private final BookingExpirationOutboxMapper mapper;
    private final ObjectMapper objectMapper;

    public MyBatisBookingExpirationOutbox(
            BookingExpirationOutboxMapper mapper,
            ObjectMapper objectMapper
    ) {
        this.mapper = Objects.requireNonNull(
                mapper,
                "mapper must not be null"
        );
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper must not be null"
        );
    }

    @Override
    public void append(BookingPaymentDeadlineEvent event) {
        BookingPaymentDeadlineEvent validated = Objects.requireNonNull(
                event,
                "event must not be null"
        );
        LocalDateTime occurredAt = LocalDateTime.ofInstant(
                validated.occurredAt(),
                DATABASE_ZONE
        );
        int inserted = mapper.insertEvent(
                UUID.randomUUID().toString(),
                Long.toString(validated.bookingId().value()),
                validated.aggregateVersion(),
                BookingPaymentDeadlineEvent.TYPE,
                serialize(validated),
                TraceContext.currentTraceId(),
                occurredAt,
                occurredAt
        );
        if (inserted != 1) {
            throw new IllegalStateException(
                    "failed to insert booking expiration outbox event"
            );
        }
    }

    private String serialize(BookingPaymentDeadlineEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bookingId", event.bookingId().value());
        payload.put("bookingNumber", event.bookingNumber().toString());
        payload.put("expiresAt", event.expiresAt());
        payload.put("occurredAt", event.occurredAt());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "failed to serialize booking expiration event",
                    exception
            );
        }
    }
}

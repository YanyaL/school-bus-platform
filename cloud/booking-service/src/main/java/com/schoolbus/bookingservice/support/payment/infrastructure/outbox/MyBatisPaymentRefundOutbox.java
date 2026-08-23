package com.schoolbus.bookingservice.support.payment.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.bookingservice.shared.web.TraceContext;
import com.schoolbus.bookingservice.support.payment.application.PaymentRefundOutboxPort;
import com.schoolbus.bookingservice.support.payment.application.RefundRequiredEvent;
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
public class MyBatisPaymentRefundOutbox
        implements PaymentRefundOutboxPort {

    public static final String CONTEXT_NAME = "booking";
    public static final String AGGREGATE_TYPE = "BookingOrder";
    public static final String EVENT_TYPE = "RefundRequested";

    private static final ZoneOffset DATABASE_ZONE = ZoneOffset.UTC;

    private final OutboxMapper mapper;
    private final ObjectMapper objectMapper;

    public MyBatisPaymentRefundOutbox(
            OutboxMapper mapper,
            ObjectMapper objectMapper
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper must not be null"
        );
    }

    @Override
    public void append(RefundRequiredEvent event) {
        RefundRequiredEvent validated = Objects.requireNonNull(
                event,
                "event must not be null"
        );
        LocalDateTime occurredAt = LocalDateTime.ofInstant(
                validated.occurredAt(),
                DATABASE_ZONE
        );
        int inserted = mapper.insertEvent(
                UUID.randomUUID().toString(),
                CONTEXT_NAME,
                AGGREGATE_TYPE,
                validated.bookingNumber().toString(),
                0L,
                EVENT_TYPE,
                serialize(validated),
                TraceContext.currentTraceId(),
                occurredAt,
                occurredAt
        );
        if (inserted != 1) {
            throw new IllegalStateException(
                    "failed to insert RefundRequested outbox event"
            );
        }
    }

    private String serialize(RefundRequiredEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paymentNumber", event.paymentNumber().toString());
        payload.put("bookingNumber", event.bookingNumber().toString());
        payload.put("amount", event.amount().amount());
        payload.put("reason", event.reason());
        payload.put("paidAt", event.paidAt());
        payload.put("occurredAt", event.occurredAt());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "failed to serialize RefundRequested event",
                    exception
            );
        }
    }
}

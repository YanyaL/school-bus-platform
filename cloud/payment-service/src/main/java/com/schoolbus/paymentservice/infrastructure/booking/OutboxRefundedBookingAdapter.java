package com.schoolbus.paymentservice.infrastructure.booking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.paymentservice.application.refund.PaymentRefundedCommand;
import com.schoolbus.paymentservice.application.refund.RefundedBookingPort;
import com.schoolbus.paymentservice.infrastructure.outbox.OutboxMapper;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
@ConditionalOnProperty(
        prefix = "school-bus.payment.migration",
        name = "booking-write-mode",
        havingValue = "EVENT",
        matchIfMissing = true
)
public class OutboxRefundedBookingAdapter implements RefundedBookingPort {

    public static final String CONTEXT_NAME = "payment";
    public static final String AGGREGATE_TYPE = "PaymentRecord";
    public static final String EVENT_TYPE = "PaymentRefunded";

    private static final ZoneOffset DATABASE_ZONE = ZoneOffset.UTC;

    private final OutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    public OutboxRefundedBookingAdapter(
            OutboxMapper outboxMapper,
            ObjectMapper objectMapper
    ) {
        this.outboxMapper = Objects.requireNonNull(outboxMapper);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public void markRefunded(PaymentRefundedCommand command) {
        PaymentRefundedCommand checked = Objects.requireNonNull(command);
        LocalDateTime occurredAt = LocalDateTime.ofInstant(
                checked.occurredAt(),
                DATABASE_ZONE
        );
        int inserted = outboxMapper.insertEvent(
                UUID.randomUUID().toString(),
                CONTEXT_NAME,
                AGGREGATE_TYPE,
                checked.paymentNumber(),
                0L,
                EVENT_TYPE,
                serialize(checked),
                MDC.get("traceId"),
                occurredAt,
                occurredAt
        );
        if (inserted != 1) {
            throw new IllegalStateException(
                    "failed to append PaymentRefunded outbox event"
            );
        }
    }

    private String serialize(PaymentRefundedCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paymentNumber", command.paymentNumber());
        payload.put("bookingNumber", command.bookingNumber().toString());
        payload.put("refundReference", command.refundReference());
        payload.put("reason", command.reason());
        payload.put("refundedAt", command.refundedAt());
        payload.put("occurredAt", command.occurredAt());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "failed to serialize PaymentRefunded event",
                    exception
            );
        }
    }
}

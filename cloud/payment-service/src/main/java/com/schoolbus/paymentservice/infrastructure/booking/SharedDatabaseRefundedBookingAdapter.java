package com.schoolbus.paymentservice.infrastructure.booking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.paymentservice.application.refund.RefundedBookingPort;
import com.schoolbus.paymentservice.domain.BookingNumber;
import com.schoolbus.paymentservice.infrastructure.persistence.RefundBookingMapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class SharedDatabaseRefundedBookingAdapter
        implements RefundedBookingPort {

    private static final ZoneOffset DATABASE_ZONE = ZoneOffset.UTC;
    private static final String REFUND_PENDING = "REFUND_PENDING";
    private static final String REFUNDED = "REFUNDED";
    private static final String CANCELLED = "CANCELLED";
    private static final String SETTLED = "SETTLED";
    private static final String SETTLED_EVENT =
            "TripCancellationBookingsSettled";

    private final RefundBookingMapper mapper;
    private final ObjectMapper objectMapper;

    public SharedDatabaseRefundedBookingAdapter(
            RefundBookingMapper mapper,
            ObjectMapper objectMapper
    ) {
        this.mapper = Objects.requireNonNull(mapper);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public void markRefunded(
            BookingNumber bookingNumber,
            Instant refundedAt
    ) {
        BookingNumber checked = Objects.requireNonNull(bookingNumber);
        Instant checkedTime = Objects.requireNonNull(refundedAt);
        RefundBookingMapper.BookingRefundRow booking = mapper
                .selectBookingForRefund(checked.value());
        if (booking == null) {
            throw new IllegalStateException(
                    "booking was not found for refund " + checked
            );
        }
        if (REFUNDED.equals(booking.status())
                || CANCELLED.equals(booking.status())) {
            return;
        }
        if (!REFUND_PENDING.equals(booking.status())) {
            throw new IllegalStateException(
                    "booking is not waiting for refund: " + checked
            );
        }
        int updated = mapper.confirmBookingRefunded(
                booking.id(),
                toDatabaseTime(checkedTime)
        );
        if (updated != 1) {
            throw new IllegalStateException(
                    "failed to mark booking refunded: " + checked
            );
        }
        if (booking.tripId() == null) {
            return;
        }
        int progressUpdated = mapper.decrementTripCancellationPendingRefund(
                booking.tripId(),
                toDatabaseTime(checkedTime)
        );
        if (progressUpdated == 0) {
            String sagaStatus = mapper.selectTripCancellationStatus(
                    booking.tripId()
            );
            if (!SETTLED.equals(sagaStatus)) {
                return;
            }
        }
        if (progressUpdated == 1
                && SETTLED.equals(
                        mapper.selectTripCancellationStatus(booking.tripId())
                )) {
            appendTripCancellationSettledOutbox(
                    booking.tripId(),
                    checkedTime
            );
        }
    }

    private void appendTripCancellationSettledOutbox(
            long tripId,
            Instant settledAt
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tripId", tripId);
        payload.put("settledAt", settledAt);
        LocalDateTime databaseTime = toDatabaseTime(settledAt);
        int inserted = mapper.insertTripCancellationSettledOutbox(
                UUID.randomUUID().toString(),
                Long.toString(tripId),
                serialize(payload),
                MDC.get("traceId"),
                databaseTime
        );
        if (inserted != 1) {
            throw new IllegalStateException(
                    "failed to append trip cancellation settled outbox"
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

    private LocalDateTime toDatabaseTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, DATABASE_ZONE);
    }
}

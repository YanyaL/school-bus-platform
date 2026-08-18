package com.schoolbus.paymentservice.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.paymentservice.api.PaymentServiceException;
import com.schoolbus.paymentservice.infrastructure.identity.SnowflakeIdGenerator;
import com.schoolbus.paymentservice.infrastructure.persistence.BookingPaymentRow;
import com.schoolbus.paymentservice.infrastructure.persistence.PaymentConfirmationMapper;
import com.schoolbus.paymentservice.infrastructure.persistence.PaymentRecordRow;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentConfirmationTransaction {

    private static final ZoneOffset DATABASE_ZONE = ZoneOffset.UTC;
    private static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    private static final String SUCCEEDED = "SUCCEEDED";
    private static final String REFUND_PENDING = "REFUND_PENDING";
    private static final String PAYMENT_WINDOW_EXPIRED = "PAYMENT_WINDOW_EXPIRED";
    private static final String ORDER_ALREADY_FINALIZED = "ORDER_ALREADY_FINALIZED";
    private static final String SEAT_LOCK_LOST = "SEAT_LOCK_LOST";

    private final PaymentConfirmationMapper mapper;
    private final SnowflakeIdGenerator idGenerator;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public PaymentConfirmationTransaction(
            PaymentConfirmationMapper mapper,
            SnowflakeIdGenerator idGenerator,
            Clock clock,
            ObjectMapper objectMapper
    ) {
        this.mapper = Objects.requireNonNull(mapper);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.clock = Objects.requireNonNull(clock);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConfirmPaymentResult confirmOnce(ConfirmPaymentCommand command) {
        ConfirmPaymentCommand checked = Objects.requireNonNull(command);
        Optional<ConfirmPaymentResult> existing = findExisting(checked);
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }

        BookingPaymentRow booking = mapper.selectBookingForUpdate(
                checked.bookingNumber()
        );
        if (booking == null) {
            throw businessError(
                    "PAYMENT_BOOKING_NOT_FOUND",
                    "booking was not found",
                    HttpStatus.NOT_FOUND
            );
        }
        if (booking.getPriceSnapshot().compareTo(checked.amount()) != 0) {
            throw businessError(
                    "PAYMENT_AMOUNT_MISMATCH",
                    "payment amount does not match booking amount",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }

        Instant now = clock.instant();
        if (!PENDING_PAYMENT.equals(booking.getStatus())) {
            return recordRefundPending(
                    checked,
                    ORDER_ALREADY_FINALIZED,
                    now
            );
        }
        Instant expiresAt = booking.getExpiresAt().toInstant(DATABASE_ZONE);
        if (!checked.paidAt().isBefore(expiresAt)) {
            return recordRefundPending(
                    checked,
                    PAYMENT_WINDOW_EXPIRED,
                    now
            );
        }

        int soldRows = mapper.confirmSeatSold(
                booking.getTripId(),
                booking.getSeatNumber(),
                booking.getOrderNo(),
                toDatabaseTime(now)
        );
        if (soldRows != 1) {
            return recordRefundPending(checked, SEAT_LOCK_LOST, now);
        }

        long paymentId = idGenerator.nextId();
        insertPayment(paymentId, checked, SUCCEEDED, null, now);
        int updatedOrders = mapper.confirmBookingPaid(
                booking.getId(),
                checked.paymentNumber(),
                toDatabaseTime(checked.paidAt()),
                toDatabaseTime(now),
                booking.getVersion()
        );
        if (updatedOrders != 1) {
            throw businessError(
                    "PAYMENT_CONCURRENCY_CONFLICT",
                    "booking was modified by another request",
                    HttpStatus.CONFLICT
            );
        }
        return result(paymentId, checked, PaymentConfirmationOutcome.CONFIRMED);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ConfirmPaymentResult resolveDuplicate(
            ConfirmPaymentCommand command
    ) {
        return findExisting(Objects.requireNonNull(command))
                .orElseThrow(() -> businessError(
                        "PAYMENT_REQUEST_CONFLICT",
                        "payment request conflicts with an existing callback",
                        HttpStatus.CONFLICT
                ));
    }

    private ConfirmPaymentResult recordRefundPending(
            ConfirmPaymentCommand command,
            String reason,
            Instant now
    ) {
        long paymentId = idGenerator.nextId();
        insertPayment(paymentId, command, REFUND_PENDING, reason, now);
        mapper.insertRefundOutbox(
                UUID.randomUUID().toString(),
                command.paymentNumber(),
                serializeRefundEvent(command, reason, now),
                MDC.get("traceId"),
                toDatabaseTime(now)
        );
        return result(
                paymentId,
                command,
                PaymentConfirmationOutcome.REFUND_PENDING
        );
    }

    private void insertPayment(
            long paymentId,
            ConfirmPaymentCommand command,
            String status,
            String failureReason,
            Instant recordedAt
    ) {
        int rows = mapper.insertPayment(
                paymentId,
                command.paymentNumber(),
                command.requestNumber(),
                command.bookingNumber(),
                command.amount(),
                status,
                failureReason,
                toDatabaseTime(command.paidAt()),
                toDatabaseTime(recordedAt)
        );
        if (rows != 1) {
            throw new IllegalStateException("failed to insert payment record");
        }
    }

    private Optional<ConfirmPaymentResult> findExisting(
            ConfirmPaymentCommand command
    ) {
        PaymentRecordRow byRequest = mapper.selectByRequestNo(
                command.requestNumber()
        );
        if (byRequest != null) {
            ensureSameCallback(byRequest, command, true);
            return Optional.of(toResult(byRequest));
        }
        PaymentRecordRow byPayment = mapper.selectByPaymentNo(
                command.paymentNumber()
        );
        if (byPayment != null) {
            ensureSameCallback(byPayment, command, false);
            return Optional.of(toResult(byPayment));
        }
        return Optional.empty();
    }

    private void ensureSameCallback(
            PaymentRecordRow existing,
            ConfirmPaymentCommand command,
            boolean requireSamePaymentNumber
    ) {
        boolean same = existing.getOrderNo().equals(command.bookingNumber())
                && existing.getAmount().compareTo(command.amount()) == 0
                && existing.getCompletedAt().toInstant(DATABASE_ZONE)
                        .equals(command.paidAt())
                && (!requireSamePaymentNumber
                    || existing.getPaymentNo().equals(command.paymentNumber()));
        if (!same) {
            throw businessError(
                    "PAYMENT_REQUEST_CONFLICT",
                    "payment request conflicts with an existing callback",
                    HttpStatus.CONFLICT
            );
        }
    }

    private ConfirmPaymentResult toResult(PaymentRecordRow row) {
        return new ConfirmPaymentResult(
                row.getId(),
                row.getPaymentNo(),
                row.getOrderNo(),
                row.getAmount(),
                SUCCEEDED.equals(row.getStatus())
                        ? PaymentConfirmationOutcome.CONFIRMED
                        : PaymentConfirmationOutcome.REFUND_PENDING,
                row.getCompletedAt().toInstant(DATABASE_ZONE)
        );
    }

    private ConfirmPaymentResult result(
            long paymentId,
            ConfirmPaymentCommand command,
            PaymentConfirmationOutcome outcome
    ) {
        return new ConfirmPaymentResult(
                paymentId,
                command.paymentNumber(),
                command.bookingNumber(),
                command.amount(),
                outcome,
                command.paidAt()
        );
    }

    private String serializeRefundEvent(
            ConfirmPaymentCommand command,
            String reason,
            Instant occurredAt
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paymentNumber", command.paymentNumber());
        payload.put("bookingNumber", command.bookingNumber());
        payload.put("amount", command.amount());
        payload.put("reason", reason);
        payload.put("paidAt", command.paidAt());
        payload.put("occurredAt", occurredAt);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "failed to serialize payment refund event",
                    exception
            );
        }
    }

    private LocalDateTime toDatabaseTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, DATABASE_ZONE);
    }

    private PaymentServiceException businessError(
            String code,
            String message,
            HttpStatus status
    ) {
        return new PaymentServiceException(code, message, status);
    }
}

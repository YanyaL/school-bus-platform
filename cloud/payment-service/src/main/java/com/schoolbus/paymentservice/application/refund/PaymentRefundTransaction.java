package com.schoolbus.paymentservice.application.refund;

import com.schoolbus.paymentservice.domain.PaymentNumber;
import com.schoolbus.paymentservice.domain.PaymentRecord;
import com.schoolbus.paymentservice.domain.PaymentRecordRepository;
import com.schoolbus.paymentservice.domain.PaymentStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@Service
public class PaymentRefundTransaction {

    public static final String CONSUMER_NAME = "payment-refund-consumer";

    private final PaymentRecordRepository paymentRecordRepository;
    private final ConsumedEventRepository consumedEventRepository;
    private final RefundedBookingPort refundedBookingPort;
    private final Clock clock;

    public PaymentRefundTransaction(
            PaymentRecordRepository paymentRecordRepository,
            ConsumedEventRepository consumedEventRepository,
            RefundedBookingPort refundedBookingPort,
            Clock clock
    ) {
        this.paymentRecordRepository = Objects.requireNonNull(
                paymentRecordRepository,
                "paymentRecordRepository must not be null"
        );
        this.consumedEventRepository = Objects.requireNonNull(
                consumedEventRepository,
                "consumedEventRepository must not be null"
        );
        this.refundedBookingPort = Objects.requireNonNull(
                refundedBookingPort,
                "refundedBookingPort must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional(readOnly = true)
    public boolean isConsumed(String eventId) {
        return consumedEventRepository.exists(
                CONSUMER_NAME,
                eventId
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RefundPreparation prepareRefund(
            RefundMessageEnvelope envelope
    ) {
        RefundMessageEnvelope checked = Objects.requireNonNull(
                envelope,
                "envelope must not be null"
        );
        PaymentRecord payment = findAndValidate(checked.payload());
        if (payment.status() == PaymentStatus.REFUNDED) {
            return RefundPreparation.alreadyRefunded(
                    payment.refundReference(),
                    payment.refundedAt()
            );
        }
        if (payment.status() == PaymentStatus.SUCCEEDED) {
            payment.requestRefund(
                    checked.payload().reason(),
                    clock.instant()
            );
            paymentRecordRepository.save(payment);
            return RefundPreparation.ready();
        }
        if (payment.status() != PaymentStatus.REFUND_PENDING) {
            throw new RefundMessageConflictException(
                    "payment is not waiting for a refund: "
                            + checked.payload().paymentNumber()
            );
        }
        return RefundPreparation.ready();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RefundProcessingResult completeRefund(
            RefundMessageEnvelope envelope,
            RefundReceipt receipt
    ) {
        RefundMessageEnvelope checkedEnvelope = Objects.requireNonNull(
                envelope,
                "envelope must not be null"
        );
        RefundReceipt checkedReceipt = Objects.requireNonNull(
                receipt,
                "receipt must not be null"
        );
        Instant now = clock.instant();
        boolean inserted = consumedEventRepository.insertIfAbsent(
                CONSUMER_NAME,
                checkedEnvelope.eventId(),
                now
        );
        if (!inserted) {
            return new RefundProcessingResult(
                    RefundProcessingOutcome.DUPLICATE_EVENT,
                    checkedEnvelope.payload().paymentNumber(),
                    null
            );
        }

        PaymentRefundRequiredMessage message = checkedEnvelope.payload();
        PaymentRecord payment = findAndValidate(message);
        if (payment.status() == PaymentStatus.REFUNDED) {
            return new RefundProcessingResult(
                    RefundProcessingOutcome.ALREADY_REFUNDED,
                    message.paymentNumber(),
                    payment.refundReference()
            );
        }
        if (payment.status() != PaymentStatus.REFUND_PENDING) {
            throw new RefundMessageConflictException(
                    "payment is not waiting for a refund: "
                            + message.paymentNumber()
            );
        }
        payment.confirmRefund(
                checkedReceipt.refundReference(),
                checkedReceipt.refundedAt()
        );
        refundedBookingPort.markRefunded(
                payment.bookingNumber(),
                checkedReceipt.refundedAt()
        );
        paymentRecordRepository.save(payment);
        return new RefundProcessingResult(
                RefundProcessingOutcome.REFUNDED,
                message.paymentNumber(),
                checkedReceipt.refundReference()
        );
    }

    private PaymentRecord findAndValidate(
            PaymentRefundRequiredMessage message
    ) {
        PaymentRecord payment = paymentRecordRepository
                .findByPaymentNumber(
                        PaymentNumber.of(message.paymentNumber())
                )
                .orElseThrow(
                        () -> new RefundPaymentNotFoundException(
                                message.paymentNumber()
                        )
                );
        validateMessage(payment, message);
        return payment;
    }

    private void validateMessage(
            PaymentRecord payment,
            PaymentRefundRequiredMessage message
    ) {
        boolean identityMatches = payment.bookingNumber().toString()
                        .equals(message.bookingNumber())
                && payment.amount().amount()
                        .compareTo(message.amount()) == 0;
        boolean reasonMatches = payment.status() == PaymentStatus.SUCCEEDED
                || Objects.equals(
                        payment.failureReason(),
                        message.reason()
                );
        if (!identityMatches || !reasonMatches) {
            throw new RefundMessageConflictException(
                    "refund message conflicts with payment record: "
                            + message.paymentNumber()
            );
        }
    }
}

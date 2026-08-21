package com.schoolbus.bookingservice.infrastructure.payment;

import com.schoolbus.bookingservice.support.payment.application.PaymentRefundOutboxPort;
import com.schoolbus.bookingservice.support.payment.application.RefundRequiredEvent;
import com.schoolbus.bookingservice.application.tripcancellation.TripCancellationRefundPort;
import com.schoolbus.bookingservice.domain.order.BookingAmount;
import com.schoolbus.bookingservice.domain.order.BookingNumber;
import com.schoolbus.bookingservice.domain.order.BookingOrder;
import com.schoolbus.bookingservice.support.payment.domain.PaymentNumber;
import com.schoolbus.bookingservice.infrastructure.persistence.PaymentRefundLookupMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Component
@Profile("!test")
public class SharedDatabaseTripCancellationRefundAdapter
        implements TripCancellationRefundPort {

    public static final String REASON = "TRIP_CANCELLED";
    private static final ZoneOffset DATABASE_ZONE = ZoneOffset.UTC;

    private final PaymentRefundLookupMapper paymentMapper;
    private final PaymentRefundOutboxPort refundOutboxPort;

    public SharedDatabaseTripCancellationRefundAdapter(
            PaymentRefundLookupMapper paymentMapper,
            PaymentRefundOutboxPort refundOutboxPort
    ) {
        this.paymentMapper = Objects.requireNonNull(paymentMapper);
        this.refundOutboxPort = Objects.requireNonNull(refundOutboxPort);
    }

    @Override
    public void requestRefund(BookingOrder order, Instant requestedAt) {
        BookingOrder checkedOrder = Objects.requireNonNull(
                order,
                "order must not be null"
        );
        if (checkedOrder.paymentReference() == null) {
            throw new IllegalStateException(
                    "paid booking does not contain a payment reference"
            );
        }
        Instant checkedTime = Objects.requireNonNull(requestedAt);
        PaymentRefundLookupMapper.PaymentRefundRow payment = paymentMapper
                .selectByPaymentNumber(
                        checkedOrder.paymentReference().toString()
                );
        if (payment == null) {
            throw new IllegalStateException(
                    "payment was not found for booking "
                            + checkedOrder.bookingNumber()
            );
        }
        if (!payment.bookingNumber().equals(
                checkedOrder.bookingNumber().toString()
        )
                || payment.amount().compareTo(
                        checkedOrder.amount().amount()
                ) != 0) {
            throw new IllegalStateException(
                    "payment does not match booking "
                            + checkedOrder.bookingNumber()
            );
        }
        if ("REFUND_PENDING".equals(payment.status())
                || "REFUNDED".equals(payment.status())) {
            return;
        }
        if (!"SUCCEEDED".equals(payment.status())) {
            throw new IllegalStateException(
                    "only succeeded payment can request a refund"
            );
        }
        int updated = paymentMapper.markRefundPending(
                payment.id(),
                payment.version(),
                REASON,
                LocalDateTime.ofInstant(checkedTime, DATABASE_ZONE)
        );
        if (updated != 1) {
            throw new IllegalStateException(
                    "failed to mark payment refund-pending for booking "
                            + checkedOrder.bookingNumber()
            );
        }
        refundOutboxPort.append(new RefundRequiredEvent(
                PaymentNumber.of(payment.paymentNumber()),
                BookingNumber.of(payment.bookingNumber()),
                new BookingAmount(payment.amount()),
                REASON,
                payment.completedAt().toInstant(DATABASE_ZONE),
                checkedTime
        ));
    }
}

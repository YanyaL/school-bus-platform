package com.schoolbus.payment.infrastructure.booking;

import com.schoolbus.booking.application.tripcancellation.TripCancellationRefundPort;
import com.schoolbus.booking.domain.order.BookingOrder;
import com.schoolbus.payment.application.PaymentRefundOutboxPort;
import com.schoolbus.payment.application.RefundRequiredEvent;
import com.schoolbus.payment.domain.PaymentNumber;
import com.schoolbus.payment.domain.PaymentRecord;
import com.schoolbus.payment.domain.PaymentRecordRepository;
import com.schoolbus.payment.domain.PaymentStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

@Component
@Profile("!test")
public class LocalTripCancellationRefundAdapter
        implements TripCancellationRefundPort {

    public static final String REASON = "TRIP_CANCELLED";

    private final PaymentRecordRepository paymentRepository;
    private final PaymentRefundOutboxPort refundOutboxPort;

    public LocalTripCancellationRefundAdapter(
            PaymentRecordRepository paymentRepository,
            PaymentRefundOutboxPort refundOutboxPort
    ) {
        this.paymentRepository = Objects.requireNonNull(paymentRepository);
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
        PaymentRecord payment = paymentRepository.findByPaymentNumber(
                        PaymentNumber.of(
                                checkedOrder.paymentReference().toString()
                        )
                )
                .orElseThrow(() -> new IllegalStateException(
                        "payment was not found for booking "
                                + checkedOrder.bookingNumber()
                ));
        if (!payment.bookingNumber().equals(checkedOrder.bookingNumber())
                || payment.amount().amount().compareTo(
                        checkedOrder.amount().amount()
                ) != 0) {
            throw new IllegalStateException(
                    "payment does not match booking "
                            + checkedOrder.bookingNumber()
            );
        }
        if (payment.status() == PaymentStatus.REFUND_PENDING
                || payment.status() == PaymentStatus.REFUNDED) {
            return;
        }

        Instant checkedTime = Objects.requireNonNull(requestedAt);
        payment.requestRefund(REASON, checkedTime);
        paymentRepository.save(payment);
        refundOutboxPort.append(new RefundRequiredEvent(
                payment.paymentNumber(),
                payment.bookingNumber(),
                payment.amount(),
                REASON,
                payment.completedAt(),
                checkedTime
        ));
    }
}

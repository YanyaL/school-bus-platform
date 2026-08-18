package com.schoolbus.payment.application.refund;

import org.springframework.context.annotation.Profile;
import com.schoolbus.payment.config.ConditionalOnEmbeddedRefundMessaging;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@Profile("local")
@ConditionalOnEmbeddedRefundMessaging
public class PaymentRefundApplicationService {

    private final PaymentRefundTransaction transaction;
    private final RefundGateway refundGateway;

    public PaymentRefundApplicationService(
            PaymentRefundTransaction transaction,
            RefundGateway refundGateway
    ) {
        this.transaction = Objects.requireNonNull(
                transaction,
                "transaction must not be null"
        );
        this.refundGateway = Objects.requireNonNull(
                refundGateway,
                "refundGateway must not be null"
        );
    }

    public RefundProcessingResult process(
            RefundMessageEnvelope envelope
    ) {
        RefundMessageEnvelope checked = Objects.requireNonNull(
                envelope,
                "envelope must not be null"
        );
        if (transaction.isConsumed(checked.eventId())) {
            return new RefundProcessingResult(
                    RefundProcessingOutcome.DUPLICATE_EVENT,
                    checked.payload().paymentNumber(),
                    null
            );
        }
        RefundPreparation preparation = transaction.prepareRefund(
                checked
        );
        if (preparation.alreadyRefunded()) {
            return transaction.completeRefund(
                    checked,
                    new RefundReceipt(
                            preparation.refundReference(),
                            preparation.refundedAt()
                    )
            );
        }
        PaymentRefundRequiredMessage payload = checked.payload();
        RefundReceipt receipt = refundGateway.refund(
                new RefundRequest(
                        "payment-refund:" + payload.paymentNumber(),
                        payload.paymentNumber(),
                        payload.amount(),
                        payload.reason()
                )
        );
        return transaction.completeRefund(checked, receipt);
    }
}

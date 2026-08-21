package com.schoolbus.payment.infrastructure.refund;

import com.schoolbus.payment.application.refund.RefundGateway;
import com.schoolbus.payment.application.refund.RefundReceipt;
import com.schoolbus.payment.application.refund.RefundRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import com.schoolbus.payment.config.ConditionalOnEmbeddedRefundMessaging;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Objects;

@Component
@Profile("local")
@ConditionalOnEmbeddedRefundMessaging
public class SimulatedRefundGateway implements RefundGateway {

    private static final Logger log = LoggerFactory.getLogger(
            SimulatedRefundGateway.class
    );

    private final Clock clock;

    public SimulatedRefundGateway(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public RefundReceipt refund(RefundRequest request) {
        RefundRequest checked = Objects.requireNonNull(
                request,
                "request must not be null"
        );
        String refundReference = "SIM-" + checked.idempotencyKey();
        log.warn(
                "Simulated refund executed: paymentNumber={}, amount={}, idempotencyKey={}",
                checked.paymentNumber(),
                checked.amount(),
                checked.idempotencyKey()
        );
        return new RefundReceipt(refundReference, clock.instant());
    }
}

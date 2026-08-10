package com.schoolbus.payment.application.refund;

public record RefundPreparation(
        boolean alreadyRefunded,
        String refundReference,
        java.time.Instant refundedAt
) {

    public static RefundPreparation ready() {
        return new RefundPreparation(false, null, null);
    }

    public static RefundPreparation alreadyRefunded(
            String refundReference,
            java.time.Instant refundedAt
    ) {
        return new RefundPreparation(
                true,
                refundReference,
                refundedAt
        );
    }
}

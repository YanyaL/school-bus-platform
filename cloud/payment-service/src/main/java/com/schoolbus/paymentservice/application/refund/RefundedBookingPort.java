package com.schoolbus.paymentservice.application.refund;

public interface RefundedBookingPort {

    void markRefunded(PaymentRefundedCommand command);
}

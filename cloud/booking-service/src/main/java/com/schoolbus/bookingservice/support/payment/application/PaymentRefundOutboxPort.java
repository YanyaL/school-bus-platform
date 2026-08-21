package com.schoolbus.bookingservice.support.payment.application;

public interface PaymentRefundOutboxPort {

    void append(RefundRequiredEvent event);
}

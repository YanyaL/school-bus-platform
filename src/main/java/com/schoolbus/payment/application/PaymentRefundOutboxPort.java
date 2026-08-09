package com.schoolbus.payment.application;

public interface PaymentRefundOutboxPort {

    void append(RefundRequiredEvent event);
}

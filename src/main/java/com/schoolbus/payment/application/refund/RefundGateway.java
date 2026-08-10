package com.schoolbus.payment.application.refund;

public interface RefundGateway {

    RefundReceipt refund(RefundRequest request);
}

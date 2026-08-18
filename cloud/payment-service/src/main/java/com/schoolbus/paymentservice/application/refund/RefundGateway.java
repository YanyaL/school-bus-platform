package com.schoolbus.paymentservice.application.refund;

public interface RefundGateway {

    RefundReceipt refund(RefundRequest request);
}

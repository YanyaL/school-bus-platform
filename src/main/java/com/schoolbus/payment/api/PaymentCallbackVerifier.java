package com.schoolbus.payment.api;

public interface PaymentCallbackVerifier {

    void verify(String rawBody, String signature);
}

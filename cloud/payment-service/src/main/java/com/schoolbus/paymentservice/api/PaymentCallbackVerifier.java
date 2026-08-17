package com.schoolbus.paymentservice.api;

public interface PaymentCallbackVerifier {

    void verify(String rawBody, String signature);
}

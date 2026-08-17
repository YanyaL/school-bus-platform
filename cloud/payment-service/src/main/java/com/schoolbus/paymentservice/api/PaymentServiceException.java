package com.schoolbus.paymentservice.api;

import org.springframework.http.HttpStatus;

public final class PaymentServiceException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public PaymentServiceException(
            String code,
            String message,
            HttpStatus status
    ) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}

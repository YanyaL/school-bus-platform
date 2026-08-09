package com.schoolbus.payment.application;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public final class PaymentConcurrencyException extends BusinessException {
    public PaymentConcurrencyException(Throwable cause) {
        super(ErrorCode.PAYMENT_CONCURRENCY_CONFLICT);
        initCause(cause);
    }
}

package com.schoolbus.payment.application;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public final class PaymentRequestConflictException extends BusinessException {
    public PaymentRequestConflictException() {
        super(ErrorCode.PAYMENT_REQUEST_CONFLICT);
    }
}

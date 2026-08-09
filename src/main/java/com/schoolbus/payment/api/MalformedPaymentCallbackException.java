package com.schoolbus.payment.api;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public final class MalformedPaymentCallbackException extends BusinessException {
    public MalformedPaymentCallbackException() {
        super(ErrorCode.MALFORMED_PAYMENT_CALLBACK);
    }
}

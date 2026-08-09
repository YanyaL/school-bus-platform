package com.schoolbus.payment.api;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public final class InvalidPaymentSignatureException extends BusinessException {
    public InvalidPaymentSignatureException() {
        super(ErrorCode.INVALID_PAYMENT_SIGNATURE);
    }
}

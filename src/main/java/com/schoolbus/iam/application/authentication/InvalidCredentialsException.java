package com.schoolbus.iam.application.authentication;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public final class InvalidCredentialsException
        extends BusinessException {

    public InvalidCredentialsException() {
        super(
                ErrorCode.INVALID_CREDENTIALS,
                "invalid student number or password"
        );
    }
}

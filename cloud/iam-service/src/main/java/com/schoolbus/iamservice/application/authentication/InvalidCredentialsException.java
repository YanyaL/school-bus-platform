package com.schoolbus.iamservice.application.authentication;

import com.schoolbus.iamservice.api.BusinessException;
import com.schoolbus.iamservice.api.ErrorCode;

public final class InvalidCredentialsException
        extends BusinessException {

    public InvalidCredentialsException() {
        super(
                ErrorCode.INVALID_CREDENTIALS,
                "invalid student number or password"
        );
    }
}

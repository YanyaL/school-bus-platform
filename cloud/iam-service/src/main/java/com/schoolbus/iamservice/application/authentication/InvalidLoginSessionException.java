package com.schoolbus.iamservice.application.authentication;

import com.schoolbus.iamservice.api.BusinessException;
import com.schoolbus.iamservice.api.ErrorCode;

public final class InvalidLoginSessionException
        extends BusinessException {

    public InvalidLoginSessionException() {
        super(
                ErrorCode.INVALID_LOGIN_SESSION,
                "invalid login session"
        );
    }
}

package com.schoolbus.iam.application.authentication;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public final class InvalidLoginSessionException
        extends BusinessException {

    public InvalidLoginSessionException() {
        super(
                ErrorCode.INVALID_LOGIN_SESSION,
                "invalid login session"
        );
    }
}

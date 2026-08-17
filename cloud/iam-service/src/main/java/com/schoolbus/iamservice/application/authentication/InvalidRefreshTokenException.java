package com.schoolbus.iamservice.application.authentication;

import com.schoolbus.iamservice.api.BusinessException;
import com.schoolbus.iamservice.api.ErrorCode;

public final class InvalidRefreshTokenException
        extends BusinessException {

    public InvalidRefreshTokenException() {
        super(
                ErrorCode.INVALID_REFRESH_TOKEN,
                "invalid or expired refresh token"
        );
    }
}

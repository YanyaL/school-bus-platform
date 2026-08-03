package com.schoolbus.iam.application.authentication;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public final class InvalidRefreshTokenException
        extends BusinessException {

    public InvalidRefreshTokenException() {
        super(
                ErrorCode.INVALID_REFRESH_TOKEN,
                "invalid or expired refresh token"
        );
    }
}

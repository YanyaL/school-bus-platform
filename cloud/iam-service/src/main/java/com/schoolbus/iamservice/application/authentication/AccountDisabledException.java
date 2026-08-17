package com.schoolbus.iamservice.application.authentication;

import com.schoolbus.iamservice.api.BusinessException;
import com.schoolbus.iamservice.api.ErrorCode;

public final class AccountDisabledException
        extends BusinessException {

    public AccountDisabledException() {
        super(
                ErrorCode.ACCOUNT_DISABLED,
                "account is disabled"
        );
    }
}

package com.schoolbus.iam.application.authentication;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public final class AccountDisabledException
        extends BusinessException {

    public AccountDisabledException() {
        super(
                ErrorCode.ACCOUNT_DISABLED,
                "account is disabled"
        );
    }
}

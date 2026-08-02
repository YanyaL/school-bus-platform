package com.schoolbus.iam.application.authentication;

import com.schoolbus.iam.domain.account.Account;

public interface AccessTokenIssuer {

    AccessToken issue(Account account);
}

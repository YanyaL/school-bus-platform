package com.schoolbus.iamservice.application.authentication;

import com.schoolbus.iamservice.domain.account.Account;

public interface AccessTokenIssuer {

    AccessToken issue(Account account, String sessionId);
}

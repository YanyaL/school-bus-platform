package com.schoolbus.iamservice.application.authentication;

import java.time.Instant;

public interface AccessTokenRevocationRepository {

    void revokeIssuedBefore(String subject, Instant revokedAt);
}

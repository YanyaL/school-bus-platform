package com.schoolbus.iamservice.application.authentication;

import com.schoolbus.iamservice.domain.identity.UserId;

import java.util.Optional;

public interface LoginSessionRepository {

    void save(LoginSession session);

    Optional<LoginSession> findBySessionId(
            String sessionId
    );

    Optional<LoginSession> findByRefreshTokenHash(
            String refreshTokenHash
    );

    boolean replaceRefreshToken(
            LoginSession replacement,
            String expectedRefreshTokenHash
    );

    void deleteBySessionId(String sessionId);

    void deleteByUserId(UserId userId);
}

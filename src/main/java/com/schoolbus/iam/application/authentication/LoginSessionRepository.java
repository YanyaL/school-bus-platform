package com.schoolbus.iam.application.authentication;

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
}

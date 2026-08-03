package com.schoolbus.iam.application.authentication;

import com.schoolbus.shared.domain.identity.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginSessionTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-08-03T01:00:00Z");
    private static final Instant EXPIRES_AT =
            CREATED_AT.plusSeconds(604_800);

    @Test
    void shouldRemainActiveBeforeExpiration() {
        LoginSession session = validSession();

        assertThat(
                session.isExpiredAt(
                        EXPIRES_AT.minusSeconds(1)
                )
        ).isFalse();
    }

    @Test
    void shouldExpireAtExpirationInstant() {
        LoginSession session = validSession();

        assertThat(session.isExpiredAt(EXPIRES_AT)).isTrue();
        assertThat(
                session.isExpiredAt(
                        EXPIRES_AT.plusSeconds(1)
                )
        ).isTrue();
    }

    @Test
    void shouldRejectBlankSessionId() {
        assertThatThrownBy(
                () -> new LoginSession(
                        " ",
                        UserId.of(1000001L),
                        "refresh-token-hash",
                        CREATED_AT,
                        EXPIRES_AT
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBlankRefreshTokenHash() {
        assertThatThrownBy(
                () -> new LoginSession(
                        "session-001",
                        UserId.of(1000001L),
                        " ",
                        CREATED_AT,
                        EXPIRES_AT
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectExpirationNotAfterCreation() {
        assertThatThrownBy(
                () -> new LoginSession(
                        "session-001",
                        UserId.of(1000001L),
                        "refresh-token-hash",
                        CREATED_AT,
                        CREATED_AT
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    private LoginSession validSession() {
        return new LoginSession(
                "session-001",
                UserId.of(1000001L),
                "refresh-token-hash",
                CREATED_AT,
                EXPIRES_AT
        );
    }
}

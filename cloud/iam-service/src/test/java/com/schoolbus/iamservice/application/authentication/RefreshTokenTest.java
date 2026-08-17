package com.schoolbus.iamservice.application.authentication;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshTokenTest {

    private static final Instant ISSUED_AT =
            Instant.parse("2026-08-02T05:00:00Z");

    @Test
    void shouldRejectBlankTokenValue() {
        assertThatThrownBy(
                () -> new RefreshToken(
                        " ",
                        ISSUED_AT,
                        ISSUED_AT.plusSeconds(60)
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectExpirationNotAfterIssuedTime() {
        assertThatThrownBy(
                () -> new RefreshToken(
                        "refresh-token",
                        ISSUED_AT,
                        ISSUED_AT
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }
}

package com.schoolbus.iamservice.application.authentication;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessTokenTest {

    private static final Instant ISSUED_AT =
            Instant.parse("2026-08-02T05:00:00Z");

    @Test
    void shouldRejectBlankTokenValue() {
        assertThatThrownBy(
                () -> new AccessToken(
                        " ",
                        AccessToken.BEARER_TYPE,
                        ISSUED_AT,
                        ISSUED_AT.plusSeconds(900)
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectExpirationBeforeIssuedTime() {
        assertThatThrownBy(
                () -> new AccessToken(
                        "header.payload.signature",
                        AccessToken.BEARER_TYPE,
                        ISSUED_AT,
                        ISSUED_AT.minusSeconds(1)
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }
}

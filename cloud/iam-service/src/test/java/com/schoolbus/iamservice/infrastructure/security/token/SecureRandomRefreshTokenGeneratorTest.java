package com.schoolbus.iamservice.infrastructure.security.token;

import com.schoolbus.iamservice.application.authentication.RefreshToken;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SecureRandomRefreshTokenGeneratorTest {

    private static final Instant NOW =
            Instant.parse("2026-08-02T05:00:00Z");

    @Test
    void shouldGenerateUrlSafeRefreshTokenWithExpiration() {
        SecureRandomRefreshTokenGenerator generator =
                new SecureRandomRefreshTokenGenerator(
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        Duration.ofDays(7),
                        new SecureRandom()
                );

        RefreshToken token = generator.generate();

        assertThat(token.value())
                .hasSize(43)
                .matches("^[A-Za-z0-9_-]+$");
        assertThat(token.issuedAt()).isEqualTo(NOW);
        assertThat(token.expiresAt())
                .isEqualTo(NOW.plus(Duration.ofDays(7)));
    }

    @Test
    void shouldGenerateDifferentValuesEachTime() {
        SecureRandomRefreshTokenGenerator generator =
                new SecureRandomRefreshTokenGenerator(
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        Duration.ofDays(7),
                        new SecureRandom()
                );

        String firstValue = generator.generate().value();
        String secondValue = generator.generate().value();

        assertThat(firstValue).isNotEqualTo(secondValue);
    }
}

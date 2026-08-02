package com.schoolbus.iam.infrastructure.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "school-bus.security.jwt")
public record JwtProperties(
        String issuer,
        String audience,
        Duration accessTokenTtl
) {

    public JwtProperties {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException(
                    "JWT issuer must not be blank"
            );
        }
        if (audience == null || audience.isBlank()) {
            throw new IllegalArgumentException(
                    "JWT audience must not be blank"
            );
        }
        if (accessTokenTtl == null
                || accessTokenTtl.isZero()
                || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException(
                    "JWT accessTokenTtl must be positive"
            );
        }
    }
}

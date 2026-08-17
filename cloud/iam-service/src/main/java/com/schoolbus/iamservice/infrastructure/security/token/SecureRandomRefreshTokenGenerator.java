package com.schoolbus.iamservice.infrastructure.security.token;

import com.schoolbus.iamservice.application.authentication.RefreshToken;
import com.schoolbus.iamservice.application.authentication.RefreshTokenGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

@Component
public class SecureRandomRefreshTokenGenerator
        implements RefreshTokenGenerator {

    private static final int TOKEN_LENGTH_BYTES = 32;

    private final Clock clock;
    private final Duration timeToLive;
    private final SecureRandom secureRandom;

    @Autowired
    public SecureRandomRefreshTokenGenerator(
            Clock clock,
            @Value("${school-bus.security.refresh-token.ttl}")
            Duration timeToLive
    ) {
        this(clock, timeToLive, new SecureRandom());
    }

    SecureRandomRefreshTokenGenerator(
            Clock clock,
            Duration timeToLive,
            SecureRandom secureRandom
    ) {
        this.clock = Objects.requireNonNull(
                clock,
                "clock must not be null"
        );
        this.timeToLive = Objects.requireNonNull(
                timeToLive,
                "timeToLive must not be null"
        );
        if (timeToLive.isZero() || timeToLive.isNegative()) {
            throw new IllegalArgumentException(
                    "timeToLive must be positive"
            );
        }
        this.secureRandom = Objects.requireNonNull(
                secureRandom,
                "secureRandom must not be null"
        );
    }

    @Override
    public RefreshToken generate() {
        byte[] randomBytes = new byte[TOKEN_LENGTH_BYTES];
        secureRandom.nextBytes(randomBytes);

        String value = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
        Instant issuedAt = clock.instant();

        return new RefreshToken(
                value,
                issuedAt,
                issuedAt.plus(timeToLive)
        );
    }
}

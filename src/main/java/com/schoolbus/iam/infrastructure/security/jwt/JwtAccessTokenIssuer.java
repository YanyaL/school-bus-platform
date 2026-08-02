package com.schoolbus.iam.infrastructure.security.jwt;

import com.schoolbus.iam.application.authentication.AccessToken;
import com.schoolbus.iam.application.authentication.AccessTokenIssuer;
import com.schoolbus.iam.domain.account.Account;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtAccessTokenIssuer(
            JwtEncoder jwtEncoder,
            JwtProperties properties,
            Clock clock
    ) {
        this.jwtEncoder = Objects.requireNonNull(
                jwtEncoder,
                "jwtEncoder must not be null"
        );
        this.properties = Objects.requireNonNull(
                properties,
                "properties must not be null"
        );
        this.clock = Objects.requireNonNull(
                clock,
                "clock must not be null"
        );
    }

    @Override
    public AccessToken issue(Account account) {
        Account validatedAccount = Objects.requireNonNull(
                account,
                "account must not be null"
        );
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(
                properties.accessTokenTtl()
        );
        List<String> roles = validatedAccount.roles()
                .stream()
                .map(Enum::name)
                .sorted()
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .subject(
                        Long.toString(
                                validatedAccount.userId().value()
                        )
                )
                .claim("roles", roles)
                .build();
        JwsHeader header = JwsHeader
                .with(SignatureAlgorithm.RS256)
                .type("JWT")
                .build();
        String tokenValue = jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();

        return new AccessToken(
                tokenValue,
                AccessToken.BEARER_TYPE,
                issuedAt,
                expiresAt
        );
    }
}

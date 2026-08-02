package com.schoolbus.iam.infrastructure.security.jwt;

import com.schoolbus.iam.application.authentication.AccessToken;
import com.schoolbus.iam.domain.account.Account;
import com.schoolbus.iam.domain.account.PasswordHash;
import com.schoolbus.iam.domain.account.StudentNumber;
import com.schoolbus.shared.domain.identity.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAccessTokenIssuerTest {

    private static final Instant NOW =
            Instant.now().plusSeconds(60);

    private JwtDecoder jwtDecoder;
    private JwtAccessTokenIssuer tokenIssuer;

    @BeforeEach
    void setUp() {
        JwtConfiguration configuration = new JwtConfiguration();
        JwtProperties properties = new JwtProperties(
                "https://school-bus.local",
                "school-bus-api",
                Duration.ofMinutes(15)
        );
        KeyPair keyPair = configuration.jwtKeyPair();
        JwtEncoder jwtEncoder = configuration.jwtEncoder(keyPair);
        jwtDecoder = configuration.jwtDecoder(
                keyPair,
                properties
        );
        tokenIssuer = new JwtAccessTokenIssuer(
                jwtEncoder,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldIssueSignedAndVerifiableAccessToken() {
        Account account = Account.register(
                UserId.of(1000001L),
                StudentNumber.of("S4789503"),
                PasswordHash.of(
                        "{bcrypt}$2a$10$encodedPassword"
                ),
                NOW
        );

        AccessToken accessToken = tokenIssuer.issue(account);
        Jwt jwt = jwtDecoder.decode(accessToken.value());

        assertThat(accessToken.tokenType()).isEqualTo("Bearer");
        assertThat(accessToken.issuedAt()).isEqualTo(NOW);
        assertThat(accessToken.expiresAt())
                .isEqualTo(NOW.plusSeconds(900));
        assertThat(jwt.getSubject()).isEqualTo("1000001");
        assertThat(jwt.getIssuer().toString())
                .isEqualTo("https://school-bus.local");
        assertThat(jwt.getAudience())
                .containsExactly("school-bus-api");
        assertThat(jwt.getClaimAsStringList("roles"))
                .containsExactly("STUDENT");
        assertThat(jwt.getClaims())
                .doesNotContainKey("studentNumber");
        assertThat(jwt.getId()).isNotBlank();
    }
}

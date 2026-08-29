package com.schoolbus.iamservice.infrastructure.security.jwt;

import com.schoolbus.iamservice.application.authentication.AccessToken;
import com.schoolbus.iamservice.domain.account.Account;
import com.schoolbus.iamservice.domain.account.PasswordHash;
import com.schoolbus.iamservice.domain.account.StudentNumber;
import com.schoolbus.iamservice.domain.identity.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAccessTokenIssuerTest {

    private static final Instant NOW =
            Instant.now().plusSeconds(60);

    @TempDir
    Path tempDir;

    private JwtDecoder jwtDecoder;
    private JwtAccessTokenIssuer tokenIssuer;

    @BeforeEach
    void setUp() throws Exception {
        KeyPair generated = KeyPairGenerator.getInstance("RSA")
                .generateKeyPair();
        Path publicKeyPath = tempDir.resolve("public.pem");
        Path privateKeyPath = tempDir.resolve("private.pem");
        writePublicKey(publicKeyPath, (RSAPublicKey) generated.getPublic());
        writePrivateKey(privateKeyPath, (RSAPrivateKey) generated.getPrivate());

        JwtConfiguration configuration = new JwtConfiguration();
        JwtProperties properties = new JwtProperties(
                "https://school-bus.local",
                "school-bus-api",
                Duration.ofMinutes(15),
                "file:" + publicKeyPath.toAbsolutePath(),
                "file:" + privateKeyPath.toAbsolutePath()
        );
        KeyPair keyPair = configuration.jwtKeyPair(
                properties,
                new DefaultResourceLoader()
        );
        JwtEncoder jwtEncoder = configuration.jwtEncoder(
                configuration.jwtJwkSource(keyPair)
        );
        jwtDecoder = configuration.jwtDecoder(keyPair, properties);
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

        AccessToken accessToken = tokenIssuer.issue(
                account,
                "session-001"
        );
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
        assertThat(jwt.getClaimAsString("sid"))
                .isEqualTo("session-001");
        assertThat(jwt.<Number>getClaim("iat_ms").longValue())
                .isEqualTo(NOW.toEpochMilli());
        assertThat(jwt.getClaims())
                .doesNotContainKey("studentNumber");
        assertThat(jwt.getId()).isNotBlank();
    }

    private static void writePublicKey(Path path, RSAPublicKey publicKey)
            throws Exception {
        String pem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(publicKey.getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
        Files.writeString(path, pem);
    }

    private static void writePrivateKey(Path path, RSAPrivateKey privateKey)
            throws Exception {
        String pem = "-----BEGIN " + "PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(privateKey.getEncoded())
                + "\n-----END " + "PRIVATE KEY-----\n";
        Files.writeString(path, pem);
    }
}

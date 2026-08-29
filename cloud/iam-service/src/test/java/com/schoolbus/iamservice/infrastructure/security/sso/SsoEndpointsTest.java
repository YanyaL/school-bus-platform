package com.schoolbus.iamservice.infrastructure.security.sso;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.iamservice.application.authentication.AccessTokenRevocationRepository;
import com.schoolbus.iamservice.domain.account.Account;
import com.schoolbus.iamservice.domain.account.AccountStatus;
import com.schoolbus.iamservice.domain.account.PasswordHash;
import com.schoolbus.iamservice.domain.account.Role;
import com.schoolbus.iamservice.domain.account.StudentNumber;
import com.schoolbus.iamservice.domain.identity.UserId;
import com.schoolbus.iamservice.infrastructure.persistence.MyBatisAccountRepository;
import com.schoolbus.iamservice.infrastructure.session.RedisLoginSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.flyway.enabled=false",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
@AutoConfigureMockMvc
class SsoEndpointsTest {

    private static final Path PUBLIC_KEY_PATH;
    private static final Path PRIVATE_KEY_PATH;

    static {
        try {
            KeyPair keyPair = KeyPairGenerator.getInstance("RSA")
                    .generateKeyPair();
            Path dir = Files.createTempDirectory("iam-sso-jwt-");
            PUBLIC_KEY_PATH = dir.resolve("public.pem");
            PRIVATE_KEY_PATH = dir.resolve("private.pem");
            writePem(
                    PUBLIC_KEY_PATH,
                    "PUBLIC KEY",
                    ((RSAPublicKey) keyPair.getPublic()).getEncoded()
            );
            writePem(
                    PRIVATE_KEY_PATH,
                    "PRIVATE KEY",
                    ((RSAPrivateKey) keyPair.getPrivate()).getEncoded()
            );
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @DynamicPropertySource
    static void registerJwtKeys(DynamicPropertyRegistry registry) {
        registry.add(
                "school-bus.security.jwt.public-key-location",
                () -> "file:" + PUBLIC_KEY_PATH.toAbsolutePath()
        );
        registry.add(
                "school-bus.security.jwt.private-key-location",
                () -> "file:" + PRIVATE_KEY_PATH.toAbsolutePath()
        );
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("jwtDecoder")
    private JwtDecoder jwtDecoder;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private MyBatisAccountRepository myBatisAccountRepository;

    @MockitoBean
    private RedisLoginSessionRepository redisLoginSessionRepository;

    @MockitoBean
    private AccessTokenRevocationRepository accessTokenRevocationRepository;

    @Test
    void shouldPublishOidcDiscoveryAndJwkSet() throws Exception {
        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer")
                        .value("https://school-bus.local"))
                .andExpect(jsonPath("$.authorization_endpoint")
                        .value("https://school-bus.local/oauth2/authorize"))
                .andExpect(jsonPath("$.end_session_endpoint")
                        .value("https://school-bus.local/connect/logout"))
                .andExpect(jsonPath("$.code_challenge_methods_supported[0]")
                        .value("S256"));

        mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"));
    }

    @Test
    void shouldAllowConfiguredStudentOriginToExchangeCode() throws Exception {
        mockMvc.perform(options("/oauth2/token")
                        .header("Origin", "http://127.0.0.1:5173")
                        .header(
                                "Access-Control-Request-Method",
                                "POST"
                        ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin",
                        "http://127.0.0.1:5173"
                ));
    }

    @Test
    void shouldAllowConfiguredAdminOriginToExchangeCode() throws Exception {
        mockMvc.perform(options("/oauth2/token")
                        .header("Origin", "http://127.0.0.1:5174")
                        .header(
                                "Access-Control-Request-Method",
                                "POST"
                        ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin",
                        "http://127.0.0.1:5174"
                ));
    }

    @Test
    void shouldRejectUntrustedOriginFromTokenEndpoint() throws Exception {
        mockMvc.perform(options("/oauth2/token")
                        .header("Origin", "https://attacker.example")
                        .header(
                                "Access-Control-Request-Method",
                                "POST"
                        ))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        "Access-Control-Allow-Origin"
                ));
    }

    @Test
    void shouldRejectAuthorizationRequestWithoutPkce() throws Exception {
        SchoolBusUserPrincipal principal = studentPrincipal();

        mockMvc.perform(get("/oauth2/authorize")
                        .with(user(principal))
                        .queryParam("response_type", "code")
                        .queryParam("client_id", "school-bus-student-web")
                        .queryParam("scope", "openid profile")
                        .queryParam(
                                "redirect_uri",
                                "http://127.0.0.1:5173/auth/callback"
                        ))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(
                        result.getResponse().getRedirectedUrl()
                ).contains("error=invalid_request", "code_challenge"));
    }

    @Test
    void shouldIssueAuthorizationCodeWhenPkceIsPresent() throws Exception {
        mockMvc.perform(get("/oauth2/authorize")
                        .with(user(studentPrincipal()))
                        .queryParam("response_type", "code")
                        .queryParam("client_id", "school-bus-student-web")
                        .queryParam("scope", "openid profile")
                        .queryParam("state", "state-001")
                        .queryParam("nonce", "nonce-001")
                        .queryParam(
                                "redirect_uri",
                                "http://127.0.0.1:5173/auth/callback"
                        )
                        .queryParam(
                                "code_challenge",
                                "0123456789012345678901234567890123456789012"
                        )
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(
                        result.getResponse().getRedirectedUrl()
                )
                        .startsWith(
                                "http://127.0.0.1:5173/auth/callback?"
                        )
                        .contains("code=", "state=state-001"));
    }

    @Test
    void shouldReuseOneBrowserLoginSessionAcrossStudentAndAdminClients()
            throws Exception {
        when(myBatisAccountRepository.findByStudentNumber(any()))
                .thenReturn(Optional.of(adminAccount("StrongPass!2026")));

        MvcResult login = mockMvc.perform(formLogin()
                        .user("S4789503")
                        .password("StrongPass!2026"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        jakarta.servlet.http.HttpSession session = login.getRequest()
                .getSession(false);
        assertThat(session).isNotNull();

        String studentLocation = authorizeWithSession(
                session,
                "school-bus-student-web",
                "http://127.0.0.1:5173/auth/callback",
                "student-session-state"
        );
        String adminLocation = authorizeWithSession(
                session,
                "school-bus-admin-web",
                "http://127.0.0.1:5174/auth/callback",
                "admin-session-state"
        );

        assertThat(studentLocation)
                .startsWith("http://127.0.0.1:5173/auth/callback?")
                .contains("code=", "state=student-session-state");
        assertThat(adminLocation)
                .startsWith("http://127.0.0.1:5174/auth/callback?")
                .contains("code=", "state=admin-session-state");
        assertThat(queryParameter(studentLocation, "code"))
                .isNotEqualTo(queryParameter(adminLocation, "code"));
    }

    @Test
    void shouldExchangePkceCodeForCompatibleJwtAccessToken()
            throws Exception {
        String verifier =
                "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";
        String challenge = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        MessageDigest.getInstance("SHA-256").digest(
                                verifier.getBytes(StandardCharsets.US_ASCII)
                        )
                );

        MvcResult authorization = mockMvc.perform(get("/oauth2/authorize")
                        .with(user(studentPrincipal()))
                        .queryParam("response_type", "code")
                        .queryParam("client_id", "school-bus-student-web")
                        .queryParam("scope", "openid profile")
                        .queryParam("state", "state-002")
                        .queryParam("nonce", "nonce-002")
                        .queryParam(
                                "redirect_uri",
                                "http://127.0.0.1:5173/auth/callback"
                        )
                        .queryParam("code_challenge", challenge)
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String code = queryParameter(
                authorization.getResponse().getRedirectedUrl(),
                "code"
        );

        MvcResult tokenResponse = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("client_id", "school-bus-student-web")
                        .param("code", code)
                        .param("code_verifier", verifier)
                        .param(
                                "redirect_uri",
                                "http://127.0.0.1:5173/auth/callback"
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.access_token").isString())
                .andExpect(jsonPath("$.refresh_token").doesNotExist())
                .andReturn();

        JsonNode tokenJson = objectMapper.readTree(
                tokenResponse.getResponse().getContentAsString()
        );
        Jwt accessToken = jwtDecoder.decode(
                tokenJson.get("access_token").asText()
        );

        assertThat(accessToken.getSubject()).isEqualTo("1000001");
        assertThat(accessToken.getAudience()).contains("school-bus-api");
        assertThat(accessToken.getClaimAsString("student_number"))
                .isEqualTo("S4789503");
        assertThat(accessToken.getClaimAsStringList("roles"))
                .containsExactly("STUDENT");
        assertThat(accessToken.<Number>getClaim("iat_ms").longValue())
                .isPositive();
    }

    @Test
    void shouldRedirectToRegisteredClientAfterOidcLogout() throws Exception {
        String verifier =
                "logoutabcdefghijklmnopqrstuvwxyz0123456789ABCDE";
        String challenge = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        MessageDigest.getInstance("SHA-256").digest(
                                verifier.getBytes(StandardCharsets.US_ASCII)
                        )
                );

        MvcResult authorization = mockMvc.perform(get("/oauth2/authorize")
                        .with(user(studentPrincipal()))
                        .queryParam("response_type", "code")
                        .queryParam("client_id", "school-bus-student-web")
                        .queryParam("scope", "openid profile")
                        .queryParam("state", "logout-login-state")
                        .queryParam("nonce", "logout-nonce")
                        .queryParam(
                                "redirect_uri",
                                "http://127.0.0.1:5173/auth/callback"
                        )
                        .queryParam("code_challenge", challenge)
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        MvcResult tokenResponse = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("client_id", "school-bus-student-web")
                        .param(
                                "code",
                                queryParameter(
                                        authorization.getResponse()
                                                .getRedirectedUrl(),
                                        "code"
                                )
                        )
                        .param("code_verifier", verifier)
                        .param(
                                "redirect_uri",
                                "http://127.0.0.1:5173/auth/callback"
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_token").isString())
                .andReturn();

        String idToken = objectMapper.readTree(
                tokenResponse.getResponse().getContentAsString()
        ).get("id_token").asText();

        mockMvc.perform(get("/connect/logout")
                        .queryParam("id_token_hint", idToken)
                        .queryParam(
                                "post_logout_redirect_uri",
                                "http://127.0.0.1:5173/auth/logout/callback"
                        )
                        .queryParam("state", "logout-state-001"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(
                        "Location",
                        "http://127.0.0.1:5173/auth/logout/callback"
                                + "?state=logout-state-001"
                ));
    }

    private static SchoolBusUserPrincipal studentPrincipal() {
        return new SchoolBusUserPrincipal(
                1000001L,
                "S4789503",
                "{bcrypt}$2a$10$encodedPassword",
                Set.of("STUDENT"),
                true
        );
    }

    private String authorizeWithSession(
            jakarta.servlet.http.HttpSession session,
            String clientId,
            String redirectUri,
            String state
    ) throws Exception {
        return mockMvc.perform(get("/oauth2/authorize")
                        .session((org.springframework.mock.web.MockHttpSession) session)
                        .queryParam("response_type", "code")
                        .queryParam("client_id", clientId)
                        .queryParam("scope", "openid profile")
                        .queryParam("state", state)
                        .queryParam("nonce", state + "-nonce")
                        .queryParam("redirect_uri", redirectUri)
                        .queryParam(
                                "code_challenge",
                                "0123456789012345678901234567890123456789012"
                        )
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getResponse()
                .getRedirectedUrl();
    }

    private Account adminAccount(String rawPassword) {
        Instant now = Instant.parse("2026-08-28T00:00:00Z");
        return Account.restore(
                UserId.of(1000001L),
                StudentNumber.of("S4789503"),
                PasswordHash.of(passwordEncoder.encode(rawPassword)),
                Set.of(Role.STUDENT, Role.ADMIN),
                AccountStatus.ACTIVE,
                now,
                now
        );
    }

    private static String queryParameter(String location, String name) {
        String query = URI.create(location).getRawQuery();
        return java.util.Arrays.stream(query.split("&"))
                .map(value -> value.split("=", 2))
                .filter(parts -> parts[0].equals(name))
                .map(parts -> URLDecoder.decode(
                        parts[1],
                        StandardCharsets.UTF_8
                ))
                .findFirst()
                .orElseThrow();
    }

    private static void writePem(
            Path path,
            String type,
            byte[] encoded
    ) throws Exception {
        String pem = "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(encoded)
                + "\n-----END " + type + "-----\n";
        Files.writeString(path, pem);
    }
}

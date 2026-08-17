package com.schoolbus.iamservice.api.authentication;

import com.schoolbus.iamservice.application.authentication.AuthenticationApplicationService;
import com.schoolbus.iamservice.application.authentication.AccessToken;
import com.schoolbus.iamservice.application.authentication.AuthenticationResult;
import com.schoolbus.iamservice.application.authentication.InvalidCredentialsException;
import com.schoolbus.iamservice.application.authentication.InvalidRefreshTokenException;
import com.schoolbus.iamservice.application.authentication.LoginCommand;
import com.schoolbus.iamservice.application.authentication.LogoutCommand;
import com.schoolbus.iamservice.application.authentication.RefreshAuthenticationCommand;
import com.schoolbus.iamservice.application.authentication.RefreshToken;
import com.schoolbus.iamservice.domain.account.Account;
import com.schoolbus.iamservice.domain.account.PasswordHash;
import com.schoolbus.iamservice.domain.account.StudentNumber;
import com.schoolbus.iamservice.api.GlobalExceptionHandler;
import com.schoolbus.iamservice.config.SecurityConfig;
import com.schoolbus.iamservice.domain.identity.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("web-test")
@WebMvcTest(AuthenticationController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class
})
class AuthenticationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationApplicationService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldAuthenticateStudent() throws Exception {
        Account account = Account.register(
                UserId.of(1000001L),
                StudentNumber.of("S4789503"),
                PasswordHash.of(
                        "{bcrypt}$2a$10$encodedPassword"
                ),
                Instant.parse("2026-08-02T05:00:00Z")
        );
        Instant issuedAt = Instant.parse("2026-08-02T05:00:00Z");
        AccessToken accessToken = new AccessToken(
                "header.payload.signature",
                AccessToken.BEARER_TYPE,
                issuedAt,
                issuedAt.plusSeconds(900)
        );
        RefreshToken refreshToken = new RefreshToken(
                "raw-refresh-token",
                issuedAt,
                issuedAt.plusSeconds(604800)
        );
        when(service.authenticate(any())).thenReturn(
                new AuthenticationResult(
                        account,
                        accessToken,
                        refreshToken
                )
        );

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "studentNumber": "s4789503",
                                          "password": "StrongPass!2026"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(
                        jsonPath("$.data.userId")
                                .value("1000001")
                )
                .andExpect(
                        jsonPath("$.data.studentNumber")
                                .value("S4789503")
                )
                .andExpect(
                        jsonPath("$.data.roles[0]")
                                .value("STUDENT")
                )
                .andExpect(
                        jsonPath("$.data.tokenType")
                                .value("Bearer")
                )
                .andExpect(
                        jsonPath("$.data.accessToken")
                                .value("header.payload.signature")
                )
                .andExpect(
                        jsonPath("$.data.accessTokenExpiresAt")
                                .value("2026-08-02T05:15:00Z")
                )
                .andExpect(
                        jsonPath("$.data.refreshToken")
                                .value("raw-refresh-token")
                )
                .andExpect(
                        jsonPath("$.data.refreshTokenExpiresAt")
                                .value("2026-08-09T05:00:00Z")
                );

        verify(service).authenticate(
                new LoginCommand(
                        "s4789503",
                        "StrongPass!2026"
                )
        );
    }

    @Test
    void shouldKeepSnowflakeUserIdAsJsonStringOnLogin()
            throws Exception {
        Instant issuedAt = Instant.parse("2026-08-02T05:00:00Z");
        Account account = Account.register(
                UserId.of(81_765_424_194_125_824L),
                StudentNumber.of("S4789503"),
                PasswordHash.of(
                        "{bcrypt}$2a$10$encodedPassword"
                ),
                issuedAt
        );
        when(service.authenticate(any())).thenReturn(
                new AuthenticationResult(
                        account,
                        new AccessToken(
                                "header.payload.signature",
                                AccessToken.BEARER_TYPE,
                                issuedAt,
                                issuedAt.plusSeconds(900)
                        ),
                        new RefreshToken(
                                "raw-refresh-token",
                                issuedAt,
                                issuedAt.plusSeconds(604800)
                        )
                )
        );

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "studentNumber": "s4789503",
                                          "password": "StrongPass!2026"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data.userId")
                                .value("81765424194125824")
                );
    }

    @Test
    void shouldRejectBlankPassword() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "studentNumber": "S4789503",
                                          "password": ""
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_ERROR")
                );

        verifyNoInteractions(service);
    }

    @Test
    void shouldReturnUnauthorizedForInvalidCredentials()
            throws Exception {
        when(service.authenticate(any()))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "studentNumber": "S4789503",
                                          "password": "WrongPass!2026"
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.code")
                                .value("INVALID_CREDENTIALS")
                )
                .andExpect(
                        jsonPath("$.message").value(
                                "invalid student number or password"
                        )
                );
    }

    @Test
    void shouldRotateRefreshToken() throws Exception {
        Instant issuedAt = Instant.parse("2026-08-02T05:00:00Z");
        Account account = Account.register(
                UserId.of(1000001L),
                StudentNumber.of("S4789503"),
                PasswordHash.of(
                        "{bcrypt}$2a$10$encodedPassword"
                ),
                issuedAt
        );
        AccessToken accessToken = new AccessToken(
                "new.header.payload.signature",
                AccessToken.BEARER_TYPE,
                issuedAt,
                issuedAt.plusSeconds(900)
        );
        RefreshToken refreshToken = new RefreshToken(
                "new-raw-refresh-token",
                issuedAt,
                issuedAt.plusSeconds(604800)
        );
        when(service.refresh(any())).thenReturn(
                new AuthenticationResult(
                        account,
                        accessToken,
                        refreshToken
                )
        );

        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "refreshToken": "old-raw-refresh-token"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(
                        jsonPath("$.data.tokenType")
                                .value("Bearer")
                )
                .andExpect(
                        jsonPath("$.data.accessToken")
                                .value(
                                        "new.header.payload.signature"
                                )
                )
                .andExpect(
                        jsonPath("$.data.accessTokenExpiresAt")
                                .value("2026-08-02T05:15:00Z")
                )
                .andExpect(
                        jsonPath("$.data.refreshToken")
                                .value("new-raw-refresh-token")
                )
                .andExpect(
                        jsonPath("$.data.refreshTokenExpiresAt")
                                .value("2026-08-09T05:00:00Z")
                );

        verify(service).refresh(
                new RefreshAuthenticationCommand(
                        "old-raw-refresh-token"
                )
        );
    }

    @Test
    void shouldRejectBlankRefreshToken() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "refreshToken": ""
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_ERROR")
                );

        verifyNoInteractions(service);
    }

    @Test
    void shouldReturnUnauthorizedForInvalidRefreshToken()
            throws Exception {
        when(service.refresh(any()))
                .thenThrow(new InvalidRefreshTokenException());

        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "refreshToken": "invalid-refresh-token"
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.code")
                                .value("INVALID_REFRESH_TOKEN")
                )
                .andExpect(
                        jsonPath("$.message").value(
                                "invalid or expired refresh token"
                        )
                );
    }

    @Test
    void shouldLogoutAuthenticatedSession() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .with(
                                        jwt().jwt(builder -> builder
                                                .subject("1000001")
                                                .claim(
                                                        "roles",
                                                        List.of("STUDENT")
                                                )
                                                .claim(
                                                        "sid",
                                                        "session-001"
                                                )
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(
                        jsonPath("$.message").value("success")
                );

        verify(service).logout(
                new LogoutCommand("session-001")
        );
    }

    @Test
    void shouldRejectUnauthenticatedLogout() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectAccessTokenWithoutSessionIdOnLogout()
            throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .with(
                                        jwt().jwt(builder -> builder
                                                .subject("1000001")
                                                .claim(
                                                        "roles",
                                                        List.of("STUDENT")
                                                )
                                        )
                                )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.code")
                                .value("INVALID_LOGIN_SESSION")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value("invalid login session")
                );

        verifyNoInteractions(service);
    }

    @Test
    void shouldReturnCurrentAuthenticatedUser() throws Exception {
        mockMvc.perform(
                        get("/api/v1/auth/me")
                                .with(
                                        jwt().jwt(builder -> builder
                                                .subject("1000001")
                                                .claim(
                                                        "roles",
                                                        List.of("STUDENT")
                                                )
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(
                        jsonPath("$.data.userId")
                                .value("1000001")
                )
                .andExpect(
                        jsonPath("$.data.roles[0]")
                                .value("STUDENT")
                );

        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectUnauthenticatedCurrentUserRequest()
            throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }
}

package com.schoolbus.iam.api.authentication;

import com.schoolbus.iam.application.authentication.AuthenticationApplicationService;
import com.schoolbus.iam.application.authentication.AccessToken;
import com.schoolbus.iam.application.authentication.AuthenticationResult;
import com.schoolbus.iam.application.authentication.InvalidCredentialsException;
import com.schoolbus.iam.application.authentication.LoginCommand;
import com.schoolbus.iam.domain.account.Account;
import com.schoolbus.iam.domain.account.PasswordHash;
import com.schoolbus.iam.domain.account.StudentNumber;
import com.schoolbus.shared.api.GlobalExceptionHandler;
import com.schoolbus.shared.config.SecurityConfig;
import com.schoolbus.shared.domain.identity.UserId;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
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
        when(service.authenticate(any())).thenReturn(
                new AuthenticationResult(account, accessToken)
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
                                .value(1000001L)
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
                        jsonPath("$.data.expiresAt")
                                .value("2026-08-02T05:15:00Z")
                );

        verify(service).authenticate(
                new LoginCommand(
                        "s4789503",
                        "StrongPass!2026"
                )
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
}

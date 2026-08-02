package com.schoolbus.iam.api.account;

import com.schoolbus.iam.application.account.DuplicateStudentNumberException;
import com.schoolbus.iam.application.account.RegisterAccountCommand;
import com.schoolbus.iam.application.account.RegistrationApplicationService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("web-test")
@WebMvcTest(RegistrationController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class
})
class RegistrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegistrationApplicationService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldCreateStudentAccount() throws Exception {
        Account account = Account.register(
                UserId.of(1000001L),
                StudentNumber.of("S4789503"),
                PasswordHash.of(
                        "{bcrypt}$2a$10$encodedPassword"
                ),
                Instant.parse("2026-08-02T05:00:00Z")
        );

        when(service.register(any()))
                .thenReturn(account);

        mockMvc.perform(
                        post("/api/v1/accounts")
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
                .andExpect(status().isCreated())
                .andExpect(
                        header().string(
                                "Location",
                                "/api/v1/accounts/1000001"
                        )
                )
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
                        jsonPath("$.data.status")
                                .value("ACTIVE")
                )
                .andExpect(
                        jsonPath("$.data.roles[0]")
                                .value("STUDENT")
                );

        verify(service).register(
                new RegisterAccountCommand(
                        "s4789503",
                        "StrongPass!2026"
                )
        );
    }

    @Test
    void shouldRejectInvalidPassword() throws Exception {
        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "studentNumber": "S4789503",
                                          "password": "short"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_ERROR")
                )
                .andExpect(
                        jsonPath("$.details[0].field")
                                .value("password")
                );

        verifyNoInteractions(service);
    }

    @Test
    void shouldReturnConflictForDuplicateStudentNumber()
            throws Exception {
        when(service.register(any()))
                .thenThrow(
                        new DuplicateStudentNumberException(
                                StudentNumber.of("S4789503")
                        )
                );

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "studentNumber": "S4789503",
                                          "password": "StrongPass!2026"
                                        }
                                        """)
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code").value(
                                "DUPLICATE_STUDENT_NUMBER"
                        )
                )
                .andExpect(
                        jsonPath("$.message").value(
                                "student number already exists: S4789503"
                        )
                );
    }
}

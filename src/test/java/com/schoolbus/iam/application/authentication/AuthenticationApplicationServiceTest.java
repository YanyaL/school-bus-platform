package com.schoolbus.iam.application.authentication;

import com.schoolbus.iam.domain.account.Account;
import com.schoolbus.iam.domain.account.AccountRepository;
import com.schoolbus.iam.domain.account.AccountStatus;
import com.schoolbus.iam.domain.account.PasswordHash;
import com.schoolbus.iam.domain.account.Role;
import com.schoolbus.iam.domain.account.StudentNumber;
import com.schoolbus.shared.domain.identity.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationApplicationServiceTest {

    private static final StudentNumber STUDENT_NUMBER =
            StudentNumber.of("S4789503");

    private static final PasswordHash PASSWORD_HASH =
            PasswordHash.of(
                    "{bcrypt}$2a$10$encodedPassword"
            );

    private static final String RAW_PASSWORD =
            "StrongPass!2026";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AccessTokenIssuer accessTokenIssuer;

    private AuthenticationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AuthenticationApplicationService(
                accountRepository,
                passwordEncoder,
                accessTokenIssuer
        );
    }

    @Test
    void shouldAuthenticateActiveAccount() {
        Account account = activeAccount();
        when(accountRepository.findByStudentNumber(STUDENT_NUMBER))
                .thenReturn(Optional.of(account));
        when(
                passwordEncoder.matches(
                        RAW_PASSWORD,
                        PASSWORD_HASH.value()
                )
        ).thenReturn(true);
        AccessToken accessToken = accessToken();
        when(accessTokenIssuer.issue(account))
                .thenReturn(accessToken);

        AuthenticationResult result = service.authenticate(
                new LoginCommand("s4789503", RAW_PASSWORD)
        );

        assertThat(result.account()).isSameAs(account);
        assertThat(result.accessToken()).isSameAs(accessToken);
    }

    @Test
    void shouldRejectUnknownStudentNumber() {
        when(accountRepository.findByStudentNumber(STUDENT_NUMBER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> service.authenticate(
                        new LoginCommand(
                                "S4789503",
                                RAW_PASSWORD
                        )
                )
        ).isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(accessTokenIssuer);
    }

    @Test
    void shouldRejectIncorrectPassword() {
        Account account = activeAccount();
        when(accountRepository.findByStudentNumber(STUDENT_NUMBER))
                .thenReturn(Optional.of(account));
        when(
                passwordEncoder.matches(
                        RAW_PASSWORD,
                        PASSWORD_HASH.value()
                )
        ).thenReturn(false);

        assertThatThrownBy(
                () -> service.authenticate(
                        new LoginCommand(
                                "S4789503",
                                RAW_PASSWORD
                        )
                )
        ).isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(accessTokenIssuer);
    }

    @Test
    void shouldRejectDisabledAccountAfterPasswordMatches() {
        Instant now = Instant.parse("2026-08-02T05:00:00Z");
        Account account = Account.restore(
                UserId.of(1000001L),
                STUDENT_NUMBER,
                PASSWORD_HASH,
                Set.of(Role.STUDENT),
                AccountStatus.DISABLED,
                now,
                now
        );
        when(accountRepository.findByStudentNumber(STUDENT_NUMBER))
                .thenReturn(Optional.of(account));
        when(
                passwordEncoder.matches(
                        RAW_PASSWORD,
                        PASSWORD_HASH.value()
                )
        ).thenReturn(true);

        assertThatThrownBy(
                () -> service.authenticate(
                        new LoginCommand(
                                "S4789503",
                                RAW_PASSWORD
                        )
                )
        ).isInstanceOf(AccountDisabledException.class);

        verifyNoInteractions(accessTokenIssuer);
    }

    private Account activeAccount() {
        return Account.register(
                UserId.of(1000001L),
                STUDENT_NUMBER,
                PASSWORD_HASH,
                Instant.parse("2026-08-02T05:00:00Z")
        );
    }

    private AccessToken accessToken() {
        Instant issuedAt = Instant.parse("2026-08-02T05:00:00Z");
        return new AccessToken(
                "header.payload.signature",
                AccessToken.BEARER_TYPE,
                issuedAt,
                issuedAt.plusSeconds(900)
        );
    }
}

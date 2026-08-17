package com.schoolbus.iamservice.application.authentication;

import com.schoolbus.iamservice.domain.account.Account;
import com.schoolbus.iamservice.domain.account.AccountRepository;
import com.schoolbus.iamservice.domain.account.AccountStatus;
import com.schoolbus.iamservice.domain.account.PasswordHash;
import com.schoolbus.iamservice.domain.account.Role;
import com.schoolbus.iamservice.domain.account.StudentNumber;
import com.schoolbus.iamservice.domain.identity.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationApplicationServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-08-02T05:00:00Z");

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

    @Mock
    private RefreshTokenGenerator refreshTokenGenerator;

    @Mock
    private RefreshTokenHasher refreshTokenHasher;

    @Mock
    private LoginSessionRepository loginSessionRepository;

    private AuthenticationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AuthenticationApplicationService(
                accountRepository,
                passwordEncoder,
                accessTokenIssuer,
                refreshTokenGenerator,
                refreshTokenHasher,
                loginSessionRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
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
        RefreshToken refreshToken = refreshToken();
        when(refreshTokenGenerator.generate())
                .thenReturn(refreshToken);
        when(refreshTokenHasher.hash(refreshToken.value()))
                .thenReturn("refresh-token-hash");
        when(accessTokenIssuer.issue(eq(account), anyString()))
                .thenReturn(accessToken);

        AuthenticationResult result = service.authenticate(
                new LoginCommand("s4789503", RAW_PASSWORD)
        );

        assertThat(result.account()).isSameAs(account);
        assertThat(result.accessToken()).isSameAs(accessToken);
        assertThat(result.refreshToken()).isSameAs(refreshToken);

        ArgumentCaptor<String> sessionIdCaptor =
                ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LoginSession> sessionCaptor =
                ArgumentCaptor.forClass(LoginSession.class);
        verify(accessTokenIssuer).issue(
                eq(account),
                sessionIdCaptor.capture()
        );
        verify(loginSessionRepository).save(
                sessionCaptor.capture()
        );

        String sessionId = sessionIdCaptor.getValue();
        LoginSession session = sessionCaptor.getValue();
        assertThat(UUID.fromString(sessionId)).isNotNull();
        assertThat(session.sessionId()).isEqualTo(sessionId);
        assertThat(session.userId()).isEqualTo(account.userId());
        assertThat(session.refreshTokenHash())
                .isEqualTo("refresh-token-hash");
        assertThat(session.createdAt())
                .isEqualTo(refreshToken.issuedAt());
        assertThat(session.expiresAt())
                .isEqualTo(refreshToken.expiresAt());
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
        verifyNoTokenOrSessionInteractions();
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

        verifyNoTokenOrSessionInteractions();
    }

    @Test
    void shouldRejectDisabledAccountAfterPasswordMatches() {
        Account account = Account.restore(
                UserId.of(1000001L),
                STUDENT_NUMBER,
                PASSWORD_HASH,
                Set.of(Role.STUDENT),
                AccountStatus.DISABLED,
                NOW,
                NOW
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

        verifyNoTokenOrSessionInteractions();
    }

    @Test
    void shouldRotateRefreshTokenAndIssueNewAccessToken() {
        Account account = activeAccount();
        LoginSession currentSession = loginSession(
                "old-refresh-hash",
                NOW.plusSeconds(300)
        );
        RefreshToken replacementRefreshToken = refreshToken();
        AccessToken replacementAccessToken = accessToken();
        when(refreshTokenHasher.hash("old-raw-refresh-token"))
                .thenReturn("old-refresh-hash");
        when(
                loginSessionRepository.findByRefreshTokenHash(
                        "old-refresh-hash"
                )
        ).thenReturn(Optional.of(currentSession));
        when(accountRepository.findByUserId(account.userId()))
                .thenReturn(Optional.of(account));
        when(refreshTokenGenerator.generate())
                .thenReturn(replacementRefreshToken);
        when(
                refreshTokenHasher.hash(
                        replacementRefreshToken.value()
                )
        ).thenReturn("new-refresh-hash");
        when(
                accessTokenIssuer.issue(
                        account,
                        currentSession.sessionId()
                )
        ).thenReturn(replacementAccessToken);
        when(
                loginSessionRepository.replaceRefreshToken(
                        any(LoginSession.class),
                        eq("old-refresh-hash")
                )
        ).thenReturn(true);

        AuthenticationResult result = service.refresh(
                new RefreshAuthenticationCommand(
                        "old-raw-refresh-token"
                )
        );

        assertThat(result.account()).isSameAs(account);
        assertThat(result.accessToken())
                .isSameAs(replacementAccessToken);
        assertThat(result.refreshToken())
                .isSameAs(replacementRefreshToken);

        ArgumentCaptor<LoginSession> replacementCaptor =
                ArgumentCaptor.forClass(LoginSession.class);
        verify(loginSessionRepository).replaceRefreshToken(
                replacementCaptor.capture(),
                eq("old-refresh-hash")
        );
        LoginSession replacement = replacementCaptor.getValue();
        assertThat(replacement.sessionId())
                .isEqualTo(currentSession.sessionId());
        assertThat(replacement.userId())
                .isEqualTo(currentSession.userId());
        assertThat(replacement.refreshTokenHash())
                .isEqualTo("new-refresh-hash");
        assertThat(replacement.createdAt())
                .isEqualTo(currentSession.createdAt());
        assertThat(replacement.expiresAt())
                .isEqualTo(replacementRefreshToken.expiresAt());
    }

    @Test
    void shouldRejectUnknownRefreshToken() {
        when(refreshTokenHasher.hash("unknown-refresh-token"))
                .thenReturn("unknown-refresh-hash");
        when(
                loginSessionRepository.findByRefreshTokenHash(
                        "unknown-refresh-hash"
                )
        ).thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> service.refresh(
                        new RefreshAuthenticationCommand(
                                "unknown-refresh-token"
                        )
                )
        ).isInstanceOf(InvalidRefreshTokenException.class);

        verifyNoInteractions(
                accountRepository,
                refreshTokenGenerator,
                accessTokenIssuer
        );
    }

    @Test
    void shouldDeleteAndRejectExpiredRefreshSession() {
        LoginSession expiredSession = loginSession(
                "expired-refresh-hash",
                NOW
        );
        when(refreshTokenHasher.hash("expired-refresh-token"))
                .thenReturn("expired-refresh-hash");
        when(
                loginSessionRepository.findByRefreshTokenHash(
                        "expired-refresh-hash"
                )
        ).thenReturn(Optional.of(expiredSession));

        assertThatThrownBy(
                () -> service.refresh(
                        new RefreshAuthenticationCommand(
                                "expired-refresh-token"
                        )
                )
        ).isInstanceOf(InvalidRefreshTokenException.class);

        verify(loginSessionRepository).deleteBySessionId(
                expiredSession.sessionId()
        );
        verifyNoInteractions(
                accountRepository,
                refreshTokenGenerator,
                accessTokenIssuer
        );
    }

    @Test
    void shouldRejectRefreshWhenAccountIsDisabled() {
        LoginSession currentSession = loginSession(
                "old-refresh-hash",
                NOW.plusSeconds(300)
        );
        Account disabledAccount = Account.restore(
                currentSession.userId(),
                STUDENT_NUMBER,
                PASSWORD_HASH,
                Set.of(Role.STUDENT),
                AccountStatus.DISABLED,
                NOW,
                NOW
        );
        when(refreshTokenHasher.hash("old-raw-refresh-token"))
                .thenReturn("old-refresh-hash");
        when(
                loginSessionRepository.findByRefreshTokenHash(
                        "old-refresh-hash"
                )
        ).thenReturn(Optional.of(currentSession));
        when(
                accountRepository.findByUserId(
                        currentSession.userId()
                )
        ).thenReturn(Optional.of(disabledAccount));

        assertThatThrownBy(
                () -> service.refresh(
                        new RefreshAuthenticationCommand(
                                "old-raw-refresh-token"
                        )
                )
        ).isInstanceOf(AccountDisabledException.class);

        verifyNoInteractions(
                refreshTokenGenerator,
                accessTokenIssuer
        );
    }

    @Test
    void shouldRejectRefreshTokenAlreadyRotatedByAnotherRequest() {
        Account account = activeAccount();
        LoginSession currentSession = loginSession(
                "old-refresh-hash",
                NOW.plusSeconds(300)
        );
        RefreshToken replacementRefreshToken = refreshToken();
        when(refreshTokenHasher.hash("old-raw-refresh-token"))
                .thenReturn("old-refresh-hash");
        when(
                loginSessionRepository.findByRefreshTokenHash(
                        "old-refresh-hash"
                )
        ).thenReturn(Optional.of(currentSession));
        when(accountRepository.findByUserId(account.userId()))
                .thenReturn(Optional.of(account));
        when(refreshTokenGenerator.generate())
                .thenReturn(replacementRefreshToken);
        when(
                refreshTokenHasher.hash(
                        replacementRefreshToken.value()
                )
        ).thenReturn("new-refresh-hash");
        when(
                accessTokenIssuer.issue(
                        account,
                        currentSession.sessionId()
                )
        ).thenReturn(accessToken());
        when(
                loginSessionRepository.replaceRefreshToken(
                        any(LoginSession.class),
                        eq("old-refresh-hash")
                )
        ).thenReturn(false);

        assertThatThrownBy(
                () -> service.refresh(
                        new RefreshAuthenticationCommand(
                                "old-raw-refresh-token"
                        )
                )
        ).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void shouldDeleteLoginSessionWhenLoggingOut() {
        LogoutCommand command = new LogoutCommand("session-001");

        service.logout(command);

        verify(loginSessionRepository).deleteBySessionId(
                "session-001"
        );
        verifyNoInteractions(
                accountRepository,
                passwordEncoder,
                accessTokenIssuer,
                refreshTokenGenerator,
                refreshTokenHasher
        );
    }

    @Test
    void shouldTreatRepeatedLogoutAsIdempotent() {
        LogoutCommand command = new LogoutCommand("session-001");

        service.logout(command);
        service.logout(command);

        verify(loginSessionRepository, times(2))
                .deleteBySessionId("session-001");
    }

    @Test
    void shouldRejectLogoutWithoutSessionId() {
        assertThatThrownBy(
                () -> new LogoutCommand(" ")
        ).isInstanceOf(InvalidLoginSessionException.class);

        verifyNoInteractions(loginSessionRepository);
    }

    private Account activeAccount() {
        return Account.register(
                UserId.of(1000001L),
                STUDENT_NUMBER,
                PASSWORD_HASH,
                NOW
        );
    }

    private AccessToken accessToken() {
        Instant issuedAt = NOW;
        return new AccessToken(
                "header.payload.signature",
                AccessToken.BEARER_TYPE,
                issuedAt,
                issuedAt.plusSeconds(900)
        );
    }

    private RefreshToken refreshToken() {
        Instant issuedAt = NOW;
        return new RefreshToken(
                "raw-refresh-token",
                issuedAt,
                issuedAt.plusSeconds(604800)
        );
    }

    private LoginSession loginSession(
            String refreshTokenHash,
            Instant expiresAt
    ) {
        return new LoginSession(
                "session-001",
                UserId.of(1000001L),
                refreshTokenHash,
                NOW.minusSeconds(60),
                expiresAt
        );
    }

    private void verifyNoTokenOrSessionInteractions() {
        verifyNoInteractions(
                accessTokenIssuer,
                refreshTokenGenerator,
                refreshTokenHasher,
                loginSessionRepository
        );
    }
}

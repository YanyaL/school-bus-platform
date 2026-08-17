package com.schoolbus.iam.application.authentication;

import com.schoolbus.iam.config.ConditionalOnEmbeddedIam;
import com.schoolbus.iam.domain.account.Account;
import com.schoolbus.iam.domain.account.AccountRepository;
import com.schoolbus.iam.domain.account.AccountStatus;
import com.schoolbus.iam.domain.account.StudentNumber;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

@Service
@Profile("!test")
@ConditionalOnEmbeddedIam
public class AuthenticationApplicationService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenHasher refreshTokenHasher;
    private final LoginSessionRepository loginSessionRepository;
    private final Clock clock;

    public AuthenticationApplicationService(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            AccessTokenIssuer accessTokenIssuer,
            RefreshTokenGenerator refreshTokenGenerator,
            RefreshTokenHasher refreshTokenHasher,
            LoginSessionRepository loginSessionRepository,
            Clock clock
    ) {
        this.accountRepository = Objects.requireNonNull(
                accountRepository,
                "accountRepository must not be null"
        );
        this.passwordEncoder = Objects.requireNonNull(
                passwordEncoder,
                "passwordEncoder must not be null"
        );
        this.accessTokenIssuer = Objects.requireNonNull(
                accessTokenIssuer,
                "accessTokenIssuer must not be null"
        );
        this.refreshTokenGenerator = Objects.requireNonNull(
                refreshTokenGenerator,
                "refreshTokenGenerator must not be null"
        );
        this.refreshTokenHasher = Objects.requireNonNull(
                refreshTokenHasher,
                "refreshTokenHasher must not be null"
        );
        this.loginSessionRepository = Objects.requireNonNull(
                loginSessionRepository,
                "loginSessionRepository must not be null"
        );
        this.clock = Objects.requireNonNull(
                clock,
                "clock must not be null"
        );
    }

    @Transactional(readOnly = true)
    public AuthenticationResult authenticate(LoginCommand command) {
        LoginCommand validatedCommand = Objects.requireNonNull(
                command,
                "command must not be null"
        );

        StudentNumber studentNumber = StudentNumber.of(
                validatedCommand.studentNumber()
        );

        Account account = accountRepository
                .findByStudentNumber(studentNumber)
                .orElseThrow(InvalidCredentialsException::new);

        boolean passwordMatches = passwordEncoder.matches(
                validatedCommand.rawPassword(),
                account.passwordHash().value()
        );

        if (!passwordMatches) {
            throw new InvalidCredentialsException();
        }

        if (account.status() != AccountStatus.ACTIVE) {
            throw new AccountDisabledException();
        }

        String sessionId = UUID.randomUUID().toString();
        RefreshToken refreshToken = refreshTokenGenerator.generate();
        String refreshTokenHash = refreshTokenHasher.hash(
                refreshToken.value()
        );
        LoginSession loginSession = new LoginSession(
                sessionId,
                account.userId(),
                refreshTokenHash,
                refreshToken.issuedAt(),
                refreshToken.expiresAt()
        );
        AccessToken accessToken = accessTokenIssuer.issue(
                account,
                sessionId
        );

        loginSessionRepository.save(loginSession);

        return new AuthenticationResult(
                account,
                accessToken,
                refreshToken
        );
    }

    @Transactional(readOnly = true)
    public AuthenticationResult refresh(
            RefreshAuthenticationCommand command
    ) {
        RefreshAuthenticationCommand validatedCommand =
                Objects.requireNonNull(
                        command,
                        "command must not be null"
                );
        String currentRefreshTokenHash = refreshTokenHasher.hash(
                validatedCommand.rawRefreshToken()
        );
        LoginSession currentSession = loginSessionRepository
                .findByRefreshTokenHash(currentRefreshTokenHash)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (currentSession.isExpiredAt(clock.instant())) {
            loginSessionRepository.deleteBySessionId(
                    currentSession.sessionId()
            );
            throw new InvalidRefreshTokenException();
        }

        Account account = accountRepository
                .findByUserId(currentSession.userId())
                .orElseThrow(InvalidRefreshTokenException::new);
        if (account.status() != AccountStatus.ACTIVE) {
            throw new AccountDisabledException();
        }

        RefreshToken replacementRefreshToken =
                refreshTokenGenerator.generate();
        String replacementRefreshTokenHash =
                refreshTokenHasher.hash(
                        replacementRefreshToken.value()
                );
        LoginSession replacementSession = new LoginSession(
                currentSession.sessionId(),
                currentSession.userId(),
                replacementRefreshTokenHash,
                currentSession.createdAt(),
                replacementRefreshToken.expiresAt()
        );
        AccessToken replacementAccessToken =
                accessTokenIssuer.issue(
                        account,
                        currentSession.sessionId()
                );

        boolean replaced = loginSessionRepository
                .replaceRefreshToken(
                        replacementSession,
                        currentRefreshTokenHash
                );
        if (!replaced) {
            throw new InvalidRefreshTokenException();
        }

        return new AuthenticationResult(
                account,
                replacementAccessToken,
                replacementRefreshToken
        );
    }

    public void logout(LogoutCommand command) {
        LogoutCommand validatedCommand = Objects.requireNonNull(
                command,
                "command must not be null"
        );
        loginSessionRepository.deleteBySessionId(
                validatedCommand.sessionId()
        );
    }
}

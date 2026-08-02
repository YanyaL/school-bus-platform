package com.schoolbus.iam.application.authentication;

import com.schoolbus.iam.domain.account.Account;
import com.schoolbus.iam.domain.account.AccountRepository;
import com.schoolbus.iam.domain.account.AccountStatus;
import com.schoolbus.iam.domain.account.StudentNumber;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@Profile("!test")
public class AuthenticationApplicationService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthenticationApplicationService(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.accountRepository = Objects.requireNonNull(
                accountRepository,
                "accountRepository must not be null"
        );
        this.passwordEncoder = Objects.requireNonNull(
                passwordEncoder,
                "passwordEncoder must not be null"
        );
    }

    @Transactional(readOnly = true)
    public Account authenticate(LoginCommand command) {
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

        return account;
    }
}

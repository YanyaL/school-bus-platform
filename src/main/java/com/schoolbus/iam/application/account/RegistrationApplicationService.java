package com.schoolbus.iam.application.account;

import com.schoolbus.iam.domain.account.Account;
import com.schoolbus.iam.domain.account.AccountRepository;
import com.schoolbus.iam.domain.account.PasswordHash;
import com.schoolbus.iam.domain.account.StudentNumber;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.util.Objects;

public final class RegistrationApplicationService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public RegistrationApplicationService(
        AccountRepository accountRepository,
        PasswordEncoder passwordEncoder,
        Clock clock
    ) {
        this.accountRepository =
            Objects.requireNonNull(
                accountRepository,
                "accountRepository must not be null"
            );

        this.passwordEncoder =
            Objects.requireNonNull(
                passwordEncoder,
                "passwordEncoder must not be null"
            );

        this.clock = Objects.requireNonNull(
            clock,
            "clock must not be null"
        );
    }

    public Account register(
        RegisterAccountCommand command
    ) {
        Objects.requireNonNull(
            command,
            "command must not be null"
        );

        StudentNumber studentNumber =
            StudentNumber.of(
                command.studentNumber()
            );

        if (accountRepository
            .existsByStudentNumber(studentNumber)) {
            throw new DuplicateStudentNumberException(
                studentNumber
            );
        }

        String encodedPassword =
            passwordEncoder.encode(
                command.rawPassword()
            );

        PasswordHash passwordHash =
            PasswordHash.of(encodedPassword);

        Account account = Account.register(
            studentNumber,
            passwordHash,
            clock.instant()
        );

        return accountRepository.save(account);
    }
}

package com.schoolbus.iamservice.application.account;

import com.schoolbus.iamservice.domain.account.Account;
import com.schoolbus.iamservice.domain.account.AccountRepository;
import com.schoolbus.iamservice.domain.account.PasswordHash;
import com.schoolbus.iamservice.domain.account.StudentNumber;
import com.schoolbus.iamservice.domain.identity.UserId;
import com.schoolbus.iamservice.domain.identity.UserIdGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;

@Service
public class RegistrationApplicationService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserIdGenerator userIdGenerator;
    private final Clock clock;

    public RegistrationApplicationService(
        AccountRepository accountRepository,
        PasswordEncoder passwordEncoder,
        UserIdGenerator userIdGenerator,
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

        this.userIdGenerator =
            Objects.requireNonNull(
                userIdGenerator,
                "userIdGenerator must not be null"
            );

        this.clock = Objects.requireNonNull(
            clock,
            "clock must not be null"
        );
    }

    @Transactional
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

        UserId userId =
            userIdGenerator.nextId();

        Account account = Account.register(
            userId,
            studentNumber,
            passwordHash,
            clock.instant()
        );

        return accountRepository.save(account);
    }
}

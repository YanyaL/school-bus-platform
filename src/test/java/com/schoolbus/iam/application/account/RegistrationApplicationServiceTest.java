package com.schoolbus.iam.application.account;

import com.schoolbus.iam.domain.account.Account;
import com.schoolbus.iam.domain.account.AccountRepository;
import com.schoolbus.iam.domain.account.AccountStatus;
import com.schoolbus.iam.domain.account.Role;
import com.schoolbus.iam.domain.account.StudentNumber;
import com.schoolbus.shared.domain.identity.UserId;
import com.schoolbus.shared.domain.identity.UserIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationApplicationServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-08-01T10:00:00Z");

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserIdGenerator userIdGenerator;

    private RegistrationApplicationService service;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(
                NOW,
                ZoneOffset.UTC
        );

        service = new RegistrationApplicationService(
                accountRepository,
                passwordEncoder,
                userIdGenerator,
                fixedClock
        );
    }

    @Test
    void shouldRegisterStudentAccount() {
        RegisterAccountCommand command =
                new RegisterAccountCommand(
                        "s4789503",
                        "StrongPass!2026"
                );

        StudentNumber studentNumber =
                StudentNumber.of("S4789503");

        UserId userId =
                UserId.of(1000001L);

        String encodedPassword =
                "{bcrypt}$2a$10$encodedPassword";

        when(
                accountRepository
                        .existsByStudentNumber(studentNumber)
        ).thenReturn(false);

        when(
                passwordEncoder.encode(
                        command.rawPassword()
                )
        ).thenReturn(encodedPassword);

        when(userIdGenerator.nextId())
                .thenReturn(userId);

        when(
                accountRepository.save(any(Account.class))
        ).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        Account account = service.register(command);

        assertThat(account.userId())
                .isEqualTo(userId);

        assertThat(account.studentNumber())
                .isEqualTo(studentNumber);

        assertThat(account.passwordHash().value())
                .isEqualTo(encodedPassword);

        assertThat(account.status())
                .isEqualTo(AccountStatus.ACTIVE);

        assertThat(account.roles())
                .containsExactly(Role.STUDENT);

        assertThat(account.createdAt())
                .isEqualTo(NOW);

        verify(accountRepository)
                .existsByStudentNumber(studentNumber);

        verify(passwordEncoder)
                .encode("StrongPass!2026");

        verify(userIdGenerator).nextId();

        verify(accountRepository)
                .save(account);
    }

    @Test
    void shouldRejectDuplicateStudentNumber() {
        RegisterAccountCommand command =
                new RegisterAccountCommand(
                        "S4789503",
                        "StrongPass!2026"
                );

        StudentNumber studentNumber =
                StudentNumber.of("S4789503");

        when(
                accountRepository
                        .existsByStudentNumber(studentNumber)
        ).thenReturn(true);

        assertThatThrownBy(
                () -> service.register(command)
        )
                .isInstanceOf(
                        DuplicateStudentNumberException.class
                )
                .hasMessage(
                        "student number already exists: S4789503"
                );

        verifyNoInteractions(
                passwordEncoder,
                userIdGenerator
        );

        verify(accountRepository, never())
                .save(any(Account.class));
    }
}

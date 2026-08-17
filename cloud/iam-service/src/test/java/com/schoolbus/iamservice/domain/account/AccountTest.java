package com.schoolbus.iamservice.domain.account;

import com.schoolbus.iamservice.domain.identity.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private static final Instant REGISTERED_AT =
            Instant.parse("2026-07-31T10:00:00Z");

    private final UserId userId =
            UserId.of(1000001L);

    private final StudentNumber studentNumber =
            StudentNumber.of("S4789503");

    private final PasswordHash passwordHash =
            PasswordHash.of("{bcrypt}$2a$10$oldPasswordHash");

    @Test
    void shouldRegisterActiveStudentAccount() {
        Account account = Account.register(
                userId,
                studentNumber,
                passwordHash,
                REGISTERED_AT
        );

        assertThat(account.userId())
                .isEqualTo(userId);

        assertThat(account.studentNumber())
                .isEqualTo(studentNumber);

        assertThat(account.passwordHash())
                .isEqualTo(passwordHash);

        assertThat(account.status())
                .isEqualTo(AccountStatus.ACTIVE);

        assertThat(account.roles())
                .containsExactly(Role.STUDENT);

        assertThat(account.createdAt())
                .isEqualTo(REGISTERED_AT);

        assertThat(account.updatedAt())
                .isEqualTo(REGISTERED_AT);
    }

    @Test
    void shouldDisableAccount() {
        Account account = Account.register(
                userId,
                studentNumber,
                passwordHash,
                REGISTERED_AT
        );

        Instant disabledAt =
                REGISTERED_AT.plusSeconds(60);

        account.disable(disabledAt);

        assertThat(account.status())
                .isEqualTo(AccountStatus.DISABLED);

        assertThat(account.updatedAt())
                .isEqualTo(disabledAt);
    }

    @Test
    void shouldEnableAccount() {
        Account account = Account.register(
                userId,
                studentNumber,
                passwordHash,
                REGISTERED_AT
        );

        account.disable(
                REGISTERED_AT.plusSeconds(60)
        );

        Instant enabledAt =
                REGISTERED_AT.plusSeconds(120);

        account.enable(enabledAt);

        assertThat(account.status())
                .isEqualTo(AccountStatus.ACTIVE);

        assertThat(account.updatedAt())
                .isEqualTo(enabledAt);
    }

    @Test
    void shouldChangePassword() {
        Account account = Account.register(
                userId,
                studentNumber,
                passwordHash,
                REGISTERED_AT
        );

        PasswordHash newPasswordHash =
                PasswordHash.of(
                        "{bcrypt}$2a$10$newPasswordHash"
                );

        Instant changedAt =
                REGISTERED_AT.plusSeconds(180);

        account.changePassword(
                newPasswordHash,
                changedAt
        );

        assertThat(account.passwordHash())
                .isEqualTo(newPasswordHash);

        assertThat(account.updatedAt())
                .isEqualTo(changedAt);
    }

    @Test
    void shouldCheckAccountRoles() {
        Account account = Account.register(
                userId,
                studentNumber,
                passwordHash,
                REGISTERED_AT
        );

        assertThat(account.hasRole(Role.STUDENT))
                .isTrue();

        assertThat(account.hasRole(Role.ADMIN))
                .isFalse();
    }

    @Test
    void shouldRestoreExistingAccount() {
        Instant createdAt =
                Instant.parse("2026-01-01T10:00:00Z");

        Instant updatedAt =
                Instant.parse("2026-07-01T10:00:00Z");

        Account account = Account.restore(
                userId,
                studentNumber,
                passwordHash,
                Set.of(Role.ADMIN),
                AccountStatus.DISABLED,
                createdAt,
                updatedAt
        );

        assertThat(account.userId())
                .isEqualTo(userId);

        assertThat(account.studentNumber())
                .isEqualTo(studentNumber);

        assertThat(account.passwordHash())
                .isEqualTo(passwordHash);

        assertThat(account.roles())
                .containsExactly(Role.ADMIN);

        assertThat(account.status())
                .isEqualTo(AccountStatus.DISABLED);

        assertThat(account.createdAt())
                .isEqualTo(createdAt);

        assertThat(account.updatedAt())
                .isEqualTo(updatedAt);
    }

    @Test
    void shouldRejectAccountWithoutRoles() {
        assertThatThrownBy(
                () -> Account.restore(
                        userId,
                        studentNumber,
                        passwordHash,
                        Set.of(),
                        AccountStatus.ACTIVE,
                        REGISTERED_AT,
                        REGISTERED_AT
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage("roles must not be empty");
    }
}

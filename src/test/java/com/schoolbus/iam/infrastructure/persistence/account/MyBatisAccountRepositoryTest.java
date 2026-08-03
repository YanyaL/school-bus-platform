package com.schoolbus.iam.infrastructure.persistence.account;

import com.schoolbus.iam.domain.account.Account;
import com.schoolbus.iam.domain.account.AccountStatus;
import com.schoolbus.iam.domain.account.PasswordHash;
import com.schoolbus.iam.domain.account.Role;
import com.schoolbus.iam.domain.account.StudentNumber;
import com.schoolbus.shared.domain.identity.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyBatisAccountRepositoryTest {

    private static final Instant REGISTERED_AT =
            Instant.parse("2026-08-02T05:00:00Z");

    private static final UserId USER_ID =
            UserId.of(1000001L);

    private static final StudentNumber STUDENT_NUMBER =
            StudentNumber.of("S4789503");

    private static final PasswordHash PASSWORD_HASH =
            PasswordHash.of("{bcrypt}$2a$10$encodedPassword");

    @Mock
    private AccountMapper accountMapper;

    private MyBatisAccountRepository repository;

    @BeforeEach
    void setUp() {
        repository = new MyBatisAccountRepository(
                accountMapper
        );
    }

    @Test
    void shouldReturnTrueWhenStudentNumberExists() {
        when(
                accountMapper.countByStudentNumber(
                        STUDENT_NUMBER.value()
                )
        ).thenReturn(1);

        boolean exists = repository
                .existsByStudentNumber(STUDENT_NUMBER);

        assertThat(exists).isTrue();

        verify(accountMapper)
                .countByStudentNumber(
                        STUDENT_NUMBER.value()
                );
    }

    @Test
    void shouldReturnFalseWhenStudentNumberDoesNotExist() {
        when(
                accountMapper.countByStudentNumber(
                        STUDENT_NUMBER.value()
                )
        ).thenReturn(0);

        boolean exists = repository
                .existsByStudentNumber(STUDENT_NUMBER);

        assertThat(exists).isFalse();
    }

    @Test
    void shouldInsertAccountBeforeItsRoles() {
        Account account = Account.register(
                USER_ID,
                STUDENT_NUMBER,
                PASSWORD_HASH,
                REGISTERED_AT
        );

        LocalDateTime databaseTime =
                LocalDateTime.ofInstant(
                        REGISTERED_AT,
                        ZoneOffset.UTC
                );

        when(
                accountMapper.insertAccount(
                        any(AccountDataObject.class)
                )
        ).thenAnswer(invocation -> {
            AccountDataObject dataObject =
                    invocation.getArgument(0);
            dataObject.setId(42L);
            return 1;
        });

        when(
                accountMapper.insertRole(
                        42L,
                        Role.STUDENT.name(),
                        databaseTime
                )
        ).thenReturn(1);

        Account savedAccount = repository.save(account);

        assertThat(savedAccount).isSameAs(account);

        ArgumentCaptor<AccountDataObject> captor =
                ArgumentCaptor.forClass(
                        AccountDataObject.class
                );

        InOrder inOrder = inOrder(accountMapper);
        inOrder.verify(accountMapper)
                .insertAccount(captor.capture());
        inOrder.verify(accountMapper)
                .insertRole(
                        42L,
                        Role.STUDENT.name(),
                        databaseTime
                );

        AccountDataObject inserted = captor.getValue();

        assertThat(inserted.getId()).isEqualTo(42L);
        assertThat(inserted.getUserId())
                .isEqualTo(USER_ID.value());
        assertThat(inserted.getStudentNumber())
                .isEqualTo(STUDENT_NUMBER.value());
        assertThat(inserted.getPasswordHash())
                .isEqualTo(PASSWORD_HASH.value());
        assertThat(inserted.getStatus())
                .isEqualTo(AccountStatus.ACTIVE.name());
        assertThat(inserted.getVersion()).isZero();
        assertThat(inserted.getCreatedAt())
                .isEqualTo(databaseTime);
        assertThat(inserted.getUpdatedAt())
                .isEqualTo(databaseTime);
    }

    @Test
    void shouldReturnEmptyWhenAccountDoesNotExist() {
        when(
                accountMapper.selectByStudentNumber(
                        STUDENT_NUMBER.value()
                )
        ).thenReturn(null);

        Optional<Account> result = repository
                .findByStudentNumber(STUDENT_NUMBER);

        assertThat(result).isEmpty();

        verify(accountMapper, never())
                .selectRoleCodesByAccountId(any());
    }

    @Test
    void shouldRestoreAccountAndRolesFromDatabase() {
        Instant createdAt =
                Instant.parse("2026-01-01T10:00:00Z");
        Instant updatedAt =
                Instant.parse("2026-07-01T10:00:00Z");

        AccountDataObject dataObject = new AccountDataObject();
        dataObject.setId(42L);
        dataObject.setUserId(USER_ID.value());
        dataObject.setStudentNumber(STUDENT_NUMBER.value());
        dataObject.setPasswordHash(PASSWORD_HASH.value());
        dataObject.setStatus(AccountStatus.DISABLED.name());
        dataObject.setVersion(3L);
        dataObject.setCreatedAt(
                LocalDateTime.ofInstant(
                        createdAt,
                        ZoneOffset.UTC
                )
        );
        dataObject.setUpdatedAt(
                LocalDateTime.ofInstant(
                        updatedAt,
                        ZoneOffset.UTC
                )
        );

        when(
                accountMapper.selectByStudentNumber(
                        STUDENT_NUMBER.value()
                )
        ).thenReturn(dataObject);

        when(
                accountMapper.selectRoleCodesByAccountId(42L)
        ).thenReturn(List.of(Role.ADMIN.name()));

        Account restored = repository
                .findByStudentNumber(STUDENT_NUMBER)
                .orElseThrow();

        assertThat(restored.userId()).isEqualTo(USER_ID);
        assertThat(restored.studentNumber())
                .isEqualTo(STUDENT_NUMBER);
        assertThat(restored.passwordHash())
                .isEqualTo(PASSWORD_HASH);
        assertThat(restored.status())
                .isEqualTo(AccountStatus.DISABLED);
        assertThat(restored.roles())
                .containsExactly(Role.ADMIN);
        assertThat(restored.createdAt())
                .isEqualTo(createdAt);
        assertThat(restored.updatedAt())
                .isEqualTo(updatedAt);
    }

    @Test
    void shouldFindAccountByUserId() {
        AccountDataObject dataObject = new AccountDataObject();
        dataObject.setId(42L);
        dataObject.setUserId(USER_ID.value());
        dataObject.setStudentNumber(STUDENT_NUMBER.value());
        dataObject.setPasswordHash(PASSWORD_HASH.value());
        dataObject.setStatus(AccountStatus.ACTIVE.name());
        dataObject.setVersion(0L);
        dataObject.setCreatedAt(
                LocalDateTime.ofInstant(
                        REGISTERED_AT,
                        ZoneOffset.UTC
                )
        );
        dataObject.setUpdatedAt(
                LocalDateTime.ofInstant(
                        REGISTERED_AT,
                        ZoneOffset.UTC
                )
        );
        when(accountMapper.selectByUserId(USER_ID.value()))
                .thenReturn(dataObject);
        when(accountMapper.selectRoleCodesByAccountId(42L))
                .thenReturn(List.of(Role.STUDENT.name()));

        Optional<Account> result = repository.findByUserId(USER_ID);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().userId())
                .isEqualTo(USER_ID);
        assertThat(result.orElseThrow().roles())
                .containsExactly(Role.STUDENT);
    }
}

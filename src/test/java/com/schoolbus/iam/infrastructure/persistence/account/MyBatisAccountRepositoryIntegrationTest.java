package com.schoolbus.iam.infrastructure.persistence.account;

import com.schoolbus.iam.domain.account.Account;
import com.schoolbus.iam.domain.account.AccountStatus;
import com.schoolbus.iam.domain.account.PasswordHash;
import com.schoolbus.iam.domain.account.Role;
import com.schoolbus.iam.domain.account.StudentNumber;
import com.schoolbus.shared.domain.identity.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("integration-test")
@Transactional
class MyBatisAccountRepositoryIntegrationTest {

    private static final Instant REGISTERED_AT =
            Instant.parse("2026-08-02T05:00:00.123Z");

    private static final PasswordHash PASSWORD_HASH =
            PasswordHash.of(
                    "{bcrypt}$2a$10$integrationTestHash"
            );

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(
                    DockerImageName.parse("mysql:8.4.0")
            )
                    .withDatabaseName("school_bus_test")
                    .withUsername("school_bus")
                    .withPassword("school_bus");

    @Autowired
    private AccountMapper accountMapper;

    private MyBatisAccountRepository repository;

    @BeforeEach
    void setUp() {
        repository = new MyBatisAccountRepository(
                accountMapper
        );
    }

    @Test
    void shouldSaveAndRestoreAccountUsingRealMySql() {
        UserId userId = UserId.of(1000001L);
        StudentNumber studentNumber =
                StudentNumber.of("S4789503");

        Account account = Account.register(
                userId,
                studentNumber,
                PASSWORD_HASH,
                REGISTERED_AT
        );

        repository.save(account);

        assertThat(
                repository.existsByStudentNumber(studentNumber)
        ).isTrue();

        AccountDataObject stored = accountMapper
                .selectByStudentNumber(studentNumber.value());

        assertThat(stored).isNotNull();
        assertThat(stored.getId()).isPositive();
        assertThat(stored.getUserId())
                .isEqualTo(userId.value());
        assertThat(stored.getVersion()).isZero();
        assertThat(
                accountMapper.selectRoleCodesByAccountId(
                        stored.getId()
                )
        ).containsExactly(Role.STUDENT.name());

        Optional<Account> result = repository
                .findByStudentNumber(studentNumber);

        assertThat(result).isPresent();
        Account restored = result.orElseThrow();
        assertThat(restored.userId()).isEqualTo(userId);
        assertThat(restored.studentNumber())
                .isEqualTo(studentNumber);
        assertThat(restored.passwordHash())
                .isEqualTo(PASSWORD_HASH);
        assertThat(restored.roles())
                .containsExactly(Role.STUDENT);
        assertThat(restored.status())
                .isEqualTo(AccountStatus.ACTIVE);
        assertThat(restored.createdAt())
                .isEqualTo(REGISTERED_AT);
        assertThat(restored.updatedAt())
                .isEqualTo(REGISTERED_AT);
    }

    @Test
    void shouldRejectDuplicateStudentNumberUsingDatabaseConstraint() {
        StudentNumber studentNumber =
                StudentNumber.of("S4789504");

        Account firstAccount = Account.register(
                UserId.of(1000002L),
                studentNumber,
                PASSWORD_HASH,
                REGISTERED_AT
        );
        Account duplicateAccount = Account.register(
                UserId.of(1000003L),
                studentNumber,
                PASSWORD_HASH,
                REGISTERED_AT
        );

        repository.save(firstAccount);

        assertThatThrownBy(
                () -> repository.save(duplicateAccount)
        ).isInstanceOf(
                DataIntegrityViolationException.class
        );
    }
}

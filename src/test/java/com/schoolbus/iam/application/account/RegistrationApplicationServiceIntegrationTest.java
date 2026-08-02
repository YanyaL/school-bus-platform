package com.schoolbus.iam.application.account;

import com.schoolbus.iam.domain.account.Account;
import com.schoolbus.iam.domain.account.AccountStatus;
import com.schoolbus.iam.domain.account.Role;
import com.schoolbus.iam.domain.account.StudentNumber;
import com.schoolbus.iam.infrastructure.persistence.account.AccountDataObject;
import com.schoolbus.iam.infrastructure.persistence.account.AccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("integration-test")
class RegistrationApplicationServiceIntegrationTest {

    private static final String RAW_PASSWORD =
            "StrongPass!2026";

    private static final String FAILING_TRIGGER =
            "fail_iam_account_role_insert";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(
                    DockerImageName.parse("mysql:8.4.0")
            )
                    .withDatabaseName("school_bus_test")
                    .withUsername("school_bus")
                    .withPassword("school_bus")
                    .withCommand(
                            "--log-bin-trust-function-creators=1"
                    );

    @Autowired
    private RegistrationApplicationService service;

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        dropFailingTrigger();
        jdbcTemplate.update("DELETE FROM iam_account_role");
        jdbcTemplate.update("DELETE FROM iam_account");
    }

    @Test
    void shouldRegisterStudentUsingCompleteApplicationFlow() {
        RegisterAccountCommand command =
                new RegisterAccountCommand(
                        "s4789503",
                        RAW_PASSWORD
                );

        Account account = service.register(command);

        assertThat(account.userId().value()).isPositive();
        assertThat(account.studentNumber())
                .isEqualTo(StudentNumber.of("S4789503"));
        assertThat(account.status())
                .isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.roles())
                .containsExactly(Role.STUDENT);
        assertThat(account.passwordHash().value())
                .isNotEqualTo(RAW_PASSWORD)
                .startsWith("{bcrypt}");
        assertThat(
                passwordEncoder.matches(
                        RAW_PASSWORD,
                        account.passwordHash().value()
                )
        ).isTrue();

        AccountDataObject stored = accountMapper
                .selectByStudentNumber("S4789503");

        assertThat(stored).isNotNull();
        assertThat(stored.getUserId())
                .isEqualTo(account.userId().value());
        assertThat(stored.getPasswordHash())
                .isEqualTo(account.passwordHash().value());
        assertThat(
                accountMapper.selectRoleCodesByAccountId(
                        stored.getId()
                )
        ).containsExactly(Role.STUDENT.name());
    }

    @Test
    void shouldRejectDuplicateStudentNumber() {
        RegisterAccountCommand command =
                new RegisterAccountCommand(
                        "S4789504",
                        RAW_PASSWORD
                );

        service.register(command);

        assertThatThrownBy(
                () -> service.register(command)
        )
                .isInstanceOf(
                        DuplicateStudentNumberException.class
                )
                .hasMessage(
                        "student number already exists: S4789504"
                );

        assertThat(
                accountMapper.countByStudentNumber("S4789504")
        ).isEqualTo(1);
    }

    @Test
    void shouldRollbackAccountWhenRoleInsertFails() {
        jdbcTemplate.execute("""
                CREATE TRIGGER fail_iam_account_role_insert
                BEFORE INSERT ON iam_account_role
                FOR EACH ROW
                SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'forced role insert failure'
                """);

        try {
            RegisterAccountCommand command =
                    new RegisterAccountCommand(
                            "S4789505",
                            RAW_PASSWORD
                    );

            assertThatThrownBy(
                    () -> service.register(command)
            ).isInstanceOf(DataAccessException.class);
        } finally {
            dropFailingTrigger();
        }

        assertThat(
                accountMapper.countByStudentNumber("S4789505")
        ).isZero();
    }

    private void dropFailingTrigger() {
        jdbcTemplate.execute(
                "DROP TRIGGER IF EXISTS " + FAILING_TRIGGER
        );
    }
}

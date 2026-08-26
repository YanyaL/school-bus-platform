package com.schoolbus.iamservice.infrastructure.security.bootstrap;

import com.schoolbus.iamservice.domain.account.AccountStatus;
import com.schoolbus.iamservice.domain.account.Role;
import com.schoolbus.iamservice.domain.account.StudentNumber;
import com.schoolbus.iamservice.infrastructure.persistence.AccountDataObject;
import com.schoolbus.iamservice.infrastructure.persistence.AccountMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Service
@ConditionalOnProperty(
        prefix = "school-bus.security.admin-bootstrap",
        name = "enabled",
        havingValue = "true"
)
public class AdminRoleProvisioningService {

    private final AccountMapper accountMapper;
    private final Clock clock;

    public AdminRoleProvisioningService(
            AccountMapper accountMapper,
            Clock clock
    ) {
        this.accountMapper = Objects.requireNonNull(accountMapper);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public boolean provision(String rawStudentNumber) {
        String studentNumber = StudentNumber.of(rawStudentNumber).value();
        AccountDataObject account = accountMapper.selectByStudentNumber(
                studentNumber
        );
        if (account == null) {
            throw new IllegalStateException(
                    "configured admin bootstrap account does not exist"
            );
        }
        if (!AccountStatus.ACTIVE.name().equals(account.getStatus())) {
            throw new IllegalStateException(
                    "configured admin bootstrap account is not active"
            );
        }

        int affectedRows = accountMapper.insertRoleIfAbsent(
                account.getId(),
                Role.ADMIN.name(),
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
        );
        if (affectedRows < 0 || affectedRows > 1) {
            throw new IllegalStateException(
                    "unexpected admin bootstrap row count: " + affectedRows
            );
        }
        return affectedRows == 1;
    }
}

package com.schoolbus.iam.infrastructure.persistence.account;

import com.schoolbus.iam.domain.account.Account;
import com.schoolbus.iam.domain.account.AccountRepository;
import com.schoolbus.iam.domain.account.AccountStatus;
import com.schoolbus.iam.domain.account.PasswordHash;
import com.schoolbus.iam.domain.account.Role;
import com.schoolbus.iam.domain.account.StudentNumber;
import com.schoolbus.shared.domain.identity.UserId;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class MyBatisAccountRepository
        implements AccountRepository {

    private static final ZoneOffset DATABASE_ZONE =
            ZoneOffset.UTC;

    private final AccountMapper accountMapper;

    public MyBatisAccountRepository(
            AccountMapper accountMapper
    ) {
        this.accountMapper = Objects.requireNonNull(
                accountMapper,
                "accountMapper must not be null"
        );
    }

    @Override
    public boolean existsByStudentNumber(
            StudentNumber studentNumber
    ) {
        StudentNumber validatedStudentNumber =
                Objects.requireNonNull(
                        studentNumber,
                        "studentNumber must not be null"
                );

        return accountMapper.countByStudentNumber(
                validatedStudentNumber.value()
        ) > 0;
    }

    @Override
    public Account save(Account account) {
        Account validatedAccount = Objects.requireNonNull(
                account,
                "account must not be null"
        );

        AccountDataObject dataObject =
                toDataObject(validatedAccount);

        int accountRows =
                accountMapper.insertAccount(dataObject);

        if (accountRows != 1) {
            throw new IllegalStateException(
                    "failed to insert account"
            );
        }

        if (dataObject.getId() == null) {
            throw new IllegalStateException(
                    "generated account id is missing"
            );
        }

        for (Role role : validatedAccount.roles()) {
            int roleRows = accountMapper.insertRole(
                    dataObject.getId(),
                    role.name(),
                    dataObject.getCreatedAt()
            );

            if (roleRows != 1) {
                throw new IllegalStateException(
                        "failed to insert account role"
                );
            }
        }

        return validatedAccount;
    }

    @Override
    public Optional<Account> findByStudentNumber(
            StudentNumber studentNumber
    ) {
        StudentNumber validatedStudentNumber =
                Objects.requireNonNull(
                        studentNumber,
                        "studentNumber must not be null"
                );

        AccountDataObject dataObject =
                accountMapper.selectByStudentNumber(
                        validatedStudentNumber.value()
                );

        if (dataObject == null) {
            return Optional.empty();
        }

        Set<Role> roles = accountMapper
                .selectRoleCodesByAccountId(
                        dataObject.getId()
                )
                .stream()
                .map(Role::valueOf)
                .collect(Collectors.toUnmodifiableSet());

        return Optional.of(
                toDomain(dataObject, roles)
        );
    }

    private AccountDataObject toDataObject(
            Account account
    ) {
        AccountDataObject dataObject =
                new AccountDataObject();

        dataObject.setUserId(
                account.userId().value()
        );
        dataObject.setStudentNumber(
                account.studentNumber().value()
        );
        dataObject.setPasswordHash(
                account.passwordHash().value()
        );
        dataObject.setStatus(
                account.status().name()
        );
        dataObject.setVersion(0L);
        dataObject.setCreatedAt(
                LocalDateTime.ofInstant(
                        account.createdAt(),
                        DATABASE_ZONE
                )
        );
        dataObject.setUpdatedAt(
                LocalDateTime.ofInstant(
                        account.updatedAt(),
                        DATABASE_ZONE
                )
        );

        return dataObject;
    }

    private Account toDomain(
            AccountDataObject dataObject,
            Set<Role> roles
    ) {
        return Account.restore(
                UserId.of(dataObject.getUserId()),
                StudentNumber.of(
                        dataObject.getStudentNumber()
                ),
                PasswordHash.of(
                        dataObject.getPasswordHash()
                ),
                roles,
                AccountStatus.valueOf(
                        dataObject.getStatus()
                ),
                dataObject.getCreatedAt()
                        .toInstant(DATABASE_ZONE),
                dataObject.getUpdatedAt()
                        .toInstant(DATABASE_ZONE)
        );
    }
}

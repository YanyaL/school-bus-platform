package com.schoolbus.iam.domain.account;

import com.schoolbus.shared.domain.identity.UserId;

import java.util.Optional;

public interface AccountRepository {

    boolean existsByStudentNumber(
        StudentNumber studentNumber
    );

    Account save(Account account);

    Optional<Account> findByStudentNumber(
        StudentNumber studentNumber
    );

    Optional<Account> findByUserId(UserId userId);
}

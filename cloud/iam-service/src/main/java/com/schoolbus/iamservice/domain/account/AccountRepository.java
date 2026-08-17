package com.schoolbus.iamservice.domain.account;

import com.schoolbus.iamservice.domain.identity.UserId;

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

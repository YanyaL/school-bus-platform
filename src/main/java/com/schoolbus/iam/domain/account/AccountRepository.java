package com.schoolbus.iam.domain.account;

import java.util.Optional;

public interface AccountRepository {

    boolean existsByStudentNumber(
        StudentNumber studentNumber
    );

    Account save(Account account);

    Optional<Account> findByStudentNumber(
        StudentNumber studentNumber
    );
}

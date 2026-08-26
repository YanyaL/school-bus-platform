package com.schoolbus.iamservice.infrastructure.security.sso;

import com.schoolbus.iamservice.domain.account.Account;
import com.schoolbus.iamservice.domain.account.AccountRepository;
import com.schoolbus.iamservice.domain.account.AccountStatus;
import com.schoolbus.iamservice.domain.account.StudentNumber;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;

    public AccountUserDetailsService(AccountRepository accountRepository) {
        this.accountRepository = Objects.requireNonNull(
                accountRepository,
                "accountRepository must not be null"
        );
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        StudentNumber studentNumber;
        try {
            studentNumber = StudentNumber.of(username);
        } catch (IllegalArgumentException exception) {
            throw notFound(username);
        }

        Account account = accountRepository
                .findByStudentNumber(studentNumber)
                .orElseThrow(() -> notFound(username));

        return new SchoolBusUserPrincipal(
                account.userId().value(),
                account.studentNumber().value(),
                account.passwordHash().value(),
                account.roles().stream().map(Enum::name).collect(
                        java.util.stream.Collectors.toUnmodifiableSet()
                ),
                account.status() == AccountStatus.ACTIVE
        );
    }

    private static UsernameNotFoundException notFound(String username) {
        return new UsernameNotFoundException(
                "Account not found for student number: " + username
        );
    }
}

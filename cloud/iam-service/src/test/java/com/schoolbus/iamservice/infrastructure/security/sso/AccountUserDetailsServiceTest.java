package com.schoolbus.iamservice.infrastructure.security.sso;

import com.schoolbus.iamservice.domain.account.Account;
import com.schoolbus.iamservice.domain.account.AccountRepository;
import com.schoolbus.iamservice.domain.account.AccountStatus;
import com.schoolbus.iamservice.domain.account.PasswordHash;
import com.schoolbus.iamservice.domain.account.Role;
import com.schoolbus.iamservice.domain.account.StudentNumber;
import com.schoolbus.iamservice.domain.identity.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountUserDetailsServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-08-24T00:00:00Z");

    private final AccountRepository repository =
            mock(AccountRepository.class);
    private final AccountUserDetailsService service =
            new AccountUserDetailsService(repository);

    @Test
    void shouldMapActiveAccountToStableSsoPrincipal() {
        StudentNumber studentNumber = StudentNumber.of("s4789503");
        Account account = Account.restore(
                UserId.of(1000001L),
                studentNumber,
                PasswordHash.of("{bcrypt}$2a$10$encodedPassword"),
                Set.of(Role.STUDENT, Role.ADMIN),
                AccountStatus.ACTIVE,
                NOW,
                NOW
        );
        when(repository.findByStudentNumber(studentNumber))
                .thenReturn(Optional.of(account));

        SchoolBusUserPrincipal principal =
                (SchoolBusUserPrincipal) service
                        .loadUserByUsername(" s4789503 ");

        assertThat(principal.userId()).isEqualTo(1000001L);
        assertThat(principal.getUsername()).isEqualTo("S4789503");
        assertThat(principal.getPassword())
                .isEqualTo("{bcrypt}$2a$10$encodedPassword");
        assertThat(principal.roles()).containsExactly("ADMIN", "STUDENT");
        assertThat(principal.isEnabled()).isTrue();
        assertThat(principal.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN", "ROLE_STUDENT");
    }

    @Test
    void shouldDisableAuthenticationForDisabledAccount() {
        StudentNumber studentNumber = StudentNumber.of("S4789503");
        Account account = Account.restore(
                UserId.of(1000001L),
                studentNumber,
                PasswordHash.of("{bcrypt}$2a$10$encodedPassword"),
                Set.of(Role.STUDENT),
                AccountStatus.DISABLED,
                NOW,
                NOW
        );
        when(repository.findByStudentNumber(studentNumber))
                .thenReturn(Optional.of(account));

        SchoolBusUserPrincipal principal =
                (SchoolBusUserPrincipal) service
                        .loadUserByUsername("S4789503");

        assertThat(principal.isEnabled()).isFalse();
    }

    @Test
    void shouldHideInvalidAndMissingStudentNumbersBehindSameException() {
        when(repository.findByStudentNumber(StudentNumber.of("UNKNOWN")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("invalid-学号"))
                .isInstanceOf(UsernameNotFoundException.class);
        assertThatThrownBy(() -> service.loadUserByUsername("UNKNOWN"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}

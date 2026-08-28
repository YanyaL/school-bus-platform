package com.schoolbus.iamservice.infrastructure.security.bootstrap;

import com.schoolbus.iamservice.infrastructure.persistence.AccountDataObject;
import com.schoolbus.iamservice.infrastructure.persistence.AccountMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminRoleProvisioningServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-26T00:00:00Z"),
            ZoneOffset.UTC
    );

    private final AccountMapper mapper = mock(AccountMapper.class);
    private final AdminRoleProvisioningService service =
            new AdminRoleProvisioningService(mapper, CLOCK);

    @Test
    void shouldGrantAdminRoleToExistingActiveAccount() {
        when(mapper.selectByStudentNumber("S4789503"))
                .thenReturn(account("ACTIVE"));
        when(mapper.insertRoleIfAbsent(eq(7L), eq("ADMIN"), any()))
                .thenReturn(1);

        assertThat(service.provision(" s4789503 ")).isTrue();

        verify(mapper).insertRoleIfAbsent(
                7L,
                "ADMIN",
                java.time.LocalDateTime.parse("2026-08-26T00:00:00")
        );
    }

    @Test
    void shouldBeIdempotentWhenAdminRoleAlreadyExists() {
        when(mapper.selectByStudentNumber("S4789503"))
                .thenReturn(account("ACTIVE"));
        when(mapper.insertRoleIfAbsent(eq(7L), eq("ADMIN"), any()))
                .thenReturn(0);

        assertThat(service.provision("S4789503")).isFalse();
    }

    @Test
    void shouldFailFastWhenAccountDoesNotExist() {
        assertThatThrownBy(() -> service.provision("S4789503"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void shouldRejectDisabledAccount() {
        when(mapper.selectByStudentNumber("S4789503"))
                .thenReturn(account("DISABLED"));

        assertThatThrownBy(() -> service.provision("S4789503"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not active");
    }

    private static AccountDataObject account(String status) {
        AccountDataObject account = new AccountDataObject();
        account.setId(7L);
        account.setStudentNumber("S4789503");
        account.setStatus(status);
        return account;
    }
}

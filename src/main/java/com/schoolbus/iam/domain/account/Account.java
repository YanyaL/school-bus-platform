package com.schoolbus.iam.domain.account;

import com.schoolbus.shared.domain.identity.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public final class Account {

    private final UserId userId;
    private final StudentNumber studentNumber;
    private PasswordHash passwordHash;
    private final Set<Role> roles;
    private AccountStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private Account(
            UserId userId,
            StudentNumber studentNumber,
            PasswordHash passwordHash,
            Set<Role> roles,
            AccountStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.userId = Objects.requireNonNull(
                userId,
                "userId must not be null"
        );
        this.studentNumber = Objects.requireNonNull(
                studentNumber,
                "studentNumber must not be null"
        );
        this.passwordHash = Objects.requireNonNull(
                passwordHash,
                "passwordHash must not be null"
        );
        Set<Role> validatedRoles = Set.copyOf(
                Objects.requireNonNull(
                        roles,
                        "roles must not be null"
                )
        );
        if (validatedRoles.isEmpty()) {
            throw new IllegalArgumentException(
                    "roles must not be empty"
            );
        }
        this.roles = validatedRoles;
        this.status = Objects.requireNonNull(
                status,
                "status must not be null"
        );
        this.createdAt = Objects.requireNonNull(
                createdAt,
                "createdAt must not be null"
        );
        this.updatedAt = Objects.requireNonNull(
                updatedAt,
                "updatedAt must not be null"
        );
    }

    public static Account register(
            UserId userId,
            StudentNumber studentNumber,
            PasswordHash passwordHash,
            Instant registeredAt
    ) {
        return new Account(
                userId,
                studentNumber,
                passwordHash,
                Set.of(Role.STUDENT),
                AccountStatus.ACTIVE,
                registeredAt,
                registeredAt
        );
    }

    public static Account restore(
            UserId userId,
            StudentNumber studentNumber,
            PasswordHash passwordHash,
            Set<Role> roles,
            AccountStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new Account(
                userId,
                studentNumber,
                passwordHash,
                roles,
                status,
                createdAt,
                updatedAt
        );
    }

    public void disable(Instant disabledAt) {
        Instant operationTime = Objects.requireNonNull(
                disabledAt,
                "disabledAt must not be null"
        );
        status = AccountStatus.DISABLED;
        updatedAt = operationTime;
    }

    public void enable(Instant enabledAt) {
        Instant operationTime = Objects.requireNonNull(
                enabledAt,
                "enabledAt must not be null"
        );
        status = AccountStatus.ACTIVE;
        updatedAt = operationTime;
    }

    public void changePassword(
            PasswordHash newPasswordHash,
            Instant changedAt
    ) {
        PasswordHash validatedPasswordHash = Objects.requireNonNull(
                newPasswordHash,
                "newPasswordHash must not be null"
        );
        Instant operationTime = Objects.requireNonNull(
                changedAt,
                "changedAt must not be null"
        );
        passwordHash = validatedPasswordHash;
        updatedAt = operationTime;
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public UserId userId() {
        return userId;
    }

    public StudentNumber studentNumber() {
        return studentNumber;
    }

    public PasswordHash passwordHash() {
        return passwordHash;
    }

    public Set<Role> roles() {
        return roles;
    }

    public AccountStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}

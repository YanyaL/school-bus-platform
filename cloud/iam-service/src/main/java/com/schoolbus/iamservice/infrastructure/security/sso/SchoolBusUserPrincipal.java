package com.schoolbus.iamservice.infrastructure.security.sso;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class SchoolBusUserPrincipal implements UserDetails {

    private final long userId;
    private final String studentNumber;
    private final String passwordHash;
    private final Set<String> roles;
    private final boolean enabled;

    public SchoolBusUserPrincipal(
            long userId,
            String studentNumber,
            String passwordHash,
            Set<String> roles,
            boolean enabled
    ) {
        this.userId = userId;
        this.studentNumber = studentNumber;
        this.passwordHash = passwordHash;
        this.roles = Set.copyOf(roles);
        this.enabled = enabled;
    }

    public long userId() {
        return userId;
    }

    public String studentNumber() {
        return studentNumber;
    }

    public List<String> roles() {
        return roles.stream().sorted().toList();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .sorted()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return studentNumber;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}

package com.schoolbus.iamservice.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PasswordConfiguration.class)
class PasswordConfigurationTest {

    private final PasswordEncoder passwordEncoder;

    @Autowired
    PasswordConfigurationTest(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Test
    void shouldEncodeAndVerifyPassword() {
        String rawPassword = "StrongPass!2026";

        String encodedPassword = passwordEncoder.encode(rawPassword);

        assertThat(encodedPassword).startsWith("{bcrypt}");
        assertThat(encodedPassword).isNotEqualTo(rawPassword);
        assertThat(passwordEncoder.matches(rawPassword, encodedPassword)).isTrue();
    }

    @Test
    void shouldGenerateDifferentHashesForSamePassword() {
        String rawPassword = "StrongPass!2026";

        String firstHash = passwordEncoder.encode(rawPassword);
        String secondHash = passwordEncoder.encode(rawPassword);

        assertThat(firstHash).isNotEqualTo(secondHash);
    }
}

package com.schoolbus.iam.domain.account;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordHashTest {

    @Test
    void shouldCreatePasswordHash() {
        String encodedPassword = "{bcrypt}$2a$10$abcdef";

        PasswordHash passwordHash = PasswordHash.of(encodedPassword);

        assertThat(passwordHash.value())
                .isEqualTo(encodedPassword);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void shouldRejectBlankPasswordHash(String value) {
        assertThatThrownBy(() -> PasswordHash.of(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("passwordHash must not be blank");
    }

    @Test
    void shouldRejectPasswordHashWithoutAlgorithmPrefix() {
        assertThatThrownBy(() -> PasswordHash.of("abcdef"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "passwordHash must contain an algorithm prefix"
                );
    }

    @Test
    void shouldRejectPasswordHashLongerThan255Characters() {
        String value = "{bcrypt}" + "a".repeat(248);

        assertThat(value).hasSize(256);

        assertThatThrownBy(() -> PasswordHash.of(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("passwordHash is too long");
    }
}

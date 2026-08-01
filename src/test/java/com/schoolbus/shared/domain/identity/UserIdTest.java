package com.schoolbus.shared.domain.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserIdTest {

    @Test
    void shouldCreatePositiveUserId() {
        UserId userId = UserId.of(1000001L);

        assertThat(userId.value())
                .isEqualTo(1000001L);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -100})
    void shouldRejectNonPositiveUserId(long value) {
        assertThatThrownBy(() -> UserId.of(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId must be positive");
    }
}

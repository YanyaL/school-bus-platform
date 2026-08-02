package com.schoolbus.shared.infrastructure.identity;

import com.schoolbus.shared.domain.identity.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnowflakeUserIdGeneratorTest {

    @Test
    void shouldGeneratePositiveAndUniqueIds() {
        SnowflakeUserIdGenerator generator =
                new SnowflakeUserIdGenerator(1L);

        Set<Long> generatedIds = new HashSet<>();

        for (int i = 0; i < 5000; i++) {
            UserId userId = generator.nextId();
            generatedIds.add(userId.value());
        }

        assertThat(generatedIds)
                .hasSize(5000)
                .allMatch(id -> id > 0);
    }

    @Test
    void shouldGenerateIncreasingIds() {
        SnowflakeUserIdGenerator generator =
                new SnowflakeUserIdGenerator(1L);

        UserId first = generator.nextId();
        UserId second = generator.nextId();

        assertThat(second.value())
                .isGreaterThan(first.value());
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 1024})
    void shouldRejectInvalidWorkerId(long workerId) {
        assertThatThrownBy(
                () -> new SnowflakeUserIdGenerator(workerId)
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage(
                        "workerId must be between 0 and 1023"
                );
    }
}

package com.schoolbus.iam.domain.account;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentNumberTest {

    @Test
    void shouldNormalizeStudentNumber() {
        StudentNumber studentNumber =
            StudentNumber.of(" s4789503 ");

        assertThat(studentNumber.value())
            .isEqualTo("S4789503");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        " ",
        "S-4789503",
        "学生001",
        "123456789012345678901234567890123"
    })
    void shouldRejectInvalidStudentNumber(String value) {
        assertThatThrownBy(() -> StudentNumber.of(value))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

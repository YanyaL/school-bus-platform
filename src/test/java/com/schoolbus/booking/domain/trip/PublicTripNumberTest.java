package com.schoolbus.booking.domain.trip;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicTripNumberTest {

    @Test
    void shouldNormalizeUuidString() {
        PublicTripNumber number = PublicTripNumber.of(
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
        );
        assertThat(number.toString())
                .isEqualTo("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    }

    @Test
    void shouldRejectBlank() {
        assertThatThrownBy(() -> PublicTripNumber.of("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectInvalidUuid() {
        assertThatThrownBy(() -> PublicTripNumber.of("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

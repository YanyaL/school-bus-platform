package com.schoolbus.transport.domain.trip;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TripIdentityTest {

    @Test
    void shouldCreatePositiveIdentifiers() {
        assertThat(TripId.of(1001L).value()).isEqualTo(1001L);
        assertThat(RouteId.of(2001L).value()).isEqualTo(2001L);
    }

    @Test
    void shouldRejectNonPositiveIdentifiers() {
        assertThatThrownBy(() -> TripId.of(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tripId must be positive");
        assertThatThrownBy(() -> RouteId.of(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("routeId must be positive");
    }
}

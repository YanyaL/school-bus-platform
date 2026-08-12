package com.schoolbus.transport.domain.trip;

import com.schoolbus.transport.domain.route.RouteId;
import com.schoolbus.transport.domain.vehicle.VehicleId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TripIdentityTest {

    @Test
    void shouldCreatePositiveIdentifiers() {
        assertThat(TripId.of(1001L).value()).isEqualTo(1001L);
        assertThat(RouteId.of(2001L).value()).isEqualTo(2001L);
        assertThat(VehicleId.of(3001L).value()).isEqualTo(3001L);
    }

    @Test
    void shouldRejectNonPositiveIdentifiers() {
        assertThatThrownBy(() -> TripId.of(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tripId must be positive");
        assertThatThrownBy(() -> RouteId.of(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("routeId must be positive");
        assertThatThrownBy(() -> VehicleId.of(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("vehicleId must be positive");
    }

    @Test
    void shouldValidateTripNumberAndMoney() {
        TripNumber number = TripNumber.of(
                "11111111-1111-1111-1111-111111111111"
        );

        assertThat(number.toString())
                .isEqualTo(
                        "11111111-1111-1111-1111-111111111111"
                );
        assertThat(Money.of("5").amount().toPlainString())
                .isEqualTo("5.00");
        assertThatThrownBy(() -> Money.of("-0.01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount must not be negative");
    }
}

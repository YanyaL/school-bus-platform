package com.schoolbus.transport.domain.route;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-08-12T00:00:00Z");

    @Test
    void shouldCreateEnabledRoute() {
        Route route = Route.create(
                RouteNumber.of(
                        "11111111-1111-1111-1111-111111111111"
                ),
                RouteCode.of("MAIN-EAST-01"),
                Campus.MAIN,
                Campus.EAST,
                40,
                CREATED_AT
        );

        assertThat(route.isNew()).isTrue();
        assertThat(route.status()).isEqualTo(RouteStatus.ENABLED);
        assertThat(route.departureCampus()).isEqualTo(Campus.MAIN);
        assertThat(route.arrivalCampus()).isEqualTo(Campus.EAST);
        assertThat(route.estimatedDurationMinutes()).isEqualTo(40);
    }

    @Test
    void shouldRejectSameDepartureAndArrivalCampus() {
        assertThatThrownBy(() -> Route.create(
                RouteNumber.generate(),
                RouteCode.of("MAIN-MAIN-01"),
                Campus.MAIN,
                Campus.MAIN,
                40,
                CREATED_AT
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("departureCampus and arrivalCampus must differ");
    }

    @Test
    void shouldRejectZeroEstimatedDuration() {
        assertThatThrownBy(() -> Route.create(
                RouteNumber.generate(),
                RouteCode.of("MAIN-EAST-02"),
                Campus.MAIN,
                Campus.EAST,
                0,
                CREATED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBlankRouteCode() {
        assertThatThrownBy(() -> RouteCode.of(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("routeCode must not be blank");
    }

    @Test
    void shouldEnableAndDisableRoute() {
        Route route = restoredRoute(RouteStatus.DISABLED);

        route.enable(CREATED_AT.plusSeconds(10));

        assertThat(route.status()).isEqualTo(RouteStatus.ENABLED);
        assertThat(route.version()).isEqualTo(1L);

        route.disable(CREATED_AT.plusSeconds(20));

        assertThat(route.status()).isEqualTo(RouteStatus.DISABLED);
        assertThat(route.version()).isEqualTo(2L);
    }

    @Test
    void shouldRejectEnablingAlreadyEnabledRoute() {
        Route route = restoredRoute(RouteStatus.ENABLED);

        assertThatThrownBy(() -> route.enable(CREATED_AT.plusSeconds(10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("route is already enabled");
    }

    @Test
    void shouldRejectDisablingAlreadyDisabledRoute() {
        Route route = restoredRoute(RouteStatus.DISABLED);

        assertThatThrownBy(() -> route.disable(CREATED_AT.plusSeconds(10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("route is already disabled");
    }

    @Test
    void shouldRejectDurationAboveMaximum() {
        assertThatThrownBy(() -> Route.create(
                RouteNumber.generate(),
                RouteCode.of("MAIN-EAST-03"),
                Campus.MAIN,
                Campus.EAST,
                Route.MAX_DURATION_MINUTES + 1,
                CREATED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private Route restoredRoute(RouteStatus status) {
        return Route.restore(
                RouteId.of(2001L),
                RouteNumber.of(
                        "22222222-2222-2222-2222-222222222222"
                ),
                RouteCode.of("MAIN-EAST-01"),
                Campus.MAIN,
                Campus.EAST,
                40,
                status,
                0L,
                CREATED_AT,
                CREATED_AT
        );
    }
}

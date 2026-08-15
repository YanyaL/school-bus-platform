package com.schoolbus.gateway.routing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CoreServiceRoutesTest {

    @Autowired
    private RouteLocator routeLocator;

    @ParameterizedTest
    @CsvSource({
            "GET, /api/v1/trips, school-bus-transport-query-trips, lb://school-bus-transport-query",
            "GET, /api/v1/trips/11111111-1111-1111-1111-111111111111/seats, school-bus-transport-query-seats, lb://school-bus-transport-query",
            "GET, /api/v1/admin/trips, school-bus-core-api, lb://school-bus-core",
            "POST, /api/v1/admin/trips, school-bus-core-api, lb://school-bus-core",
            "GET, /api/v1/bookings, school-bus-core-api, lb://school-bus-core",
            "POST, /api/v1/auth/login, school-bus-core-api, lb://school-bus-core",
            "POST, /api/v1/trips, school-bus-core-api, lb://school-bus-core"
    })
    void routesRequestsToExpectedService(
            String method,
            String path,
            String expectedRouteId,
            String expectedUri
    ) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.valueOf(method), path).build()
        );

        Route route = routeLocator.getRoutes()
                .filterWhen(candidate -> Mono.from(candidate.getPredicate().apply(exchange)))
                .blockFirst();

        assertThat(route).isNotNull();
        assertThat(route.getId()).isEqualTo(expectedRouteId);
        assertThat(route.getUri()).isEqualTo(URI.create(expectedUri));
    }

    @Test
    void removesUntrustedIdentityHeadersBeforeForwarding() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/bookings")
                        .header("Authorization", "Bearer access-token")
                        .header("X-User-Id", "spoofed-user")
                        .header("X-User-Roles", "ADMIN")
                        .header("X-Authenticated-User", "spoofed-user")
                        .build()
        );

        Route route = routeLocator.getRoutes()
                .filterWhen(candidate -> Mono.from(candidate.getPredicate().apply(exchange)))
                .blockFirst();

        assertThat(route).isNotNull();
        invokeFilters(route.getFilters(), 0, exchange, filteredExchange -> {
            assertThat(filteredExchange.getRequest().getHeaders().getFirst("Authorization"))
                    .isEqualTo("Bearer access-token");
            assertThat(filteredExchange.getRequest().getHeaders().containsKey("X-User-Id")).isFalse();
            assertThat(filteredExchange.getRequest().getHeaders().containsKey("X-User-Roles")).isFalse();
            assertThat(filteredExchange.getRequest().getHeaders().containsKey("X-Authenticated-User")).isFalse();
            return filteredExchange.getResponse().setComplete();
        }).block();
    }

    private Mono<Void> invokeFilters(
            List<GatewayFilter> filters,
            int index,
            ServerWebExchange exchange,
            GatewayFilterChain terminalChain
    ) {
        if (index == filters.size()) {
            return terminalChain.filter(exchange);
        }
        return filters.get(index).filter(
                exchange,
                nextExchange -> invokeFilters(filters, index + 1, nextExchange, terminalChain)
        );
    }

    @Test
    void transportQueryRoutesUseSharedServiceIdNotPerInstance() {
        MockServerWebExchange trips = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/trips").build()
        );
        MockServerWebExchange seats = MockServerWebExchange.from(
                MockServerHttpRequest.get(
                        "/api/v1/trips/11111111-1111-1111-1111-111111111111/seats"
                ).build()
        );

        Route tripsRoute = routeLocator.getRoutes()
                .filterWhen(candidate -> Mono.from(candidate.getPredicate().apply(trips)))
                .blockFirst();
        Route seatsRoute = routeLocator.getRoutes()
                .filterWhen(candidate -> Mono.from(candidate.getPredicate().apply(seats)))
                .blockFirst();

        assertThat(tripsRoute).isNotNull();
        assertThat(seatsRoute).isNotNull();
        assertThat(tripsRoute.getUri()).isEqualTo(URI.create("lb://school-bus-transport-query"));
        assertThat(seatsRoute.getUri()).isEqualTo(URI.create("lb://school-bus-transport-query"));
        assertThat(tripsRoute.getUri().getHost()).isEqualTo("school-bus-transport-query");
    }
}
